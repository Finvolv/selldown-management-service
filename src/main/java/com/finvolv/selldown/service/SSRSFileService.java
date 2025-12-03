package com.finvolv.selldown.service;

import com.finvolv.selldown.dto.SSRSFileDataRequest;
import com.finvolv.selldown.model.MonthlySSRSStatus;
import com.finvolv.selldown.model.MonthlySSRSStatusEntity;
import com.finvolv.selldown.model.SSRSFileDataEntity;
import com.finvolv.selldown.repository.MonthlySSRSStatusRepository;
import com.finvolv.selldown.repository.SSRSFileDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SSRSFileService {

    private final MonthlySSRSStatusRepository monthlySSRSStatusRepository;
    private final SSRSFileDataRepository ssrsFileDataRepository;

    @Transactional
    public Flux<SSRSFileDataEntity> uploadOrUpdateSSRSFile(Integer year, Integer month, List<SSRSFileDataRequest> ssrsFileDataRequests) {
        log.info("Uploading/updating SSRS file - year: {}, month: {}, number of records: {}", 
            year, month, ssrsFileDataRequests.size());
        
        return createOrUpdateSSRSStatus(year, month)
            .flatMapMany(ssrsStatusId -> {
                log.debug("Created/Updated SSRS status with ID: {}", ssrsStatusId);
                
                // Delete existing data for this month if updating
                return ssrsFileDataRepository.deleteByMonthlySsrsId(ssrsStatusId)
                    .thenMany(
                        // Prepare all SSRS file data with SSRS ID and timestamps
                        Flux.fromIterable(ssrsFileDataRequests)
                            .map(request -> {
                                // Extract metadata (everything except lmsLan)
                                Map<String, Object> metadata = request.getMetadata();
                                
                                return SSRSFileDataEntity.builder()
                                    .monthlySsrsId(ssrsStatusId)
                                    .lmsLan(request.getLmsLan())
                                    .metadata(metadata)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .build();
                            })
                            .collectList()
                            .flatMapMany(preparedData -> {
                                // Save all SSRS file data
                                return ssrsFileDataRepository.saveAll(preparedData)
                                    .doOnComplete(() -> 
                                        log.info("Successfully uploaded/updated SSRS file - year: {}, month: {}, records: {}", 
                                            year, month, ssrsFileDataRequests.size()))
                                    .doOnError(error -> 
                                        log.error("Error uploading/updating SSRS file - year: {}, month: {}: {}", 
                                            year, month, error.getMessage()));
                            })
                    );
            });
    }
    
    @Transactional
    private Mono<Long> createOrUpdateSSRSStatus(Integer year, Integer month) {
        log.debug("Creating or updating SSRS status - year: {}, month: {}", year, month);
        
        return monthlySSRSStatusRepository.findByYearAndMonth(year, month)
            .switchIfEmpty(
                // Create new SSRS status if not exists
                createNewSSRSStatus(year, month)
            )
            .flatMap(existingStatus -> {
                // Update existing status
                existingStatus.setStatus(MonthlySSRSStatus.SSRS_UPLOADED);
                existingStatus.setModifiedAt(LocalDateTime.now());
                return monthlySSRSStatusRepository.save(existingStatus);
            })
            .map(MonthlySSRSStatusEntity::getId)
            .doOnNext(id -> log.debug("Using SSRS status ID: {} for year: {}, month: {}", id, year, month))
            .doOnError(error -> 
                log.error("Error creating/updating SSRS status - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    private Mono<MonthlySSRSStatusEntity> createNewSSRSStatus(Integer year, Integer month) {
        log.debug("Creating new SSRS status - year: {}, month: {}", year, month);
        
        MonthlySSRSStatusEntity ssrsStatus = MonthlySSRSStatusEntity.builder()
            .year(year)
            .month(month)
            .status(MonthlySSRSStatus.SSRS_INITIALIZED)
            .cycleStartDate(LocalDate.of(year, month, 1))
            .cycleEndDate(LocalDate.of(year, month, 1).withDayOfMonth(
                LocalDate.of(year, month, 1).lengthOfMonth()))
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
            
        return monthlySSRSStatusRepository.save(ssrsStatus)
            .doOnNext(saved -> log.debug("Created new SSRS status with ID: {}", saved.getId()))
            .doOnError(error -> 
                log.error("Error creating new SSRS status - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    public Mono<MonthlySSRSStatusEntity> getSSRSStatusByYearAndMonth(Integer year, Integer month) {
        return monthlySSRSStatusRepository.findByYearAndMonth(year, month);
    }
    
    public Flux<SSRSFileDataEntity> getSSRSFileDataByYearAndMonth(Integer year, Integer month) {
        return monthlySSRSStatusRepository.findByYearAndMonth(year, month)
            .flatMapMany(status -> ssrsFileDataRepository.findByMonthlySsrsId(status.getId()));
    }
}

