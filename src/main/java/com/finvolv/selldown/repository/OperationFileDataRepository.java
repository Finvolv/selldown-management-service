package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.OperationFileDataEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface OperationFileDataRepository extends ReactiveCrudRepository<OperationFileDataEntity, Long> {

    Flux<OperationFileDataEntity> findByMonthlyOperationId(Long monthlyOperationId);

    Mono<OperationFileDataEntity> findByMonthlyOperationIdAndLmsLan(Long monthlyOperationId, String lmsLan);

    @Modifying
    @Query("DELETE FROM \"sd-operation_file_data\" WHERE monthly_operation_id = :monthlyOperationId")
    Mono<Void> deleteByMonthlyOperationId(Long monthlyOperationId);
}