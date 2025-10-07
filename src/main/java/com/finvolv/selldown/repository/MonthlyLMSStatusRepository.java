package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.MonthlyLMSStatusEntity;
import com.finvolv.selldown.model.MonthlyLMSStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface MonthlyLMSStatusRepository extends ReactiveCrudRepository<MonthlyLMSStatusEntity, Long> {
    
    Mono<MonthlyLMSStatusEntity> findByYearAndMonth(Integer year, Integer month);

    Flux<MonthlyLMSStatusEntity> findByYearAndMonthAndStatus(Integer year, Integer month, MonthlyLMSStatus status);

    Flux<MonthlyLMSStatusEntity> findByYearAndMonthAndStatusIn(Integer year, Integer month, List<MonthlyLMSStatus> statuses);
    
    Flux<MonthlyLMSStatusEntity> findByStatus(MonthlyLMSStatus status);
}
