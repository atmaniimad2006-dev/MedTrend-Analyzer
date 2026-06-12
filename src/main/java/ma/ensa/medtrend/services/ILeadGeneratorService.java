package ma.ensa.medtrend.services;

import ma.ensa.medtrend.models.Business;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Service interface for Module 1 — Lead Generator.
 * Extracts businesses from Google Maps using Selenium WebDriver.
 */
public interface ILeadGeneratorService {

    /**
     * Scrapes Google Maps for businesses matching the given niche and location.
     *
     * @param niche       The business sector / keyword (e.g., "dentiste", "clinique")
     * @param location    The target geographic area (e.g., "Casablanca")
     * @param maxLeads    Maximum number of leads to extract
     * @param onLeadFound Callback fired for each valid lead found.
     *                    Parameters: (current count, the Business found).
     *                    Allows real-time UI updates from the Task.
     * @return The complete list of extracted businesses (only those with a valid website).
     */
    List<Business> scrapeGoogleMaps(String niche, String location, int maxLeads,
                                     BiConsumer<Integer, Business> onLeadFound);
}
