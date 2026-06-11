package com.bank.bian.knowyourcustomer.infrastructure;

import com.bank.bian.knowyourcustomer.domain.KycResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Delivers the verdict with the exact shape the account SDs expect on
 * PUT .../{accountId}/kyc-result : {"approved": bool, "reason": "..."}.
 * In-cluster the callbackUrl is a service DNS name, e.g.
 * http://sd-current-account.bian-operations:8080/v1/current-account-facility-fulfillment-arrangement/CA-…/kyc-result
 */
@Component
public class HttpKycResultCallback implements KycResultCallback {

    private static final Logger log = LoggerFactory.getLogger(HttpKycResultCallback.class);

    private final RestClient rest;

    public HttpKycResultCallback(RestClient.Builder builder) {
        this.rest = builder.build();
    }

    @Override
    public boolean deliver(String callbackUrl, boolean approved, String reason) {
        try {
            rest.put()
                    .uri(callbackUrl)
                    .header("Content-Type", "application/json")
                    .body(Map.of("approved", approved, "reason", reason == null ? "" : reason))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            // Never fail the assessment because the consumer is down — the verdict
            // is persisted here and re-deliverable (Kafka makes this moot later).
            log.warn("kyc-result callback to {} failed: {}", callbackUrl, e.getMessage());
            return false;
        }
    }
}
