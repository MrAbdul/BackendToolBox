package com.mrabdul.tools.ssl;

import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class CapturingKeyManager extends X509ExtendedKeyManager {

    private final X509ExtendedKeyManager delegate;
    private volatile String lastChosenClientAlias;

    public CapturingKeyManager(X509ExtendedKeyManager delegate) {
        this.delegate = delegate;
    }

    public String getLastChosenClientAlias() {
        return lastChosenClientAlias;
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        String alias = delegate.chooseClientAlias(keyType, issuers, socket);
        lastChosenClientAlias = alias;
        return alias;
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, javax.net.ssl.SSLEngine engine) {
        String alias = delegate.chooseEngineClientAlias(keyType, issuers, engine);
        lastChosenClientAlias = alias;
        return alias;
    }

    // delegate everything else

    @Override public String[] getClientAliases(String keyType, Principal[] issuers) {
        return delegate.getClientAliases(keyType, issuers);
    }
    @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return delegate.chooseServerAlias(keyType, issuers, socket);
    }
    @Override public String chooseEngineServerAlias(String keyType, Principal[] issuers, javax.net.ssl.SSLEngine engine) {
        return delegate.chooseEngineServerAlias(keyType, issuers, engine);
    }
    @Override public X509Certificate[] getCertificateChain(String alias) {
        return delegate.getCertificateChain(alias);
    }
    @Override public PrivateKey getPrivateKey(String alias) {
        return delegate.getPrivateKey(alias);
    }
    @Override public String[] getServerAliases(String keyType, Principal[] issuers) {
        return delegate.getServerAliases(keyType, issuers);
    }
}
