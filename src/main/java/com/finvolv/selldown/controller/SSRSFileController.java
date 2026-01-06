package com.finvolv.selldown.controller;

import com.finvolv.selldown.dto.SSRSFileDataRequest;
import com.finvolv.selldown.model.MonthlySSRSStatusEntity;
import com.finvolv.selldown.model.SSRSFileDataEntity;
import com.finvolv.selldown.service.SSRSFileService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ssrs-files")
@RequiredArgsConstructor
public class SSRSFileController {

    private static final Logger logger = LoggerFactory.getLogger(SSRSFileController.class);

    private final SSRSFileService ssrsFileService;

    @PostMapping(
        value = "/year/{year}/month/{month}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Flux<SSRSFileDataEntity>> uploadOrUpdateSSRSFile(
            @PathVariable Integer year,
            @PathVariable Integer month,
            @RequestBody List<Map<String, Object>> requestData) {
        
        logger.info("Received SSRS file upload/update request - year: {}, month: {}, records: {}", 
            year, month, requestData.size());
        
        // Convert request data to SSRSFileDataRequest objects
        List<SSRSFileDataRequest> ssrsFileDataRequests = requestData.stream()
            .map(data -> {
                String lmsLan = (String) data.get("lmsLan");
                
                // Create metadata map with all fields except lmsLan
                Map<String, Object> metadata = data.entrySet().stream()
                    .filter(entry -> !"lmsLan".equals(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                    ));
                
                return SSRSFileDataRequest.builder()
                    .lmsLan(lmsLan)
                    .metadata(metadata)
                    .build();
            })
            .toList();
        
        return ResponseEntity.ok(
            ssrsFileService.uploadOrUpdateSSRSFile(year, month, ssrsFileDataRequests)
                .doOnNext(saved -> logger.debug("Saved SSRS file data: {}", saved.getId()))
                .doOnComplete(() -> 
                    logger.info("Successfully processed SSRS file upload/update - year: {}, month: {}, records: {}", 
                        year, month, requestData.size()))
                .doOnError(error -> 
                    logger.error("Error processing SSRS file upload/update - year: {}, month: {}: {}", 
                        year, month, error.getMessage()))
        );
    }
    
    @GetMapping(
        value = "/year/{year}/month/{month}/status",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Mono<MonthlySSRSStatusEntity>> getSSRSStatusByYearAndMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        logger.info("Received request to fetch SSRS status - year: {}, month: {}", year, month);
        
        return ResponseEntity.ok(
            ssrsFileService.getSSRSStatusByYearAndMonth(year, month)
                .doOnNext(status -> logger.debug("Retrieved SSRS status: {}", status != null ? status.getId() : "NOT_FOUND"))
                .doOnSuccess(status -> 
                    logger.info("Successfully retrieved SSRS status - year: {}, month: {}, status: {}", 
                        year, month, status != null ? status.getStatus() : "NOT_FOUND"))
                .doOnError(error -> 
                    logger.error("Error retrieving SSRS status - year: {}, month: {}: {}", 
                        year, month, error.getMessage()))
        );
    }
    
    @GetMapping(
        value = "/year/{year}/month/{month}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Flux<SSRSFileDataEntity>> getSSRSFileDataByYearAndMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        logger.info("Received request to fetch SSRS file data - year: {}, month: {}", year, month);
        
        return ResponseEntity.ok(
            ssrsFileService.getSSRSFileDataByYearAndMonth(year, month)
                .doOnComplete(() -> 
                    logger.info("Successfully retrieved SSRS file data - year: {}, month: {}", year, month))
                .doOnError(error -> 
                    logger.error("Error retrieving SSRS file data - year: {}, month: {}: {}", 
                        year, month, error.getMessage()))
        );
    }
}

