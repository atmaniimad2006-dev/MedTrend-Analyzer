package ma.ensa.medtrend.services;

import io.github.bonigarcia.wdm.WebDriverManager;
import ma.ensa.medtrend.models.Business;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Selenium-based Google Maps scraper.
 *
 * Strategy:
 * 1. Opens Google Maps with the search query "{niche} in {location}".
 * 2. Scrolls the results panel to load all available results.
 * 3. For each result, clicks into it, extracts the business name and website.
 * 4. CRITICAL: Only businesses with a valid website are kept.
 * 5. Fires onLeadFound callback for each valid lead (enables real-time UI updates).
 *
 * Threading: This class does NOT touch the JavaFX thread.
 *            It is designed to be called from a javafx.concurrent.Task.
 */
public class LeadGeneratorServiceImpl implements ILeadGeneratorService {

    private static final Duration PAGE_LOAD_TIMEOUT   = Duration.ofSeconds(30);
    private static final Duration IMPLICIT_WAIT       = Duration.ofSeconds(5);
    private static final Duration EXPLICIT_WAIT       = Duration.ofSeconds(10);
    private static final int      SCROLL_PAUSE_MS     = 1500;
    private static final int      CLICK_PAUSE_MS      = 800;

    @Override
    public List<Business> scrapeGoogleMaps(String niche, String location, int maxLeads,
                                            BiConsumer<Integer, Business> onLeadFound) {

        List<Business> results = new ArrayList<>();
        Set<String> seenWebsites = new HashSet<>();

        // ── Setup Selenium Chrome (headless) ────────────────────
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));

        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
            driver.manage().timeouts().implicitlyWait(IMPLICIT_WAIT);

            // ── Navigate to Google Maps search ──────────────────
            String query = niche.trim() + " in " + location.trim();
            String searchUrl = "https://www.google.com/maps/search/" +
                    query.replace(" ", "+");

            System.out.println("[LeadGen] Navigating to: " + searchUrl);
            driver.get(searchUrl);

            // ── Accept Google consent dialog if present ─────────
            dismissConsentDialog(driver);

            // ── Wait for the results feed to load ───────────────
            WebDriverWait wait = new WebDriverWait(driver, EXPLICIT_WAIT);
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("div[role='feed']")));
            } catch (TimeoutException e) {
                System.err.println("[LeadGen] Results feed not found. " +
                        "Attempting fallback with result list...");
            }

            int index = 0;
            int stableScrollIterations = 0;
            int previousTotalElements = 0;

            // ── Extract data until we hit maxLeads or run out of results ──
            while (results.size() < maxLeads) {
                List<WebElement> currentItems = driver.findElements(
                        By.cssSelector("div[role='feed'] > div > div > a"));
                if (currentItems.isEmpty()) {
                    currentItems = driver.findElements(
                            By.cssSelector("a[href*='/maps/place/']"));
                }

                if (index >= currentItems.size()) {
                    // ── Scroll the results panel to load more items ─────
                    try {
                        WebElement feed = driver.findElement(By.cssSelector("div[role='feed']"));
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollTop = arguments[0].scrollHeight;", feed);
                        Thread.sleep(SCROLL_PAUSE_MS);

                        int newSize = driver.findElements(
                                By.cssSelector("div[role='feed'] > div > div > a")).size();
                        
                        if (newSize == previousTotalElements) {
                            stableScrollIterations++;
                            if (stableScrollIterations >= 3) { // Infinite Loop Failsafe
                                System.out.println("[LeadGen] Reached bottom of results. No new items after scrolling. Failsafe triggered.");
                                break;
                            }
                        } else {
                            stableScrollIterations = 0;
                            previousTotalElements = newSize;
                        }
                    } catch (Exception e) {
                        System.err.println("[LeadGen] Scroll error: " + e.getMessage());
                        break;
                    }
                    continue; // Try again after scrolling
                }

                WebElement item;
                try {
                    item = currentItems.get(index);
                } catch (IndexOutOfBoundsException e) {
                    break;
                }
                index++;

                try {
                    // Scroll the item into view and click it
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].scrollIntoView({block:'center'});", item);
                    Thread.sleep(300);
                    item.click();
                    Thread.sleep(CLICK_PAUSE_MS);

                    // ── Extract business name ───────────────────
                    String businessName = extractBusinessName(driver);
                    if (businessName == null || businessName.isBlank()) continue;

                    // ── Extract website (CRITICAL: skip if none or blacklisted) ─
                    String website = extractWebsite(driver);
                    if (website == null || website.isBlank() || isBlacklisted(website)) {
                        System.out.println("[LeadGen] SKIP (no website or blacklisted): " + businessName + " [" + website + "]");
                        navigateBackToResults(driver);
                        continue;
                    }

                    // De-duplicate by website
                    if (seenWebsites.contains(website.toLowerCase())) {
                        navigateBackToResults(driver);
                        continue;
                    }
                    seenWebsites.add(website.toLowerCase());

                    Business business = new Business(businessName, website);
                    results.add(business);

                    // ── Fire callback for real-time UI update ───
                    if (onLeadFound != null) {
                        onLeadFound.accept(results.size(), business);
                    }

                    System.out.println("[LeadGen] ✅ #" + results.size() +
                            " | " + businessName + " → " + website);

                    navigateBackToResults(driver);

                } catch (StaleElementReferenceException e) {
                    System.err.println("[LeadGen] Stale element at index " + (index-1) + ", skipping.");
                } catch (ElementClickInterceptedException e) {
                    System.err.println("[LeadGen] Click intercepted at index " + (index-1) + ", skipping.");
                } catch (Exception e) {
                    System.err.println("[LeadGen] Error at index " + (index-1) + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[LeadGen] Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }

        System.out.println("[LeadGen] Extraction complete. " +
                results.size() + " businesses with websites found.");
        return results;
    }

    // ═════════════════════════════════════════════════════════════════
    //  EXTRACTION HELPERS
    // ═════════════════════════════════════════════════════════════════

    /**
     * Extracts the business name from the currently focused Maps detail panel.
     */
    private String extractBusinessName(WebDriver driver) {
        // Primary selector: h1 inside the place details
        try {
            WebElement nameEl = driver.findElement(By.cssSelector("h1.DUwDvf"));
            return nameEl.getText().trim();
        } catch (NoSuchElementException ignored) {}

        // Fallback: any h1 in the panel
        try {
            WebElement nameEl = driver.findElement(
                    By.cssSelector("div[role='main'] h1"));
            return nameEl.getText().trim();
        } catch (NoSuchElementException ignored) {}

        return null;
    }

    /**
     * Extracts the website URL from the currently focused Maps detail panel.
     * Returns null if no website link is present.
     */
    private String extractWebsite(WebDriver driver) {
        // Strategy 1: Look for the website button/link with data-item-id="authority"
        try {
            WebElement websiteEl = driver.findElement(
                    By.cssSelector("a[data-item-id='authority']"));
            String href = websiteEl.getAttribute("href");
            if (href != null && !href.isBlank()) return cleanUrl(href);
        } catch (NoSuchElementException ignored) {}

        // Strategy 2: Look for aria-label containing "Website" or "Site Web"
        try {
            List<WebElement> links = driver.findElements(By.cssSelector("a[aria-label]"));
            for (WebElement link : links) {
                String label = link.getAttribute("aria-label");
                if (label != null && (label.toLowerCase().contains("website") ||
                        label.toLowerCase().contains("site web") ||
                        label.toLowerCase().contains("site internet"))) {
                    String href = link.getAttribute("href");
                    if (href != null && !href.isBlank() &&
                            !href.contains("google.com/maps")) {
                        return cleanUrl(href);
                    }
                }
            }
        } catch (Exception ignored) {}

        // Strategy 3: Look for link with icon class containing "website"
        try {
            WebElement websiteEl = driver.findElement(
                    By.xpath("//a[contains(@href, 'http') and not(contains(@href, 'google.com/maps'))]" +
                            "[ancestor::div[contains(@class, 'rogA2c') or contains(@class, 'Io6YTe')]]"));
            String href = websiteEl.getAttribute("href");
            if (href != null && !href.isBlank()) return cleanUrl(href);
        } catch (NoSuchElementException ignored) {}

        return null;
    }

    /**
     * Checks if the extracted URL belongs to a blacklisted domain.
     */
    private boolean isBlacklisted(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("facebook") || lowerUrl.contains("instagram") ||
               lowerUrl.contains("twitter") || lowerUrl.contains("linkedin") ||
               lowerUrl.contains("dabadoc") || lowerUrl.contains("linktr.ee") ||
               lowerUrl.contains("yellowpages");
    }

    /**
     * Navigates back to the results list after viewing a place detail.
     */
    private void navigateBackToResults(WebDriver driver) {
        try {
            // Click the back button in the details panel
            WebElement backBtn = driver.findElement(
                    By.cssSelector("button[aria-label='Back'], " +
                            "button[aria-label='Retour'], " +
                            "button[jsaction*='back']"));
            backBtn.click();
            Thread.sleep(CLICK_PAUSE_MS);
        } catch (NoSuchElementException e) {
            // If no back button, use browser back
            driver.navigate().back();
            try { Thread.sleep(CLICK_PAUSE_MS); } catch (InterruptedException ignored) {}
        } catch (Exception e) {
            driver.navigate().back();
            try { Thread.sleep(CLICK_PAUSE_MS); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Dismisses Google's consent / cookie dialog if present.
     */
    private void dismissConsentDialog(WebDriver driver) {
        try {
            Thread.sleep(2000);
            // Look for "Accept all" or "Tout accepter" button
            List<WebElement> buttons = driver.findElements(By.cssSelector("button"));
            for (WebElement btn : buttons) {
                String text = btn.getText().toLowerCase();
                if (text.contains("accept all") || text.contains("tout accepter") ||
                        text.contains("accept") || text.contains("agree")) {
                    btn.click();
                    Thread.sleep(1000);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Cleans and normalizes a URL extracted from Maps.
     */
    private String cleanUrl(String url) {
        if (url == null) return null;
        url = url.trim();
        // Remove Google redirect wrapper if present
        if (url.contains("google.com/url?q=")) {
            int start = url.indexOf("q=") + 2;
            int end = url.indexOf("&", start);
            url = end > start ? url.substring(start, end) : url.substring(start);
        }
        // Remove trailing slashes
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
