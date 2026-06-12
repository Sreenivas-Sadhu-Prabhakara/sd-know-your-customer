package com.bank.bian.knowyourcustomer.events;

import com.bank.bian.knowyourcustomer.domain.KycService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 2d-ii (profile `kafka`): consume kyc.check.requested from the account
 * domains. The event carries no documents (those belong to onboarding), so
 * event-driven checks assess with bian.kyc.event-documents (default: none →
 * REFERRED to an analyst). The richer path is the accounts' HTTP KycGateway,
 * which carries documents and a callback URL; this consumer is the
 * backbone-native alternative. Verdicts flow out on bian.kyc.assessment.
 */
@Component
@Profile("kafka")
public class CheckRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger("bian.kyc-feed");

    private final KycService service;
    private final List<String> eventDocuments;
    private final ObjectMapper mapper = new ObjectMapper();

    public CheckRequestConsumer(KycService service,
                                @Value("${bian.kyc.event-documents:}") List<String> eventDocuments) {
        this.service = service;
        this.eventDocuments = eventDocuments;
    }

    @KafkaListener(topics = "bian.kyc.check", groupId = "sd-know-your-customer")
    public void onCheckEvent(String message) {
        handle(message);
    }

    /** package-visible for direct unit testing */
    void handle(String message) {
        try {
            JsonNode e = mapper.readTree(message);
            if (!"kyc.check.requested".equals(e.path("type").asText())) {
                return;
            }
            JsonNode p = e.path("payload");
            service.assess(
                    p.path("customerReference").asText(),
                    p.path("accountId").asText(null),
                    null,
                    eventDocuments,
                    null);
        } catch (Exception ex) {
            log.warn("skipping malformed check request: {}", ex.getMessage());
        }
    }
}
