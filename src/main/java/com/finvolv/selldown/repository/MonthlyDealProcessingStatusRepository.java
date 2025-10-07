package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.MonthlyDealProcessingStatus;
import com.finvolv.selldown.model.MonthlyDealStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface MonthlyDealProcessingStatusRepository extends ReactiveCrudRepository<MonthlyDealProcessingStatus, Long> {
    
    Mono<MonthlyDealProcessingStatus> findByDealIdAndPartnerIdAndYearAndMonth(
        Long dealId, 
        Long partnerId, 
        Integer year, 
        Integer month
    );

    Flux<MonthlyDealProcessingStatus> findByYearAndMonth(Integer year, Integer month);

    Flux<MonthlyDealProcessingStatus> findByPartnerIdAndYearAndMonth(Long partnerId, Integer year, Integer month);

    Flux<MonthlyDealProcessingStatus> findByPartnerIdAndYearAndMonthAndStatus(Long partnerId, Integer year, Integer month, MonthlyDealStatus status);

    Flux<MonthlyDealProcessingStatus> findByPartnerIdAndYearAndMonthAndStatusIn(Long partnerId, Integer year, Integer month, List<MonthlyDealStatus> statuses);
}
