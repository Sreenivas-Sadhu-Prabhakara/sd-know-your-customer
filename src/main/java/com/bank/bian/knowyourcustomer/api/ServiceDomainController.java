package com.bank.bian.knowyourcustomer.api;

import com.bank.bian.knowyourcustomer.domain.KycAssessment;
import com.bank.bian.knowyourcustomer.domain.KycService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BIAN semantic API for "Know Your Customer" — Phase 2b-c, real domain.
 * Control record: KYC Assessment Procedure.
 *
 * Contract: api/openapi.yaml (owned by this repo).
 */
@RestController
@RequestMapping("/v1")
public class ServiceDomainController {

    static final String CR = "kyc-assessment-procedure";

    private final KycService service;

    public ServiceDomainController(KycService service) {
        this.service = service;
    }

    @GetMapping("/service-domain")
    public Map<String, String> serviceDomain() {
        return Map.of(
                "serviceDomain", "Know Your Customer",
                "businessArea", "Risk and Compliance",
                "businessDomain", "Financial Crime",
                "functionalPattern", "Process",
                "assetType", "KYC Assessment",
                "controlRecord", "KYC Assessment Procedure",
                "version", "0.2.0",
                "phase", "2b-deep"
        );
    }

    // ── the assessment procedure ─────────────────────────────────────────────

    public record CheckRequest(String customerReference, String accountRef, String countryCode,
                               List<String> documents, String callbackUrl) {}

    @PostMapping("/" + CR + "/initiate")
    public ResponseEntity<KycAssessment> initiate(@RequestBody CheckRequest req) {
        KycAssessment assessment = service.assess(req.customerReference(), req.accountRef(),
                req.countryCode(), req.documents(), req.callbackUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(assessment);
    }

    @GetMapping("/" + CR)
    public Collection<KycAssessment> list() {
        return service.list();
    }

    @GetMapping("/" + CR + "/{assessmentId}/retrieve")
    public KycAssessment retrieve(@PathVariable String assessmentId) {
        return service.retrieve(assessmentId);
    }

    /** Analyst decision on a REFERRED case. */
    @PutMapping("/" + CR + "/{assessmentId}/control")
    public KycAssessment control(@PathVariable String assessmentId,
                                 @RequestBody Map<String, String> body) {
        return service.decide(assessmentId, body.get("action"), body.get("reason"));
    }

    // ── watchlist maintenance ────────────────────────────────────────────────

    @PostMapping("/" + CR + "/watchlist")
    public ResponseEntity<Map<String, Object>> addToWatchlist(@RequestBody Map<String, String> body) {
        service.addToWatchlist(body.get("customerReference"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("watchlistSize", service.watchlist().size()));
    }

    @GetMapping("/" + CR + "/watchlist")
    public Set<String> watchlist() {
        return service.watchlist();
    }
}
