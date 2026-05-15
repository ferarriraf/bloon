# Bloon Battle

A simple tower-defense game for Android, inspired by Bloons TD Battles.
Built without the official Android SDK, using d8 (from r8), aapt2, zipalign,
and apksig — all assembled from public mirrors.

## Play

Install the APK on any Android 5.0+ device:

```
adb install release/bloon-battle.apk
```

Or copy `release/bloon-battle.apk` to your phone and open it.
(Enable "Install from unknown sources" if prompted.)

## Gameplay

- Tap a tower in the bottom bar, then tap a tile on the grass to place it.
- Tap **GO** (top-right) to start the next wave.
- Tap an existing tower to upgrade or sell it.
- The speed button (top-right) toggles 1x / 2x / 3x.
- Survive 20 waves, including a final MOAB, to win.

### Towers

| Name   | Cost | Strength                                   |
|--------|------|--------------------------------------------|
| Dart   | 200  | Fast single shot, cheap, reliable.         |
| Tack   | 360  | 8-way ring of darts at short range.        |
| Bomb   | 500  | AoE explosion; cannot pop black bloons.    |
| Sniper | 600  | Hits any bloon on the map instantly.       |

### Bloons

Red → Blue → Green → Yellow → Pink (faster on each pop), then Black (immune
to explosions), Lead (immune to sharp darts — bombs/sniper only), and the
boss MOAB on wave 20.

## Build from source

Requires JDK 21 + JDK 11 (for legacy apksig). Run:

```
./build.sh
```

Output: `build/bloon-battle.apk`.

The build pipeline:
1. `aapt2 compile` + `aapt2 link` — resources to APK
2. `javac` — Java sources to classes
3. `d8` (from r8.jar) — classes to `classes.dex`
4. `zipalign` — 4-byte alignment
5. Custom `Signer` using `apksig` library — v1 + v2 signature

Tools live in `~/android-sdk` and are fetched once from public mirrors
(no official Android SDK install required).
