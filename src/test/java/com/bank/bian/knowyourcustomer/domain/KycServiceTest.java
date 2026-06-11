package com.bank.bian.knowyourcustomer.domain;

import com.bank.bian.knowyourcustomer.events.DomainEvent;
import com.bank.bian.knowyourcustomer.events.EventPublisher;
import com.bank.bian.knowyourcustomer.infrastructure.InMemoryAssessmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The screening pipeline: watchlist, documents, jurisdiction, analyst flow. */
class KycServiceTest {

    static class RecordingPublisher implements EventPublisher {
        final List<DomainEvent> events = new ArrayList<>();
        @Override public void publish(DomainEvent event) { events.add(event); }
        List<String> types() { return events.stream().map(DomainEvent::type).toList(); }
    }

    static class RecordingCallback implements KycResultCallback {
        record Delivery(String url, boolean approved, String reason) {}
        final List<Delivery> deliveries = new ArrayList<>();
        boolean failNext = false;
        @Override public boolean deliver(String url, boolean approved, String reason) {
            if (failNext) { return false; }
            deliveries.add(new Delivery(url, approved, reason));
            return true;
        }
    }

    RecordingPublisher events;
    RecordingCallback callback;
    KycService service;

    @BeforeEach
    void setUp() {
        events = new RecordingPublisher();
        callback = new RecordingCallback();
        InMemoryAssessmentRepository repo = new InMemoryAssessmentRepository(List.of("C-SANCTIONED"));
        service = new KycService(repo, events, callback,
                List.of("ID", "ADDRESS"), List.of("KP", "IR"), Clock.systemUTC());
    }

    @Nested
    class Screening {
        @Test
        void cleanCustomerWithFullDocumentsApproves() {
            KycAssessment a = service.assess("C-OK", "CA-1", "IN", List.of("ID", "ADDRESS"), null);
            assertThat(a.getStatus()).isEqualTo(KycAssessment.Status.APPROVED);
            assertThat(a.getReasons()).containsExactly("CLEAN");
            assertThat(events.types()).containsExactly("kyc.assessment.completed");
        }

        @Test
        void watchlistHitRejectsRegardlessOfEverythingElse() {
            KycAssessment a = service.assess("C-SANCTIONED", "CA-2", "IN", List.of("ID", "ADDRESS"), null);
            assertThat(a.getStatus()).isEqualTo(KycAssessment.Status.REJECTED);
            assertThat(a.getReasons()).containsExactly("WATCHLIST_HIT");
        }

        @Test
        void missingDocumentRefersNotRejects() {
            KycAssessment a = service.assess("C-NEW", "CA-3", "IN", List.of("ID"), null);
            assertThat(a.getStatus()).isEqualTo(KycAssessment.Status.REFERRED);
            assertThat(a.getReasons()).containsExactly("MISSING_DOCUMENT:ADDRESS");
            assertThat(events.types()).containsExactly("kyc.assessment.referred");
        }

        @Test
        void highRiskCountryRefersForEnhancedDueDiligence() {
            KycAssessment a = service.assess("C-DPRK", "CA-4", "kp", List.of("ID", "ADDRESS"), null);
            assertThat(a.getStatus()).isEqualTo(KycAssessment.Status.REFERRED);
            assertThat(a.getReasons()).containsExactly("HIGH_RISK_COUNTRY:KP");
        }

        @Test
        void runtimeWatchlistAdditionTakesEffect() {
            service.addToWatchlist("C-NEWLY-LISTED");
            KycAssessment a = service.assess("C-NEWLY-LISTED", null, "IN", List.of("ID", "ADDRESS"), null);
            assertThat(a.getStatus()).isEqualTo(KycAssessment.Status.REJECTED);
        }
    }

    @Nested
    class AnalystDecisions {
        @Test
        void referredCaseCanBeApprovedWithReason() {
            KycAssessment a = service.assess("C-R1", "CA-5", "IR", List.of("ID", "ADDRESS"), null);
            KycAssessment decided = service.decide(a.getAssessmentId(), "approve", "EDD completed, source of funds verified");
            assertThat(decided.getStatus()).isEqualTo(KycAssessment.Status.APPROVED);
            assertThat(decided.isManuallyDecided()).isTrue();
            assertThat(decided.getReasons()).anyMatch(r -> r.startsWith("ANALYST:"));
            assertThat(events.types()).containsExactly("kyc.assessment.referred", "kyc.assessment.completed");
        }

        @Test
        void decisionWithoutReasonRejected_auditRequirement() {
            KycAssessment a = service.assess("C-R2", null, "IR", List.of("ID", "ADDRESS"), null);
            assertThatThrownBy(() -> service.decide(a.getAssessmentId(), "approve", " "))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("audit");
        }

        @Test
        void onlyReferredCasesAcceptManualDecisions() {
            KycAssessment approved = service.assess("C-R3", null, "IN", List.of("ID", "ADDRESS"), null);
            assertThatThrownBy(() -> service.decide(approved.getAssessmentId(), "reject", "nope"))
                    .hasMessageContaining("REFERRED");
        }
    }

    @Nested
    class Callbacks {
        @Test
        void verdictDeliveredToCallbackUrlInAccountKycResultShape() {
            service.assess("C-CB", "CA-9", "IN", List.of("ID", "ADDRESS"),
                    "http://sd-current-account.bian-operations:8080/v1/.../CA-9/kyc-result");
            assertThat(callback.deliveries).hasSize(1);
            assertThat(callback.deliveries.get(0).approved()).isTrue();
            assertThat(events.types()).contains("kyc.callback.delivered");
        }

        @Test
        void callbackFailureRecordedButAssessmentSurvives() {
            callback.failNext = true;
            KycAssessment a = service.assess("C-CB2", "CA-10", "IN", List.of("ID", "ADDRESS"),
                    "http://unreachable.example/kyc-result");
            assertThat(a.getStatus()).isEqualTo(KycAssessment.Status.APPROVED); // verdict persisted
            assertThat(events.types()).contains("kyc.callback.failed");
        }

        @Test
        void referredCaseCallsBackOnlyAfterAnalystDecision() {
            KycAssessment a = service.assess("C-CB3", "CA-11", "IR", List.of("ID", "ADDRESS"),
                    "http://callback.example/kyc-result");
            assertThat(callback.deliveries).isEmpty();   // not yet decided
            service.decide(a.getAssessmentId(), "reject", "EDD failed");
            assertThat(callback.deliveries).hasSize(1);
            assertThat(callback.deliveries.get(0).approved()).isFalse();
        }
    }
}
