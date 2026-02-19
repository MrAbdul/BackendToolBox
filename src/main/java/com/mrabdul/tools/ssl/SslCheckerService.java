package com.mrabdul.tools.ssl;

import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class SslCheckerService {

    public SslCheckResult check(SslCheckRequest req) {
        StringBuilder report = new StringBuilder();
        boolean ok = false;

        try {
            Target t = parseTarget(req.getTarget());
            report.append("Target Host: ").append(t.host).append("\n");
            report.append("Target Port: ").append(t.port).append("\n");
            report.append("Proxy      : ").append(req.getProxy() != null && req.getProxy().isEnabled()
                    ? (req.getProxy().getHost() + ":" + req.getProxy().getPort())
                    : "(none)").append("\n");
            report.append("JKS        : ").append(req.getJksPath()).append("\n");
            report.append("Hostname Verify: ").append(req.isHostnameVerification()).append("\n");
            report.append("Use as TrustStore: ").append(req.isUseSameStoreAsTrust()).append("\n\n");

            KeyStore ks = loadJks(req.getJksPath(), req.getJksPassword());

            // KeyManagers (client certs)
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, req.getJksPassword().toCharArray());

            X509ExtendedKeyManager baseKm = pickX509KeyManager(kmf.getKeyManagers());
            CapturingKeyManager capturingKm = baseKm != null ? new CapturingKeyManager(baseKm) : null;

            KeyManager[] kms;
            if (capturingKm != null) {
                kms = new KeyManager[] { capturingKm };
            } else {
                kms = new KeyManager[0];
                report.append("WARN: No X509 KeyManager found. Client cert selection cannot be captured.\n");
            }

            // TrustManagers (server validation)
            TrustManager[] tms = buildTrustManagers(req, ks);
            CapturingTrustManager capturingTm = findCapturingTrustManager(tms);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kms, tms, new SecureRandom());

            SSLSocketFactory sf = ctx.getSocketFactory();

            Socket raw;
            if (req.getProxy() != null && req.getProxy().isEnabled()) {
                raw = connectViaHttpProxy(req.getProxy(), t.host, t.port, report);
            } else {
                raw = new Socket(t.host, t.port);
            }

            SSLSocket ssl = (SSLSocket) sf.createSocket(raw, t.host, t.port, true);

            // Hostname verification (HTTPS style)
            if (req.isHostnameVerification()) {
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(params);
            }

            ssl.startHandshake();

            SSLSession session = ssl.getSession();
            report.append("Handshake: OK\n");
            report.append("Protocol : ").append(session.getProtocol()).append("\n");
            report.append("Cipher   : ").append(session.getCipherSuite()).append("\n\n");

            // server chain from session (more reliable to print)
            Certificate[] peer = session.getPeerCertificates();
            report.append("Server Presented Certificates: ").append(peer.length).append("\n");
            for (int i = 0; i < peer.length; i++) {
                if (peer[i] instanceof X509Certificate) {
                    report.append(formatCert("  [" + i + "]", (X509Certificate) peer[i]));
                } else {
                    report.append("  [" + i + "] ").append(peer[i].getType()).append("\n");
                }
            }
            report.append("\n");

            // which client cert alias got chosen (if any)
            if (capturingKm != null) {
                String alias = capturingKm.getLastChosenClientAlias();
                report.append("Client Certificate Alias Selected: ")
                        .append(alias == null ? "(none / not requested by server)" : alias)
                        .append("\n");
            }

            // trust anchor alias best-effort
            if (capturingTm != null) {
                String trustAlias = capturingTm.getLikelyTrustAnchorAlias();
                report.append("Likely TrustStore Alias Used: ")
                        .append(trustAlias == null ? "(unknown / not derivable reliably)" : trustAlias)
                        .append("\n");
            }

            ok = true;
            try { ssl.close(); } catch (Exception ignore) {}
            return SslCheckResult.ok(report.toString());

        } catch (Exception e) {
            report.append("\nHandshake: FAILED\n");
            report.append("Error: ").append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
            return SslCheckResult.fail(report.toString());
        }
    }

    private TrustManager[] buildTrustManagers(SslCheckRequest req, KeyStore ks) throws Exception {
        KeyStore trust = null;
        if (req.isUseSameStoreAsTrust()) {
            trust = ks;
        } else {
            // if you later want a separate truststore, add fields and load it here
            trust = null;
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);

        TrustManager[] base = tmf.getTrustManagers();
        // wrap first X509TrustManager
        for (int i = 0; i < base.length; i++) {
            if (base[i] instanceof X509TrustManager) {
                base[i] = new CapturingTrustManager((X509TrustManager) base[i], trust);
                break;
            }
        }
        return base;
    }

    private CapturingTrustManager findCapturingTrustManager(TrustManager[] tms) {
        if (tms == null) return null;
        for (int i = 0; i < tms.length; i++) {
            if (tms[i] instanceof CapturingTrustManager) return (CapturingTrustManager) tms[i];
        }
        return null;
    }

    private X509ExtendedKeyManager pickX509KeyManager(KeyManager[] kms) {
        if (kms == null) return null;
        for (int i = 0; i < kms.length; i++) {
            if (kms[i] instanceof X509ExtendedKeyManager) {
                return (X509ExtendedKeyManager) kms[i];
            }
        }
        return null;
    }

    private KeyStore loadJks(String path, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            ks.load(in, password.toCharArray());
            return ks;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignore) {}
        }
    }

    private Socket connectViaHttpProxy(ProxyConfig proxy, String host, int port, StringBuilder report) throws Exception {
        Socket s = new Socket(proxy.getHost(), proxy.getPort());
        s.setSoTimeout(15000);

        // HTTP CONNECT
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        String connect =
                "CONNECT " + host + ":" + port + " HTTP/1.1\r\n" +
                        "Host: " + host + ":" + port + "\r\n" +
                        "Proxy-Connection: keep-alive\r\n" +
                        "\r\n";

        out.write(connect.getBytes("ISO-8859-1"));
        out.flush();

        // read response headers
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
        String statusLine = br.readLine();
        if (statusLine == null) {
            throw new IOException("Proxy returned empty response.");
        }

        report.append("Proxy CONNECT Response: ").append(statusLine).append("\n");

        // consume headers
        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) break;
        }

        if (!statusLine.contains("200")) {
            throw new IOException("Proxy CONNECT failed: " + statusLine);
        }

        return s;
    }

    private String formatCert(String prefix, X509Certificate c) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append("\n");
        sb.append("      Subject: ").append(c.getSubjectX500Principal().getName()).append("\n");
        sb.append("      Issuer : ").append(c.getIssuerX500Principal().getName()).append("\n");
        sb.append("      Serial : ").append(c.getSerialNumber().toString(16)).append("\n");
        sb.append("      NotBefore: ").append(sdf.format(c.getNotBefore())).append("\n");
        sb.append("      NotAfter : ").append(sdf.format(c.getNotAfter())).append("\n");
        sb.append("      SHA-256  : ").append(fingerprintSha256(c)).append("\n");
        return sb.toString();
    }

    private String fingerprintSha256(X509Certificate c) {
        try {
            byte[] der = c.getEncoded();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(der);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "(unavailable)";
        }
    }

    private static class Target {
        final String host;
        final int port;
        Target(String host, int port) { this.host = host; this.port = port; }
    }

    private Target parseTarget(String input) throws Exception {
        String s = input.trim();
        if (s.startsWith("https://") || s.startsWith("http://")) {
            URI u = new URI(s);
            String host = u.getHost();
            int port = u.getPort() > 0 ? u.getPort() : 443;
            if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("URL host is empty.");
            return new Target(host, port);
        }

        // host:port or host
        String host;
        int port = 443;
        int idx = s.lastIndexOf(':');
        if (idx > 0 && idx < s.length() - 1 && isAllDigits(s.substring(idx + 1))) {
            host = s.substring(0, idx).trim();
            port = Integer.parseInt(s.substring(idx + 1).trim());
        } else {
            host = s;
        }
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("Host is empty.");
        return new Target(host, port);
    }

    private boolean isAllDigits(String x) {
        for (int i = 0; i < x.length(); i++) {
            char ch = x.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return !x.isEmpty();
    }
}
