package com.mrabdul.tools.ssl;

import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public class CapturingTrustManager implements X509TrustManager {

    private final X509TrustManager delegate;
    private final KeyStore trustStore;

    private volatile X509Certificate[] lastServerChain;
    private volatile String likelyTrustAnchorAlias;

    public CapturingTrustManager(X509TrustManager delegate, KeyStore trustStore) {
        this.delegate = delegate;
        this.trustStore = trustStore;
    }

    public X509Certificate[] getLastServerChain() { return lastServerChain; }
    public String getLikelyTrustAnchorAlias() { return likelyTrustAnchorAlias; }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // capture chain no matter what
        lastServerChain = chain;

        delegate.checkServerTrusted(chain, authType);

        // best-effort: find a truststore alias that matches any cert in the chain
        likelyTrustAnchorAlias = guessAliasFromTrustStore(chain);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }

    private String guessAliasFromTrustStore(X509Certificate[] chain) {
        if (trustStore == null || chain == null) return null;
        try {
            Enumeration<String> aliases = trustStore.aliases();
            while (aliases.hasMoreElements()) {
                String a = aliases.nextElement();
                if (!trustStore.isCertificateEntry(a) && !trustStore.isKeyEntry(a)) continue;

                java.security.cert.Certificate c = trustStore.getCertificate(a);
                if (!(c instanceof X509Certificate)) continue;

                X509Certificate xc = (X509Certificate) c;

                // direct match against presented chain
                for (int i = 0; i < chain.length; i++) {
                    if (chain[i].equals(xc)) {
                        return a;
                    }
                }

                // heuristic: if this trust cert can verify the last cert in the chain, it's a good candidate
                try {
                    chain[chain.length - 1].verify(xc.getPublicKey());
                    return a;
                } catch (Exception ignore) {
                    // ignore
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
