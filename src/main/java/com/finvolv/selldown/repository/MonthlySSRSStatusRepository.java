package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.MonthlySSRSStatusEntity;
import com.finvolv.selldown.model.MonthlySSRSStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface MonthlySSRSStatusRepository extends ReactiveCrudRepository<MonthlySSRSStatusEntity, Long> {
    
    Mono<MonthlySSRSStatusEntity> findByYearAndMonth(Integer year, Integer month);

    Flux<MonthlySSRSStatusEntity> findByYearAndMonthAndStatus(Integer year, Integer month, MonthlySSRSStatus status);

    Flux<MonthlySSRSStatusEntity> findByYearAndMonthAndStatusIn(Integer year, Integer month, List<MonthlySSRSStatus> statuses);
    
    Flux<MonthlySSRSStatusEntity> findByStatus(MonthlySSRSStatus status);
}

