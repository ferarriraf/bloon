#!/usr/bin/env bash
set -euo pipefail

SDK="$HOME/android-sdk"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
APKSIG_JAR="$SDK/libs/apksig.jar"

PKG_DIR="$(pwd)"
OUT="$PKG_DIR/build"
APP="$PKG_DIR/app"
TOOLS="$PKG_DIR/tools"
KEYSTORE="$PKG_DIR/build/debug.keystore"
KS_PASS="android"
KS_ALIAS="androiddebugkey"

rm -rf "$OUT"
mkdir -p "$OUT"/{compiled-res,classes,dex,gen,tools}

echo "==> Compiling resources"
RES_FLAT="$OUT/compiled-res"
find "$APP/src/main/res" -type f | while read -r f; do
  "$BT/aapt2" compile -o "$RES_FLAT" "$f"
done

echo "==> Linking resources (gen R.java, base APK without dex)"
COMPILED_FILES=()
for f in "$RES_FLAT"/*.flat; do COMPILED_FILES+=("$f"); done

"$BT/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$APP/src/main/AndroidManifest.xml" \
  --java "$OUT/gen" \
  -o "$OUT/base.apk" \
  --min-sdk-version 21 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0 \
  --auto-add-overlay \
  "${COMPILED_FILES[@]}"

echo "==> Compiling Java sources"
SRC_FILES=$(find "$APP/src/main/java" "$OUT/gen" -name "*.java")
javac -source 1.8 -target 1.8 -nowarn -Xlint:none \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $SRC_FILES 2>&1 | grep -v "obsolete" || true

echo "==> Dexing with d8"
CLASS_FILES=$(find "$OUT/classes" -name "*.class")
java -cp "$BT/r8.jar" com.android.tools.r8.D8 \
  --release \
  --min-api 21 \
  --lib "$ANDROID_JAR" \
  --output "$OUT/dex" \
  $CLASS_FILES

echo "==> Building unaligned APK"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
( cd "$OUT/dex" && zip -q "$OUT/unaligned.apk" classes.dex )

echo "==> Zipalign"
"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "==> Generating debug keystore (if missing)"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$KS_PASS" \
    -keypass "$KS_PASS" \
    -alias "$KS_ALIAS" \
    -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 \
    -validity 10000 >/dev/null 2>&1
fi

echo "==> Building signer helper"
javac --release 8 -d "$OUT/tools" -cp "$APKSIG_JAR" "$TOOLS/Signer.java"

echo "==> Signing APK (v1 + v2)"
JDK11="/usr/lib/jvm/java-11-openjdk-amd64/bin/java"
if [ ! -x "$JDK11" ]; then JDK11="java"; fi
"$JDK11" -cp "$OUT/tools:$APKSIG_JAR" Signer \
  "$KEYSTORE" "$KS_PASS" "$KS_ALIAS" "$KS_PASS" \
  "$OUT/aligned.apk" "$OUT/bloon-battle.apk"

echo ""
echo "================================================"
echo "  APK BUILT: $OUT/bloon-battle.apk"
echo "  Size: $(du -h "$OUT/bloon-battle.apk" | cut -f1)"
echo "================================================"
