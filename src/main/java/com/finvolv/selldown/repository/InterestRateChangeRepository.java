package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.InterestRateChange;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface InterestRateChangeRepository extends ReactiveCrudRepository<InterestRateChange, Long> {
    Flux<InterestRateChange> findByDealId(Long dealId);
}


