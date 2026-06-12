package ma.ensa.medtrend.services;

import ma.ensa.medtrend.models.AuditResult;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Service interface for Module 2 — Network Auditor.
 * Performs security audits: email extraction (Jsoup), SSL check, server tech detection.
 */
public interface INetworkAuditService {

    /**
     * Audits a single URL.
     *
     * @param url The target URL to audit.
     * @return An AuditResult with emails, SSL status, and server tech.
     */
    AuditResult auditSingleUrl(String url);

    /**
     * Audits a batch of URLs (e.g., loaded from CSV).
     *
     * @param urls          List of URLs to audit.
     * @param onProgress    Callback fired after each URL is audited.
     *                      Parameters: (current index, the AuditResult).
     * @return Complete list of AuditResults.
     */
    List<AuditResult> auditBatch(List<String> urls, BiConsumer<Integer, AuditResult> onProgress);
}
