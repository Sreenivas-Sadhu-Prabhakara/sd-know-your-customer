package com.bank.bian.knowyourcustomer.infrastructure;

import com.bank.bian.knowyourcustomer.domain.AssessmentRepository;
import com.bank.bian.knowyourcustomer.domain.KycAssessment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 2 adapter. Watchlist seeds from config; runtime additions via the API. */
@Repository
public class InMemoryAssessmentRepository implements AssessmentRepository {

    private final Map<String, KycAssessment> assessments = new ConcurrentHashMap<>();
    private final Set<String> watchlist = ConcurrentHashMap.newKeySet();

    public InMemoryAssessmentRepository(
            @Value("${bian.kyc.watchlist:}") List<String> seedWatchlist) {
        seedWatchlist.stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(watchlist::add);
    }

    @Override
    public void save(KycAssessment assessment) {
        assessments.put(assessment.getAssessmentId(), assessment);
    }

    @Override
    public Optional<KycAssessment> findById(String assessmentId) {
        return Optional.ofNullable(assessments.get(assessmentId));
    }

    @Override
    public Collection<KycAssessment> findAll() {
        return assessments.values();
    }

    @Override
    public Set<String> watchlist() {
        return Set.copyOf(watchlist);
    }

    @Override
    public void addToWatchlist(String customerReference) {
        watchlist.add(customerReference);
    }
}
