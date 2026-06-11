package com.bank.bian.knowyourcustomer.domain;

import com.bank.bian.knowyourcustomer.events.DomainEvent;
import com.bank.bian.knowyourcustomer.events.EventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Business rules for Know Your Customer (Process pattern).
 *
 * Screening pipeline, in precedence order:
 *   1. WATCHLIST_HIT      → REJECTED  (hard fail; not overridable through the API)
 *   2. MISSING_DOCUMENTS  → REFERRED  (required set: bian.kyc.required-documents,
 *                                      default ID + ADDRESS — incomplete isn't
 *                                      rejectable, it's a follow-up)
 *   3. HIGH_RISK_COUNTRY  → REFERRED  (bian.kyc.high-risk-countries; enhanced
 *                                      due diligence is a human decision)
 *   4. otherwise          → APPROVED
 *
 * REFERRED cases are decided by an analyst via control (approve|reject + reason).
 * Every terminal verdict publishes kyc.assessment.completed AND — if the check
 * request carried a callbackUrl — is delivered to the requesting account SD in
 * its kyc-result shape (HTTP today, superseded by the Kafka topic later).
 */
@Service
public class KycService {

    public static final String TOPIC_ASSESSMENT = "bian.kyc.assessment";

    private final AssessmentRepository repository;
    private final EventPublisher events;
    private final KycResultCallback callback;
    private final Set<String> requiredDocuments;
    private final Set<String> highRiskCountries;
    private final Clock clock;

    @Autowired
    public KycService(AssessmentRepository repository, EventPublisher events,
                      KycResultCallback callback,
                      @Value("${bian.kyc.required-documents:ID,ADDRESS}") List<String> requiredDocuments,
                      @Value("${bian.kyc.high-risk-countries:KP,IR}") List<String> highRiskCountries) {
        this(repository, events, callback, requiredDocuments, highRiskCountries, Clock.systemUTC());
    }

    public KycService(AssessmentRepository repository, EventPublisher events,
                      KycResultCallback callback, List<String> requiredDocuments,
                      List<String> highRiskCountries, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.callback = callback;
        this.requiredDocuments = normalize(requiredDocuments);
        this.highRiskCountries = normalize(highRiskCountries);
        this.clock = clock;
    }

    // ── the assessment procedure (Initiate runs it synchronously) ───────────

    public KycAssessment assess(String customerReference, String accountRef, String countryCode,
                                List<String> documents, String callbackUrl) {
        if (customerReference == null || customerReference.isBlank()) {
            throw DomainException.invalid("CUSTOMER_REQUIRED", "customerReference is required");
        }
        List<String> docs = documents == null ? List.of() : documents;
        List<String> reasons = new ArrayList<>();
        KycAssessment.Status outcome;

        if (repository.watchlist().contains(customerReference)) {
            outcome = KycAssessment.Status.REJECTED;
            reasons.add("WATCHLIST_HIT");
        } else {
            Set<String> provided = normalize(docs);
            requiredDocuments.stream()
                    .filter(req -> !provided.contains(req))
                    .forEach(missing -> reasons.add("MISSING_DOCUMENT:" + missing));
            if (countryCode != null
                    && highRiskCountries.contains(countryCode.toUpperCase(Locale.ROOT))) {
                reasons.add("HIGH_RISK_COUNTRY:" + countryCode.toUpperCase(Locale.ROOT));
            }
            outcome = reasons.isEmpty() ? KycAssessment.Status.APPROVED : KycAssessment.Status.REFERRED;
        }

        KycAssessment assessment = KycAssessment.of("KYC-" + UUID.randomUUID(),
                customerReference, accountRef, countryCode, docs, outcome,
                reasons.isEmpty() ? List.of("CLEAN") : reasons, clock.instant());
        assessment.setCallbackUrl(callbackUrl);
        repository.save(assessment);

        if (outcome != KycAssessment.Status.REFERRED) {
            announceVerdict(assessment);
        } else {
            events.publish(DomainEvent.of(TOPIC_ASSESSMENT, "kyc.assessment.referred", Map.of(
                    "assessmentId", assessment.getAssessmentId(),
                    "customerReference", customerReference,
                    "reasons", String.join(",", assessment.getReasons()))));
        }
        return assessment;
    }

    /** Analyst decision on a REFERRED case. */
    public KycAssessment decide(String assessmentId, String action, String reason) {
        KycAssessment assessment = retrieve(assessmentId);
        if (assessment.getStatus() != KycAssessment.Status.REFERRED) {
            throw DomainException.rule("NOT_REFERRED",
                    "manual decision applies to REFERRED cases only (status: " + assessment.getStatus() + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw DomainException.invalid("REASON_REQUIRED",
                    "an analyst decision must carry a reason (audit requirement)");
        }
        KycAssessment.Status next = switch (action == null ? "" : action.toLowerCase()) {
            case "approve" -> KycAssessment.Status.APPROVED;
            case "reject" -> KycAssessment.Status.REJECTED;
            default -> throw DomainException.invalid("UNKNOWN_ACTION", "action must be approve | reject");
        };
        assessment.setStatus(next);
        assessment.setManuallyDecided(true);
        List<String> reasons = new ArrayList<>(assessment.getReasons());
        reasons.add("ANALYST:" + reason);
        assessment.setReasons(reasons);
        assessment.setDecidedAt(clock.instant());
        repository.save(assessment);
        announceVerdict(assessment);
        return assessment;
    }

    private void announceVerdict(KycAssessment a) {
        boolean approved = a.getStatus() == KycAssessment.Status.APPROVED;
        events.publish(DomainEvent.of(TOPIC_ASSESSMENT, "kyc.assessment.completed", Map.of(
                "assessmentId", a.getAssessmentId(),
                "customerReference", a.getCustomerReference(),
                "accountRef", a.getAccountRef() == null ? "" : a.getAccountRef(),
                "outcome", a.getStatus().name(),
                "reasons", String.join(",", a.getReasons()))));
        if (a.getCallbackUrl() != null && !a.getCallbackUrl().isBlank()) {
            boolean delivered = callback.deliver(a.getCallbackUrl(), approved,
                    String.join(",", a.getReasons()));
            events.publish(DomainEvent.of(TOPIC_ASSESSMENT,
                    delivered ? "kyc.callback.delivered" : "kyc.callback.failed",
                    Map.of("assessmentId", a.getAssessmentId(), "callbackUrl", a.getCallbackUrl())));
        }
    }

    // ── watchlist maintenance (pragmatic flagship scope — see README) ────────

    public void addToWatchlist(String customerReference) {
        if (customerReference == null || customerReference.isBlank()) {
            throw DomainException.invalid("CUSTOMER_REQUIRED", "customerReference is required");
        }
        repository.addToWatchlist(customerReference);
    }

    public Set<String> watchlist() {
        return repository.watchlist();
    }

    // ── queries ──────────────────────────────────────────────────────────────

    public KycAssessment retrieve(String assessmentId) {
        return repository.findById(assessmentId)
                .orElseThrow(() -> DomainException.notFound("ASSESSMENT_UNKNOWN",
                        "no assessment " + assessmentId));
    }

    public Collection<KycAssessment> list() {
        return repository.findAll();
    }

    private static Set<String> normalize(List<String> values) {
        return values.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
