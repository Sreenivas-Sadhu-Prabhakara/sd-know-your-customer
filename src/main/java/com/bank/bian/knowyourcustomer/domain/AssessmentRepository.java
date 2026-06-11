package com.bank.bian.knowyourcustomer.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Persistence port — in-memory now, Postgres when the platform hydrates. */
public interface AssessmentRepository {

    void save(KycAssessment assessment);

    Optional<KycAssessment> findById(String assessmentId);

    Collection<KycAssessment> findAll();

    /** The sanctions/PEP watchlist (customer references). */
    Set<String> watchlist();

    void addToWatchlist(String customerReference);
}
