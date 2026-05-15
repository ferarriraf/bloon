import com.android.apksig.ApkSigner;
import com.android.apksig.ApkSigner.SignerConfig;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class Signer {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: Signer <keystore> <storepass> <alias> <keypass> <input.apk> <output.apk>");
            System.exit(2);
        }
        File ks = new File(args[0]);
        char[] storePass = args[1].toCharArray();
        String alias = args[2];
        char[] keyPass = args[3].toCharArray();
        File input = new File(args[4]);
        File output = new File(args[5]);

        KeyStore keystore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ks)) {
            keystore.load(fis, storePass);
        }
        PrivateKey key = (PrivateKey) keystore.getKey(alias, keyPass);
        java.security.cert.Certificate[] chain = keystore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            // try aliasing
            Enumeration<String> as = keystore.aliases();
            while (as.hasMoreElements()) {
                String a = as.nextElement();
                java.security.cert.Certificate[] cc = keystore.getCertificateChain(a);
                if (cc != null && cc.length > 0) { chain = cc; alias = a; key = (PrivateKey) keystore.getKey(a, keyPass); break; }
            }
        }
        if (chain == null || chain.length == 0) throw new RuntimeException("No cert chain for alias " + alias);
        X509Certificate[] x509 = new X509Certificate[chain.length];
        for (int i = 0; i < chain.length; i++) x509[i] = (X509Certificate) chain[i];

        SignerConfig signer = new SignerConfig.Builder("CERT", key, java.util.Arrays.asList(x509)).build();
        ApkSigner.Builder b = new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true);
        b.build().sign();
        System.out.println("Signed " + output);
    }
}
