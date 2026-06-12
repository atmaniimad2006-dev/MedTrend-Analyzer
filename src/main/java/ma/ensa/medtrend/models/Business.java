package ma.ensa.medtrend.models;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Model for Module 1 — Lead Generator.
 * Represents a business extracted from Google Maps that has a valid website.
 * Uses JavaFX properties for seamless TableView binding.
 */
public class Business {

    private final StringProperty companyName;
    private final StringProperty website;

    public Business(String companyName, String website) {
        this.companyName = new SimpleStringProperty(companyName);
        this.website = new SimpleStringProperty(website);
    }

    // ── Property Accessors (required for TableView column binding) ──

    public StringProperty companyNameProperty() { return companyName; }
    public StringProperty websiteProperty()     { return website; }

    // ── Standard Getters ────────────────────────────────────────────

    public String getCompanyName() { return companyName.get(); }
    public String getWebsite()     { return website.get(); }

    // ── Standard Setters ────────────────────────────────────────────

    public void setCompanyName(String name) { this.companyName.set(name); }
    public void setWebsite(String url)      { this.website.set(url); }

    @Override
    public String toString() {
        return "Business{name='" + getCompanyName() + "', website='" + getWebsite() + "'}";
    }
}
