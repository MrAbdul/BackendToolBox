package com.mrabdul.tools.ssl;

public class SslCheckRequest {
    private final String jksPath;
    private final String jksPassword;
    private final String target;
    private final ProxyConfig proxy;
    private final boolean useSameStoreAsTrust;
    private final boolean hostnameVerification;

    public SslCheckRequest(String jksPath,
                           String jksPassword,
                           String target,
                           ProxyConfig proxy,
                           boolean useSameStoreAsTrust,
                           boolean hostnameVerification) {
        this.jksPath = jksPath;
        this.jksPassword = jksPassword;
        this.target = target;
        this.proxy = proxy;
        this.useSameStoreAsTrust = useSameStoreAsTrust;
        this.hostnameVerification = hostnameVerification;
    }

    public String getJksPath() { return jksPath; }
    public String getJksPassword() { return jksPassword; }
    public String getTarget() { return target; }
    public ProxyConfig getProxy() { return proxy; }
    public boolean isUseSameStoreAsTrust() { return useSameStoreAsTrust; }
    public boolean isHostnameVerification() { return hostnameVerification; }
}
