package com.finvolv.selldown.repository;

import com.finvolv.selldown.model.SSRSFileDataEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SSRSFileDataRepository extends ReactiveCrudRepository<SSRSFileDataEntity, Long> {
    
    Flux<SSRSFileDataEntity> findByMonthlySsrsId(Long monthlySsrsId);
    
    Mono<SSRSFileDataEntity> findByMonthlySsrsIdAndLmsLan(Long monthlySsrsId, String lmsLan);
    
    @Query("DELETE FROM \"sd-ssrs_file_data\" WHERE monthly_ssrs_id = :monthlySsrsId")
    Mono<Void> deleteByMonthlySsrsId(Long monthlySsrsId);
}

