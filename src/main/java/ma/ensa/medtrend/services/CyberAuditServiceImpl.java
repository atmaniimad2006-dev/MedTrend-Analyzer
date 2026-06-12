package ma.ensa.medtrend.services;

import ma.ensa.medtrend.models.Lead;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

/**
 * Real cyber-audit service using pure {@link java.net.HttpsURLConnection}.
 * Inspects SSL certificate validity, extracts server headers,
 * and computes a basic risk score for each Lead.
 */
public class CyberAuditServiceImpl implements ICyberAuditService {

    private static final int TIMEOUT_MS = 8000;

    @Override
    public List<Lead> performAudit(List<Lead> leadsToAudit) {
        for (Lead lead : leadsToAudit) {
            auditSingleLead(lead);
        }
        return leadsToAudit;
    }

    private void auditSingleLead(Lead lead) {
        String targetUrl = lead.getUrl();
        if (targetUrl == null || targetUrl.isBlank()) {
            lead.setSslValid(false);
            lead.setServerInfo("N/A");
            lead.setRiskScore(100);
            return;
        }

        // Ensure the URL starts with a protocol
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://" + targetUrl;
        }

        boolean isSsl = targetUrl.startsWith("https://");
        boolean sslValid = false;
        String serverInfo = "Inconnu";
        int riskScore = 0;

        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MedTrend-Auditor/1.0");

            conn.connect();

            // ── SSL Certificate Inspection ───────────────────────
            if (conn instanceof HttpsURLConnection httpsConn) {
                try {
                    Certificate[] certs = httpsConn.getServerCertificates();
                    if (certs != null && certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                        Date expiry = x509.getNotAfter();
                        sslValid = expiry.after(new Date());
                    }
                } catch (Exception e) {
                    sslValid = false;
                }
            }

            // ── Header Extraction ────────────────────────────────
            String server = conn.getHeaderField("Server");
            String poweredBy = conn.getHeaderField("X-Powered-By");
            StringBuilder sb = new StringBuilder();
            if (server != null && !server.isBlank()) {
                sb.append(server);
            }
            if (poweredBy != null && !poweredBy.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(poweredBy);
            }
            serverInfo = sb.length() > 0 ? sb.toString() : "Non exposé";

            // ── Risk Score Computation ───────────────────────────
            if (!isSsl) {
                riskScore += 50;   // No HTTPS at all
            } else if (!sslValid) {
                riskScore += 40;   // HTTPS with invalid/expired cert
            }

            if (server != null && !server.isBlank()) {
                riskScore += 30;   // Server version exposed
            }
            if (poweredBy != null && !poweredBy.isBlank()) {
                riskScore += 20;   // Technology stack exposed
            }

            conn.disconnect();

        } catch (javax.net.ssl.SSLException e) {
            sslValid = false;
            riskScore = 80;
            serverInfo = "Erreur SSL";
            System.err.println("[CyberAudit] Erreur SSL pour " + targetUrl + " : " + e.getMessage());
        } catch (IOException e) {
            riskScore = 60;
            serverInfo = "Injoignable";
            System.err.println("[CyberAudit] Erreur IO pour " + targetUrl + " : " + e.getMessage());
        }

        lead.setSslValid(sslValid);
        lead.setServerInfo(serverInfo);
        lead.setRiskScore(Math.min(riskScore, 100));

        System.out.println("[CyberAudit] Audit terminé pour " + lead.getUrl()
                + " → SSL=" + sslValid
                + ", Server=" + serverInfo
                + ", Risk=" + lead.getRiskScore());
    }
}
