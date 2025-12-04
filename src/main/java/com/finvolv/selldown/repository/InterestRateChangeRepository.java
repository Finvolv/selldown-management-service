package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.InterestRateChange;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface InterestRateChangeRepository extends ReactiveCrudRepository<InterestRateChange, Long> {
    @Query("SELECT * FROM \"sd-interest_rate_change\" WHERE deal_id = :dealId ORDER BY start_date ASC, id ASC")
    Flux<InterestRateChange> findByDealId(Long dealId);
    
    @Query("SELECT * FROM \"sd-interest_rate_change\" WHERE deal_id = :dealId ORDER BY start_date DESC LIMIT 1")
    Mono<InterestRateChange> findTopByDealIdOrderByStartDateDesc(Long dealId);
}


