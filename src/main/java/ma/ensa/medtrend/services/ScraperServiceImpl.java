package ma.ensa.medtrend.services;

import ma.ensa.medtrend.models.Lead;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper robuste et tolérant aux pannes.
 *
 * Pilier 1 — SSL BYPASS TOTAL : TrustManager permissif acceptant tout certificat.
 * Pilier 2 — ÉVASION WAF      : User-Agents rotatifs, en-têtes réalistes, délai aléatoire anti-429.
 * Pilier 3 — DEEP CRAWLING    : Rebond automatique sur les sous-pages /contact, /fr/contact, etc.
 * Pilier 4 — EXTRACTION       : mailto: DOM + Regex, filtrage des faux positifs.
 */
public class ScraperServiceImpl implements IScraperService {

    // ═══════════════════════════════════════════════════════════
    //  CONFIGURATION
    // ═══════════════════════════════════════════════════════════

    private static final String EMAIL_REGEX =
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,7}";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    private static final int THREAD_POOL_SIZE = 5;
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5 MB max
    private static final int MAX_RETRY = 2;

    /** Sous-chemins courants pour la recherche d'emails en profondeur. */
    private static final String[] CONTACT_PATHS = {
            "/contact",
            "/contact-us",
            "/fr/contact",
            "/contactez-nous",
            "/nous-contacter",
            "/about",
            "/about-us",
            "/a-propos",
            "/fr/contactez-nous",
            "/en/contact",
            "/qui-sommes-nous"
    };

    /** Pool de User-Agents réalistes (Chrome, Firefox, Safari, Edge). */
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0"
    };

    /** Extensions de faux positifs à exclure. */
    private static final Set<String> BLACKLIST_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp",
            ".css", ".js", ".woff", ".woff2", ".ttf", ".eot", ".map"
    );

    /** Domaines / fragments typiques de faux positifs. */
    private static final Set<String> BLACKLIST_FRAGMENTS = Set.of(
            "wixpress", "sentry", "example.com", "example.org",
            "your-email", "email@domain", "test@test",
            "wordpress", "developer.mozilla"
    );

    private final Random random = new Random();

    // ═══════════════════════════════════════════════════════════
    //  PILIER 1 — SSL BYPASS TOTAL (static init)
    // ═══════════════════════════════════════════════════════════
    static {
        try {
            // TrustManager qui accepte TOUS les certificats (expirés, auto-signés, etc.)
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String auth) { }
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String auth) { }
                        @Override
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

            // HostnameVerifier qui accepte tout hostname
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            System.out.println("[Scraper] SSL Bypass activé — tous les certificats acceptés.");
        } catch (Exception e) {
            System.err.println("[Scraper] ERREUR lors de l'init SSL bypass : " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  POINT D'ENTRÉE PUBLIC
    // ═══════════════════════════════════════════════════════════

    @Override
    public List<Lead> extractData(List<String> urls) {
        List<Lead> extractedLeads = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (String url : urls) {
            executor.submit(() -> {
                Lead lead = scrapeSingleUrl(url);
                if (lead != null) {
                    extractedLeads.add(lead);
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Scraper] Pool interrompu : " + e.getMessage());
        }

        return extractedLeads;
    }

    // ═══════════════════════════════════════════════════════════
    //  LOGIQUE PRINCIPALE DE SCRAPING
    // ═══════════════════════════════════════════════════════════

    private Lead scrapeSingleUrl(String rawUrl) {
        // Nettoyage URL
        String url = normalizeUrl(rawUrl);
        Lead lead = new Lead(url);

        try {
            // ── Étape 1 : Scraper la page principale ─────────────
            Document doc = fetchWithRetry(url);

            // ── Fallback HTTP si HTTPS échoue (connection reset, handshake error) ──
            if (doc == null && url.startsWith("https://")) {
                String httpUrl = url.replace("https://", "http://");
                System.out.println("[Scraper] HTTPS échoué → tentative HTTP : " + httpUrl);
                doc = fetchWithRetry(httpUrl);
                if (doc != null) {
                    url = httpUrl; // utiliser l'URL HTTP pour le deep crawling
                }
            }

            if (doc == null) {
                lead.setEmail("Non trouvé");
                lead.setHasWhatsApp(false);
                return lead;
            }

            String html = doc.html();
            System.out.println("[Scraper] " + url
                    + " → titre: \"" + doc.title() + "\""
                    + " | HTML: " + html.length() + " chars");

            // Extraction WhatsApp (page principale)
            lead.setHasWhatsApp(detectWhatsApp(html));

            // ── Extraction Email : Stratégie combinée ────────────
            // 1) Chercher d'abord dans les liens mailto: (plus fiable)
            String email = extractMailtoFromDom(doc);

            // 2) Si pas de mailto:, chercher par Regex dans le HTML brut
            if (email == null) {
                email = extractEmailByRegex(html);
            }

            // ── Étape 2 : DEEP CRAWLING — sous-pages contact ─────
            if (email == null) {
                System.out.println("[Scraper] Aucun email sur la page principale → deep crawling...");
                email = deepCrawlForEmail(url);
            }

            lead.setEmail(email != null ? email : "Non trouvé");

            return lead;

        } catch (Exception e) {
            System.err.println("[Scraper] Erreur fatale sur " + url + " : " + e.getMessage());
            lead.setEmail("Non trouvé");
            lead.setHasWhatsApp(false);
            return lead;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PILIER 2 — CONNEXION ROBUSTE + ÉVASION WAF
    // ═══════════════════════════════════════════════════════════

    /**
     * Tente de récupérer un Document avec retry automatique.
     * Gère les erreurs 429 (Rate Limiting) avec un backoff aléatoire.
     */
    private Document fetchWithRetry(String url) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                // Délai aléatoire anti-rate-limiting (200ms à 1200ms)
                if (attempt > 1) {
                    long delay = 500 + random.nextInt(2000);
                    System.out.println("[Scraper] Retry " + attempt + "/" + MAX_RETRY
                            + " après " + delay + "ms pour " + url);
                    Thread.sleep(delay);
                } else {
                    // Petit délai même au premier essai pour être poli
                    Thread.sleep(200 + random.nextInt(500));
                }

                Connection.Response response = buildConnection(url).execute();
                int status = response.statusCode();

                // ── Gestion HTTP 429 (Rate Limiting) ─────────────
                if (status == 429) {
                    long wait = 2000 + random.nextInt(3000);
                    System.err.println("[Scraper] 429 Rate Limited sur " + url
                            + " — attente " + wait + "ms...");
                    Thread.sleep(wait);
                    continue; // retry
                }

                // ── Gestion Content-Type inattendu (PDF, image...) ─
                String contentType = response.contentType();
                if (contentType != null && !contentType.contains("text/html")
                        && !contentType.contains("application/xhtml")) {
                    System.out.println("[Scraper] " + url + " → Content-Type ignoré: " + contentType);
                    return null;
                }

                // ── Gestion redirections / erreurs HTTP ──────────
                if (status >= 400) {
                    System.err.println("[Scraper] " + url + " → HTTP " + status);
                    if (status >= 500 && attempt < MAX_RETRY) continue; // retry on 5xx
                    return null;
                }

                return response.parse();

            } catch (IOException e) {
                System.err.println("[Scraper] Tentative " + attempt + "/" + MAX_RETRY
                        + " échouée pour " + url + " : " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * Construit une connexion Jsoup avec des en-têtes de navigateur réalistes
     * pour contourner les WAF basiques (Cloudflare, ModSecurity, etc.).
     */
    private Connection buildConnection(String url) {
        String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];

        return Jsoup.connect(url)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Cache-Control", "max-age=0")
                .header("Sec-CH-UA", "\"Chromium\";v=\"126\", \"Not;A=Brand\";v=\"8\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"Windows\"")
                .referrer("https://www.google.com/")
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY_SIZE)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .sslSocketFactory(getPermissiveSSLFactory());
    }

    /**
     * Retourne une SSLSocketFactory qui accepte tous les certificats.
     * Utilisé par Jsoup directement (contourne le SSLContext par défaut si besoin).
     */
    private SSLSocketFactory getPermissiveSSLFactory() {
        try {
            TrustManager[] trustAll = { new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] c, String a) { }
                @Override public void checkServerTrusted(X509Certificate[] c, String a) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PILIER 3 — DEEP CRAWLING (Sous-pages contact)
    // ═══════════════════════════════════════════════════════════

    /**
     * Parcourt les sous-pages de contact courantes pour trouver un email.
     * Combine mailto: et Regex sur chaque sous-page.
     * S'arrête dès qu'un email valide est trouvé.
     */
    private String deepCrawlForEmail(String baseUrl) {
        for (String path : CONTACT_PATHS) {
            String subUrl = baseUrl + path;
            try {
                Document doc = fetchWithRetry(subUrl);
                if (doc == null) continue;

                String html = doc.html();

                // Stratégie 1 : mailto: dans le DOM
                String email = extractMailtoFromDom(doc);

                // Stratégie 2 : Regex
                if (email == null) {
                    email = extractEmailByRegex(html);
                }

                if (email != null) {
                    System.out.println("[Scraper] ✅ Email trouvé via " + path + " → " + email);
                    return email;
                } else {
                    System.out.println("[Scraper]    " + path + " → 200 OK, mais pas d'email");
                }

            } catch (Exception e) {
                System.out.println("[Scraper]    " + path + " → inaccessible (" + e.getMessage() + ")");
            }
        }

        System.out.println("[Scraper] ❌ Aucun email trouvé même après deep crawling.");
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  PILIER 4 — EXTRACTION CHIRURGICALE
    // ═══════════════════════════════════════════════════════════

    /**
     * Extrait un email depuis les liens mailto: du DOM (plus fiable que Regex).
     * Cherche dans les balises <a href="mailto:...">.
     */
    private String extractMailtoFromDom(Document doc) {
        Elements mailtoLinks = doc.select("a[href^=mailto:]");
        for (Element link : mailtoLinks) {
            String href = link.attr("href");
            // Extraire l'email du href : "mailto:contact@example.ma?subject=..." → "contact@example.ma"
            String raw = href.replace("mailto:", "").split("\\?")[0].trim();
            if (!raw.isEmpty() && isValidEmail(raw)) {
                return raw;
            }
        }
        return null;
    }

    /**
     * Extrait le premier email valide du HTML brut par Regex.
     * Applique un filtrage strict contre les faux positifs.
     */
    private String extractEmailByRegex(String html) {
        // Chercher aussi les emails encodés en HTML entity (&#64; = @)
        String decoded = html.replace("&#64;", "@")
                             .replace("[at]", "@")
                             .replace(" [at] ", "@")
                             .replace("(at)", "@")
                             .replace(" (at) ", "@");

        Matcher matcher = EMAIL_PATTERN.matcher(decoded);
        while (matcher.find()) {
            String candidate = matcher.group().toLowerCase(Locale.ROOT);
            if (isValidEmail(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Valide qu'un email candidat n'est pas un faux positif.
     * Vérifie les extensions de fichiers et les fragments blacklistés.
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;

        String lower = email.toLowerCase(Locale.ROOT);

        // Vérifier les extensions de fichiers (faux positifs d'assets)
        for (String ext : BLACKLIST_EXTENSIONS) {
            if (lower.endsWith(ext)) return false;
        }

        // Vérifier les fragments blacklistés
        for (String fragment : BLACKLIST_FRAGMENTS) {
            if (lower.contains(fragment)) return false;
        }

        // Rejeter les emails génériques / inutiles
        if (lower.startsWith("noreply@") || lower.startsWith("no-reply@")
                || lower.startsWith("mailer-daemon@")
                || lower.startsWith("postmaster@")) {
            return false;
        }

        // Doit contenir un TLD réaliste (au moins 2 caractères après le dernier point)
        int lastDot = lower.lastIndexOf('.');
        if (lastDot < 0 || lower.length() - lastDot - 1 < 2) return false;

        return true;
    }

    // ═══════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ═══════════════════════════════════════════════════════════

    /** Normalise l'URL brute en ajoutant https:// et retirant le trailing slash. */
    private String normalizeUrl(String raw) {
        String url = raw.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /** Détecte la présence de liens WhatsApp dans le HTML. */
    private boolean detectWhatsApp(String html) {
        return html.contains("wa.me/")
                || html.contains("api.whatsapp.com/")
                || html.contains("whatsapp.com/send")
                || html.contains("wa.link/");
    }
}
