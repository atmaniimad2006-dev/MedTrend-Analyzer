<h1 align="center">📊 MedTrend Analyzer</h1>

<p align="center">
  <i>MVC Desktop Application — B2B Data Extraction Pipeline, Network Security Audit & SQLite Persistence</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Maven-Build-C71A22?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven">
  <img src="https://img.shields.io/badge/Jsoup-Web_Scraping-43853D?style=for-the-badge" alt="Jsoup">
  <img src="https://img.shields.io/badge/Selenium-Dynamic_Crawling-43B02A?style=for-the-badge&logo=selenium&logoColor=white" alt="Selenium">
  <img src="https://img.shields.io/badge/SQLite-Persistence-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="SQLite">
  <img src="https://img.shields.io/badge/JavaFX-UI-FF0000?style=for-the-badge&logo=java&logoColor=white" alt="JavaFX">
</p>

---

## 📋 Overview

**MedTrend Analyzer** is a Java desktop application built for B2B lead generation and web infrastructure auditing. It takes a list of target URLs, extracts contact information (emails, WhatsApp presence) through multi-strategy web scraping, evaluates the security posture of each target's infrastructure (SSL, server headers), and persists results in a local SQLite database.

---

## 🏗️ Architecture

The project follows a strict **MVC (Model-View-Controller) + DAO** layered architecture:

```
┌─────────────────────────────────────────────────────────┐
│                     PRESENTATION                         │
│  controllers/                                            │
│  ├── MainController.java          (Navigation)           │
│  ├── DashboardController.java     (Analytics View)       │
│  ├── LeadGeneratorController.java (Scraping UI)          │
│  └── NetworkAuditorController.java(Audit UI)             │
│                                                          │
│  resources/views/  (.fxml layouts via SceneBuilder)       │
│  resources/css/    (Dark theme stylesheet)                │
├─────────────────────────────────────────────────────────┤
│                     BUSINESS LOGIC                        │
│  services/                                               │
│  ├── ScraperServiceImpl.java      (Jsoup + Deep Crawl)   │
│  ├── LeadGeneratorServiceImpl.java(Selenium Dynamic)     │
│  ├── NetworkAuditServiceImpl.java (SSL + Headers Audit)  │
│  └── CyberAuditServiceImpl.java  (Trust Score Engine)    │
├─────────────────────────────────────────────────────────┤
│                     DATA ACCESS                          │
│  dao/                                                    │
│  ├── DatabaseManager.java         (Singleton JDBC)       │
│  ├── ILeadDao.java               (Interface Contract)    │
│  └── LeadDaoImpl.java            (SQLite Implementation) │
├─────────────────────────────────────────────────────────┤
│                     DOMAIN MODEL                         │
│  models/                                                 │
│  ├── Lead.java        (url, email, whatsapp, ssl, risk)  │
│  ├── Business.java    (Business entity model)            │
│  └── AuditResult.java (Security audit result model)      │
└─────────────────────────────────────────────────────────┘
```

---

## ⚙️ Core Components

### 1. Web Scraper (`ScraperServiceImpl.java` — 457 lines)

A production-grade multi-strategy web scraper with 4 architectural pillars:

| Pillar | Implementation |
|---|---|
| **SSL Bypass** | Custom `TrustManager` accepting all certificates (self-signed, expired). `SSLContext.init()` with permissive `HostnameVerifier` |
| **WAF Evasion** | 5 rotating User-Agents (Chrome, Firefox, Safari, Edge), realistic browser headers (`Sec-CH-UA`, `Sec-Fetch-*`), Google referrer |
| **Deep Crawling** | Automatic sub-page traversal across 11 contact paths (`/contact`, `/fr/contact`, `/about`, `/nous-contacter`...) |
| **Extraction Pipeline** | 3-strategy email extraction: `mailto:` DOM parsing → Regex on raw HTML (with `&#64;`/`[at]` decoding) → Deep crawl fallback |

**Resilience patterns:**
- **Thread Pool:** `ExecutorService` with 5 concurrent threads
- **Retry with Backoff:** Up to 2 retries with randomized delay (200ms–2000ms)
- **HTTP 429 Handling:** Automatic backoff on rate limiting (2s–5s random wait)
- **HTTPS→HTTP Fallback:** Automatic protocol downgrade on SSL handshake failure
- **False Positive Filtering:** Blacklisted file extensions (`.png`, `.css`, `.js`...) and domains (`wixpress`, `sentry`, `example.com`...)

### 2. Network Auditor (`NetworkAuditServiceImpl.java`)

Evaluates target infrastructure security:
- **SSL Certificate Validation** (expiry, chain, protocol)
- **Server Header Analysis** (technology fingerprinting)
- **Trust Score Computation** (risk assessment)

### 3. Database Layer (`DatabaseManager.java`)

- **Singleton Pattern** for connection management
- **SQLite** embedded database (zero-config deployment)
- **Auto-schema initialization** via `CREATE TABLE IF NOT EXISTS`
- **DAO Interface** (`ILeadDao`) decoupling persistence from business logic

### 4. UI Layer (JavaFX + FXML)

- **SceneBuilder-designed** layouts
- **Dark theme** CSS stylesheet
- **Multi-view navigation** (Dashboard, Lead Generator, Network Auditor)
- **Resolution:** 1280×800 minimum

---

## 🛠️ Tech Stack

| Layer | Technology | Version |
|---|---|---|
| **Language** | Java (LTS) | 17 |
| **Build** | Apache Maven | 3.11+ |
| **Static Scraping** | Jsoup | 1.17.2 |
| **Dynamic Scraping** | Selenium + WebDriverManager | 4.21.0 / 5.8.0 |
| **Database** | SQLite via JDBC | 3.45.2 |
| **UI Framework** | JavaFX (FXML) | 17.0.6 |
| **Data Export** | OpenCSV | 5.9 |

---

## 🚀 Quick Start

```bash
# 1. Clone
git clone https://github.com/atmaniimad2006-dev/medtrend-analyzer.git
cd medtrend-analyzer

# 2. Build
mvn clean compile

# 3. Run
mvn javafx:run
```

**Prerequisites:** Java 17+, Maven 3.8+

---

## 📁 Project Structure

```
medtrend-analyzer/
├── src/main/java/ma/ensa/medtrend/
│   ├── Main.java                    # JavaFX Application entry point
│   ├── Launcher.java                # Module-safe launcher
│   ├── controllers/                 # 4 FXML controllers
│   ├── services/                    # 9 service classes (interfaces + impls)
│   ├── dao/                         # Singleton DB manager + DAO
│   └── models/                      # 3 domain objects
├── src/main/resources/
│   ├── views/                       # FXML layouts
│   ├── css/                         # Dark theme stylesheet
│   └── db/                          # SQL schemas
├── pom.xml                          # Maven build configuration
└── README.md
```

---

## 📄 License

Academic project — ENSA Khouribga.
