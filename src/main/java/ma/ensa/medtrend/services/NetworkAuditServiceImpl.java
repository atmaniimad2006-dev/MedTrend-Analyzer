package ma.ensa.medtrend.services;

import ma.ensa.medtrend.models.AuditResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Network Auditor service implementation.
 *
 * For each URL:
 * 1. Uses Jsoup to fetch the page and extract contact emails (mailto: + regex).
 * 2. Uses java.net.HttpsURLConnection to check SSL certificate validity.
 * 3. Reads the "Server" and "X-Powered-By" headers for server tech detection.
 *
 * Threading: This class does NOT touch the JavaFX thread.
 *            Designed to be called from a javafx.concurrent.Task.
 */
public class NetworkAuditServiceImpl implements INetworkAuditService {

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5 MB

    private static final String EMAIL_REGEX =
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,7}";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    /** Sub-paths commonly containing contact info. */
    private static final String[] CONTACT_PATHS = {
            "/contact", "/contact-us", "/contactez-nous",
            "/about", "/about-us", "/a-propos"
    };

    /** Known false-positive email patterns. */
    private static final Set<String> BLACKLIST_FRAGMENTS = Set.of(
            "wixpress", "sentry", "example.com", "example.org",
            "your-email", "email@domain", "test@test",
            "wordpress", "developer.mozilla", "noreply",
            "no-reply", "mailer-daemon", "postmaster"
    );

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/17.5 Safari/605.1.15"
    };

    private final Random random = new Random();

    // ═════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═════════════════════════════════════════════════════════════════

    @Override
    public AuditResult auditSingleUrl(String url) {
        url = normalizeUrl(url);
        AuditResult result = new AuditResult(url);

        // ── Phase 1: Email & Phone extraction via Jsoup ─────────────
        Set<String> emails = new LinkedHashSet<>();
        Set<String> phones = new LinkedHashSet<>();
        extractContactInfo(url, emails, phones);
        result.setContactEmails(emails.isEmpty() ? "None found" : String.join(", ", emails));
        result.setPhoneNumbers(phones.isEmpty() ? "None found" : String.join(", ", phones));

        // ── Phase 2: SSL & Server Tech via java.net ─────────────
        performSecurityAudit(result);

        System.out.println("[NetworkAudit] Done: " + url +
                " | Emails=" + result.getContactEmails() +
                " | Phones=" + result.getPhoneNumbers() +
                " | SSL=" + result.isSslValid() +
                " | Server=" + result.getServerTech());

        return result;
    }

    @Override
    public List<AuditResult> auditBatch(List<String> urls, BiConsumer<Integer, AuditResult> onProgress) {
        List<AuditResult> results = new ArrayList<>();
        int index = 0;

        for (String url : urls) {
            AuditResult result = auditSingleUrl(url);
            results.add(result);
            index++;

            if (onProgress != null) {
                onProgress.accept(index, result);
            }
        }

        return results;
    }

    // ═════════════════════════════════════════════════════════════════
    //  EMAIL EXTRACTION (Jsoup)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Extracts emails and phones from the main page and common contact sub-pages.
     */
    private void extractContactInfo(String baseUrl, Set<String> allEmails, Set<String> allPhones) {
        // 1) Scrape main page
        Document mainDoc = fetchPage(baseUrl);
        if (mainDoc != null) {
            allEmails.addAll(extractEmailsFromDocument(mainDoc));
            allPhones.addAll(extractPhonesFromDocument(mainDoc));
        }

        // 2) If no emails/phones found, deep-crawl contact sub-pages
        if (allEmails.isEmpty() || allPhones.isEmpty()) {
            for (String path : CONTACT_PATHS) {
                Document subDoc = fetchPage(baseUrl + path);
                if (subDoc != null) {
                    if (allEmails.isEmpty()) allEmails.addAll(extractEmailsFromDocument(subDoc));
                    if (allPhones.isEmpty()) allPhones.addAll(extractPhonesFromDocument(subDoc));
                    if (!allEmails.isEmpty() && !allPhones.isEmpty()) break; // Stop once we find both
                }
            }
        }
    }

    /**
     * Extracts emails from a Jsoup Document using both mailto: links and regex.
     */
    private Set<String> extractEmailsFromDocument(Document doc) {
        Set<String> emails = new LinkedHashSet<>();

        // Strategy 1: mailto: links in the DOM
        Elements mailtoLinks = doc.select("a[href^=mailto:]");
        for (Element link : mailtoLinks) {
            String href = link.attr("href");
            String raw = href.replace("mailto:", "").split("\\?")[0].trim();
            if (isValidEmail(raw)) {
                emails.add(raw.toLowerCase(Locale.ROOT));
            }
        }

        // Strategy 2: Regex on full HTML (catches obfuscated emails)
        String html = doc.html()
                .replace("&#64;", "@")
                .replace("[at]", "@")
                .replace("(at)", "@");

        Matcher matcher = EMAIL_PATTERN.matcher(html);
        while (matcher.find()) {
            String candidate = matcher.group().toLowerCase(Locale.ROOT);
            if (isValidEmail(candidate)) {
                emails.add(candidate);
            }
            if (emails.size() >= 5) break; // Cap at 5 emails per URL
        }

        return emails;
    }

    /**
     * Extracts phone numbers from a Jsoup Document using tel: links.
     */
    private Set<String> extractPhonesFromDocument(Document doc) {
        Set<String> phones = new LinkedHashSet<>();
        Elements telLinks = doc.select("a[href^=tel:]");
        for (Element link : telLinks) {
            String href = link.attr("href");
            String raw = href.replace("tel:", "").trim();
            // Basic cleanup of URL encoding
            raw = raw.replace("%20", " ");
            if (!raw.isBlank()) {
                phones.add(raw);
            }
        }
        return phones;
    }

    // ═════════════════════════════════════════════════════════════════
    //  SECURITY AUDIT (java.net)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Checks SSL certificate validity and extracts server technology headers.
     */
    private void performSecurityAudit(AuditResult result) {
        String targetUrl = result.getUrl();

        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 MedTrend-NetworkAuditor/1.0");

            conn.connect();

            // ── SSL Check ───────────────────────────────────────
            if (conn instanceof HttpsURLConnection httpsConn) {
                try {
                    java.security.cert.Certificate[] certs = httpsConn.getServerCertificates();
                    if (certs != null && certs.length > 0 &&
                            certs[0] instanceof X509Certificate x509) {
                        Date expiry = x509.getNotAfter();
                        result.setSslValid(expiry.after(new Date()));
                    }
                } catch (Exception e) {
                    result.setSslValid(false);
                }
            } else {
                result.setSslValid(false); // HTTP, no SSL
            }

            // ── Server Technology Detection ─────────────────────
            String server = conn.getHeaderField("Server");
            String poweredBy = conn.getHeaderField("X-Powered-By");
            StringBuilder tech = new StringBuilder();
            if (server != null && !server.isBlank()) {
                tech.append(server);
            }
            if (poweredBy != null && !poweredBy.isBlank()) {
                if (tech.length() > 0) tech.append(" | ");
                tech.append(poweredBy);
            }
            result.setServerTech(tech.length() > 0 ? tech.toString() : "Not exposed");

            conn.disconnect();

        } catch (javax.net.ssl.SSLException e) {
            result.setSslValid(false);
            result.setServerTech("SSL Error");
        } catch (IOException e) {
            result.setSslValid(false);
            result.setServerTech("Unreachable");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═════════════════════════════════════════════════════════════════

    private Document fetchPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENTS[random.nextInt(USER_AGENTS.length)])
                    .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,fr;q=0.8")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();
        } catch (IOException e) {
            System.err.println("[NetworkAudit] Failed to fetch: " + url +
                    " → " + e.getMessage());
            return null;
        }
    }

    private String normalizeUrl(String raw) {
        String url = raw.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        String lower = email.toLowerCase(Locale.ROOT);
        for (String fragment : BLACKLIST_FRAGMENTS) {
            if (lower.contains(fragment)) return false;
        }
        // Must have a valid TLD
        int lastDot = lower.lastIndexOf('.');
        return lastDot >= 0 && lower.length() - lastDot - 1 >= 2;
    }
}
