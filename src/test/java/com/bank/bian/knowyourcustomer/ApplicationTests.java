package com.bank.bian.knowyourcustomer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot + API smoke: screening outcomes through HTTP (watchlist seeded via config). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "bian.kyc.watchlist=C-API-SANCTIONED")
class ApplicationTests {

    static final String CR = "/v1/kyc-assessment-procedure";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void cleanCustomerApprovesThroughTheApi() {
        var r = rest.postForEntity(url(CR + "/initiate"),
                Map.of("customerReference", "C-API-OK", "countryCode", "IN",
                        "documents", List.of("ID", "ADDRESS")),
                Map.class);
        assertThat(r.getStatusCode().value()).isEqualTo(201);
        assertThat(r.getBody().get("status")).isEqualTo("APPROVED");
    }

    @Test
    void watchlistedCustomerRejected_referredCaseDecidedByAnalyst() {
        var rejected = rest.postForEntity(url(CR + "/initiate"),
                Map.of("customerReference", "C-API-SANCTIONED",
                        "documents", List.of("ID", "ADDRESS")),
                Map.class);
        assertThat(rejected.getBody().get("status")).isEqualTo("REJECTED");

        var referred = rest.postForEntity(url(CR + "/initiate"),
                Map.of("customerReference", "C-API-EDD", "countryCode", "IR",
                        "documents", List.of("ID", "ADDRESS")),
                Map.class);
        assertThat(referred.getBody().get("status")).isEqualTo("REFERRED");
        String id = (String) referred.getBody().get("assessmentId");

        var decided = rest.exchange(url(CR + "/" + id + "/control"),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("action", "approve", "reason", "EDD ok")),
                Map.class);
        assertThat(decided.getBody().get("status")).isEqualTo("APPROVED");
        assertThat((Boolean) decided.getBody().get("manuallyDecided")).isTrue();
    }
}
