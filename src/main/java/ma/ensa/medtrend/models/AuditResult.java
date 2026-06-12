package ma.ensa.medtrend.models;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;

/**
 * Model for Module 2 — Network Auditor.
 * Stores the results of a security audit for a single URL.
 * Uses JavaFX properties for seamless TableView binding.
 */
public class AuditResult {

    private final StringProperty  url;
    private final StringProperty  contactEmails;
    private final StringProperty  phoneNumbers;
    private final BooleanProperty sslValid;
    private final StringProperty  serverTech;

    public AuditResult(String url) {
        this.url           = new SimpleStringProperty(url);
        this.contactEmails = new SimpleStringProperty("");
        this.phoneNumbers  = new SimpleStringProperty("");
        this.sslValid      = new SimpleBooleanProperty(false);
        this.serverTech    = new SimpleStringProperty("Unknown");
    }

    // ── Property Accessors ──────────────────────────────────────────

    public StringProperty  urlProperty()           { return url; }
    public StringProperty  contactEmailsProperty() { return contactEmails; }
    public StringProperty  phoneNumbersProperty()  { return phoneNumbers; }
    public BooleanProperty sslValidProperty()      { return sslValid; }
    public StringProperty  serverTechProperty()    { return serverTech; }

    // ── Standard Getters ────────────────────────────────────────────

    public String  getUrl()           { return url.get(); }
    public String  getContactEmails() { return contactEmails.get(); }
    public String  getPhoneNumbers()  { return phoneNumbers.get(); }
    public boolean isSslValid()       { return sslValid.get(); }
    public String  getServerTech()    { return serverTech.get(); }

    // ── Standard Setters ────────────────────────────────────────────

    public void setUrl(String url)                  { this.url.set(url); }
    public void setContactEmails(String emails)     { this.contactEmails.set(emails); }
    public void setPhoneNumbers(String phones)      { this.phoneNumbers.set(phones); }
    public void setSslValid(boolean valid)           { this.sslValid.set(valid); }
    public void setServerTech(String tech)           { this.serverTech.set(tech); }
}
