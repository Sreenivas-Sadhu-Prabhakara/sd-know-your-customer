package com.bank.bian.knowyourcustomer.domain;

import java.time.Instant;
import java.util.List;

/**
 * Control record made real: "KYC Assessment Procedure".
 *
 * Outcomes:
 *   APPROVED  — screening passed automatically
 *   REJECTED  — hard fail (watchlist hit); terminal, never overridable via API
 *   REFERRED  — needs a human: missing documents or high-risk jurisdiction;
 *               an analyst decides via the control endpoint
 */
public class KycAssessment {

    public enum Status { APPROVED, REJECTED, REFERRED }

    private String assessmentId;
    private String customerReference;
    private String accountRef;          // the account whose opening triggered the check
    private String countryCode;
    private List<String> documents;
    private Status status;
    private List<String> reasons;       // why this outcome
    private boolean manuallyDecided;
    private String callbackUrl;         // optional: where to deliver the verdict (account SD kyc-result)
    private Instant assessedAt;
    private Instant decidedAt;          // when a REFERRED case was manually decided

    public static KycAssessment of(String assessmentId, String customerReference, String accountRef,
                                   String countryCode, List<String> documents,
                                   Status status, List<String> reasons, Instant now) {
        KycAssessment a = new KycAssessment();
        a.assessmentId = assessmentId;
        a.customerReference = customerReference;
        a.accountRef = accountRef;
        a.countryCode = countryCode;
        a.documents = List.copyOf(documents);
        a.status = status;
        a.reasons = List.copyOf(reasons);
        a.assessedAt = now;
        return a;
    }

    public String getAssessmentId() { return assessmentId; }
    public String getCustomerReference() { return customerReference; }
    public String getAccountRef() { return accountRef; }
    public String getCountryCode() { return countryCode; }
    public List<String> getDocuments() { return documents; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = List.copyOf(reasons); }
    public boolean isManuallyDecided() { return manuallyDecided; }
    public void setManuallyDecided(boolean manuallyDecided) { this.manuallyDecided = manuallyDecided; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public Instant getAssessedAt() { return assessedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
