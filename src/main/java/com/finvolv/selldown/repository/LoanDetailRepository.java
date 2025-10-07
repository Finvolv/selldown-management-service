package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.LoanDetail;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LoanDetailRepository extends ReactiveCrudRepository<LoanDetail, Long> {
    Flux<LoanDetail> findByPartnerId(Long partnerId);
    Flux<LoanDetail> findByDealIdAndPartnerId(Long dealId, Long partnerId);
    Flux<LoanDetail> findByDealId(Long dealId);
    
    Mono<Void> deleteByPartnerId(Long partnerId);
}
