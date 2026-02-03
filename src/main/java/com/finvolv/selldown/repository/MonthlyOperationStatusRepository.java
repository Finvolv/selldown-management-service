package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.MonthlyOperationStatusEntity;
import com.finvolv.selldown.model.MonthlyOperationStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface MonthlyOperationStatusRepository extends ReactiveCrudRepository<MonthlyOperationStatusEntity, Long> {

    Mono<MonthlyOperationStatusEntity> findByYearAndMonth(Integer year, Integer month);

    Flux<MonthlyOperationStatusEntity> findByYearAndMonthAndStatus(Integer year, Integer month, MonthlyOperationStatus status);

    Flux<MonthlyOperationStatusEntity> findByYearAndMonthAndStatusIn(Integer year, Integer month, List<MonthlyOperationStatus> statuses);

    Flux<MonthlyOperationStatusEntity> findByStatus(MonthlyOperationStatus status);
}
