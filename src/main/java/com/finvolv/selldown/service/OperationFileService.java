package com.finvolv.selldown.service;

import com.finvolv.selldown.dto.OperationFileDataRequest;
import com.finvolv.selldown.model.MonthlyOperationStatus;
import com.finvolv.selldown.model.MonthlyOperationStatusEntity;
import com.finvolv.selldown.model.OperationFileDataEntity;
import com.finvolv.selldown.repository.MonthlyOperationStatusRepository;
import com.finvolv.selldown.repository.OperationFileDataRepository;
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
public class OperationFileService {

    private final MonthlyOperationStatusRepository monthlyOperationStatusRepository;
    private final OperationFileDataRepository operationFileDataRepository;

    @Transactional
    public Flux<OperationFileDataEntity> uploadOrUpdateOperationFile(Integer year, Integer month, List<OperationFileDataRequest> operationFileDataRequests) {
        log.info("Uploading/updating Operation file - year: {}, month: {}, number of records: {}", 
            year, month, operationFileDataRequests.size());
        
        return createOrUpdateOperationStatus(year, month)
            .flatMapMany(operationStatusId -> {
                log.debug("Created/Updated Operation status with ID: {}", operationStatusId);
                
                // Delete existing data for this month if updating
                return operationFileDataRepository.deleteByMonthlyOperationId(operationStatusId)
                    .thenMany(
                        // Prepare all Operation file data with Operation ID and timestamps
                        Flux.fromIterable(operationFileDataRequests)
                            .map(request -> {
                                // Extract metadata (everything except lmsLan)
                                Map<String, Object> metadata = request.getMetadata();
                                
                                return OperationFileDataEntity.builder()
                                    .monthlyOperationId(operationStatusId)
                                    .lmsLan(request.getLmsLan())
                                    .metadata(metadata)
                                    .createdAt(LocalDateTime.now())
                                    .modifiedAt(LocalDateTime.now())
                                    .build();
                            })
                            .collectList()
                            .flatMapMany(preparedData -> {
                                // Save all Operation file data
                                return operationFileDataRepository.saveAll(preparedData)
                                    .doOnComplete(() -> 
                                        log.info("Successfully uploaded/updated Operation file - year: {}, month: {}, records: {}", 
                                            year, month, operationFileDataRequests.size()))
                                    .doOnError(error -> 
                                        log.error("Error uploading/updating Operation file - year: {}, month: {}: {}", 
                                            year, month, error.getMessage()));
                            })
                    );
            });
    }
    
    @Transactional
    private Mono<Long> createOrUpdateOperationStatus(Integer year, Integer month) {
        log.debug("Creating or updating Operation status - year: {}, month: {}", year, month);
        
        return monthlyOperationStatusRepository.findByYearAndMonth(year, month)
            .switchIfEmpty(
                // Create new Operation status if not exists
                createNewOperationStatus(year, month)
            )
            .flatMap(existingStatus -> {
                // Update existing status
                existingStatus.setStatus(MonthlyOperationStatus.OPERATION_UPLOADED);
                existingStatus.setModifiedAt(LocalDateTime.now());
                return monthlyOperationStatusRepository.save(existingStatus);
            })
            .map(MonthlyOperationStatusEntity::getId)
            .doOnNext(id -> log.debug("Using Operation status ID: {} for year: {}, month: {}", id, year, month))
            .doOnError(error -> 
                log.error("Error creating/updating Operation status - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    private Mono<MonthlyOperationStatusEntity> createNewOperationStatus(Integer year, Integer month) {
        log.debug("Creating new Operation status - year: {}, month: {}", year, month);
        
        MonthlyOperationStatusEntity operationStatus = MonthlyOperationStatusEntity.builder()
            .year(year)
            .month(month)
            .status(MonthlyOperationStatus.OPERATION_INITIALIZED)
            .cycleStartDate(LocalDate.of(year, month, 1))
            .cycleEndDate(LocalDate.of(year, month, 1).withDayOfMonth(
                LocalDate.of(year, month, 1).lengthOfMonth()))
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
            
        return monthlyOperationStatusRepository.save(operationStatus)
            .doOnNext(saved -> log.debug("Created new Operation status with ID: {}", saved.getId()))
            .doOnError(error -> 
                log.error("Error creating new Operation status - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    public Mono<MonthlyOperationStatusEntity> getOperationStatusByYearAndMonth(Integer year, Integer month) {
        return monthlyOperationStatusRepository.findByYearAndMonth(year, month);
    }
    
    public Flux<OperationFileDataEntity> getOperationFileDataByYearAndMonth(Integer year, Integer month) {
        return monthlyOperationStatusRepository.findByYearAndMonth(year, month)
            .flatMapMany(status -> operationFileDataRepository.findByMonthlyOperationId(status.getId()));
    }
}