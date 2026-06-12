package ma.ensa.medtrend.models;

public class Lead {
    private String url;
    private String email;
    private boolean hasWhatsApp;
    private boolean isSslValid;
    private String serverInfo;
    private int riskScore;

    public Lead(String url) {
        this.url = url;
    }

    // Getters
    public String getUrl() { return url; }
    public String getEmail() { return email; }
    public boolean isHasWhatsApp() { return hasWhatsApp; }
    public boolean isSslValid() { return isSslValid; }
    public String getServerInfo() { return serverInfo; }
    public int getRiskScore() { return riskScore; }

    // Setters
    public void setEmail(String email) { this.email = email; }
    public void setHasWhatsApp(boolean hasWhatsApp) { this.hasWhatsApp = hasWhatsApp; }
    public void setSslValid(boolean sslValid) { this.isSslValid = sslValid; }
    public void setServerInfo(String serverInfo) { this.serverInfo = serverInfo; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
}