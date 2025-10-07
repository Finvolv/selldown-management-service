package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.Deal;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DealRepository extends ReactiveCrudRepository<Deal, Long> {
    Flux<Deal> findByCustomerId(Long customerId);
}


