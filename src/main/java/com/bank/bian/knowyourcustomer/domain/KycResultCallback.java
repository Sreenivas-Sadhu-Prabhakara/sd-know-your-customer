package com.bank.bian.knowyourcustomer.domain;

/**
 * Outbound verdict-delivery port. When a check request carries a callbackUrl
 * (the requesting account SD's kyc-result endpoint), the verdict is delivered
 * there. HTTP adapter today; the Kafka kyc.assessment topic supersedes it.
 *
 * Delivery failure must never lose the assessment — implementations report
 * success/failure and the service records the outcome.
 */
public interface KycResultCallback {

    /** @return true if delivered */
    boolean deliver(String callbackUrl, boolean approved, String reason);
}
