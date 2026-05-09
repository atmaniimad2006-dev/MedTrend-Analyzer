<h1 align="center"> 📊 MedTrend Analyzer </h1>

<p align="center">
  <i>Système B2B d'Extraction de Données et d'Audit de Sécurité Réseau</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Maven-C71A22?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven">
  <img src="https://img.shields.io/badge/SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="SQLite">
  <img src="https://img.shields.io/badge/JavaFX-FF0000?style=for-the-badge&logo=java&logoColor=white" alt="JavaFX">
</p>

---

## 📑 Sommaire
1. [Vision Globale (Executive Summary)](#1-vision-globale-executive-summary)
2. [Stack Technologique](#2-stack-technologique)
3. [Architecture Système](#3-architecture-système)
4. [Périmètre MVP & Fonctionnalités](#4-périmètre-mvp--fonctionnalités)
5. [Modules & Répartition des Tâches](#5-modules--répartition-des-tâches)
6. [Plan d'Action (Sprint)](#6-plan-daction-sprint)

---

## 1. Vision Globale (Executive Summary)

> **MedTrend Analyzer** est une application Desktop conçue pour l'extraction de métadonnées web couplée à un audit réseau dynamique. 

L'objectif est d'automatiser la génération de prospects qualifiés (Leads B2B) tout en évaluant la fiabilité de leur infrastructure web. Le système prend en entrée des URLs cibles, extrait les informations de contact, audite la sécurité des serveurs, et restitue un "Trust Score" exportable.

---

## 2. Stack Technologique

L'infrastructure est 100% Java natif, optimisée pour le découplage et la performance :

* **Core :** `Java 17 (LTS)` - Utilisation de l'API Streams et Multithreading.
* **Moteur d'Extraction :** `Jsoup` - Parsing HTML ultra-rapide et bas niveau.
* **Moteur d'Audit :** `java.net` - Implémentation native (`HttpsURLConnection`, `SSLContext`).
* **Persistance :** `SQLite` + `JDBC` - Base de données embarquée (Zero configuration).
* **Interface & UI :** `JavaFX` - Architecture FXML via SceneBuilder.
* **Build Manager :** `Maven` - Résolution stricte des dépendances.

---

## 3. Architecture Système

Le projet respecte rigoureusement le pattern **MVC (Modèle-Vue-Contrôleur)** et **DAO (Data Access Object)**.

```text
MedTrend-Analyzer/
├── src/main/java/ma/ensa/medtrend/
│   ├── models/           # Objets métier (Lead.java, TrustScore.java)
│   ├── dao/              # Accès BDD (LeadDAO.java, SQLiteConnection.java)
│   ├── services/         # Logique (JsoupScraper.java, NetworkAuditor.java)
│   ├── controllers/      # Chefs d'orchestre (DashboardController.java)
│   └── Main.java         # Point d'entrée asynchrone
└── src/main/resources/
    ├── views/            # Fichiers UI (.fxml)
    └── db/               # Schémas SQL

