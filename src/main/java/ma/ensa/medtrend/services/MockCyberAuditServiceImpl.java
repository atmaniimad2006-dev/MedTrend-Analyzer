package ma.ensa.medtrend.services;

import ma.ensa.medtrend.models.Lead;

import java.util.List;
import java.util.Random;

public class MockCyberAuditServiceImpl implements ICyberAuditService {

    private static final String[] SERVER_OPTIONS = {
            "Apache 2.4",
            "nginx/1.18",
            "Cloudflare",
            "PHP/5.4 (Vulnerable)"
    };

    private final Random random = new Random();

    @Override
    public List<Lead> performAudit(List<Lead> leadsToAudit) {
        for (Lead lead : leadsToAudit) {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[CyberAudit] Thread interrompu durant l'audit de : " + lead.getUrl());
            }

            boolean sslValid = random.nextBoolean();
            lead.setSslValid(sslValid);

            String serverInfo = SERVER_OPTIONS[random.nextInt(SERVER_OPTIONS.length)];
            lead.setServerInfo(serverInfo);

            boolean isVulnerableServer = serverInfo.contains("Vulnerable");
            int riskScore;

            if (!sslValid && isVulnerableServer) {
                riskScore = 75 + random.nextInt(26);
            } else if (!sslValid || isVulnerableServer) {
                riskScore = 51 + random.nextInt(24);
            } else {
                riskScore = random.nextInt(36);
            }

            lead.setRiskScore(riskScore);

            System.out.println("[CyberAudit] Audit terminé pour " + lead.getUrl()
                    + " → SSL=" + sslValid
                    + ", Server=" + serverInfo
                    + ", Risk=" + riskScore);
        }

        return leadsToAudit;
    }
}
