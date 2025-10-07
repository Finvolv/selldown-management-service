package com.finvolv.selldown.controller;

import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import com.finvolv.selldown.service.PartnerPayoutDetailsAllService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/lms-files")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PartnerPayoutDetailsAllController {
    
    private static final Logger logger = LoggerFactory.getLogger(PartnerPayoutDetailsAllController.class);
    
    private final PartnerPayoutDetailsAllService partnerPayoutDetailsAllService;
    
    @PostMapping(value = "/year/{year}/month/{month}", 
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Flux<PartnerPayoutDetailsAll> uploadLMSFile(
            @PathVariable Integer year,
            @PathVariable Integer month,
            @RequestBody List<PartnerPayoutDetailsAll> payoutDetails) {
        
        logger.info("Received LMS file upload request - year: {}, month: {}, records: {}", 
            year, month, payoutDetails.size());
        
        return partnerPayoutDetailsAllService.uploadLMSFile(year, month, payoutDetails)
            .doOnNext(saved -> logger.debug("Saved payout detail: {}", saved.getId()))
            .doOnComplete(() -> 
                logger.info("Successfully processed LMS file upload - year: {}, month: {}, records: {}", 
                    year, month, payoutDetails.size()))
            .doOnError(error -> 
                logger.error("Error processing LMS file upload - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    @GetMapping(value = "/year/{year}/month/{month}", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<PartnerPayoutDetailsAll> getPayoutDetailsByYearAndMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        logger.info("Received request to fetch payout details - year: {}, month: {}", year, month);
        
        return partnerPayoutDetailsAllService.getPayoutDetailsByYearAndMonth(year, month)
            .doOnNext(payout -> logger.debug("Retrieved payout detail: {}", payout.getId()))
            .doOnComplete(() -> 
                logger.info("Successfully retrieved payout details - year: {}, month: {}", year, month))
            .doOnError(error -> 
                logger.error("Error retrieving payout details - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    @GetMapping(value = "/lms/{lmsId}", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<PartnerPayoutDetailsAll> getPayoutDetailsByLmsId(@PathVariable Long lmsId) {
        
        logger.info("Received request to fetch payout details - lmsId: {}", lmsId);
        
        return partnerPayoutDetailsAllService.getPayoutDetailsByLmsId(lmsId)
            .doOnNext(payout -> logger.debug("Retrieved payout detail: {}", payout.getId()))
            .doOnComplete(() -> 
                logger.info("Successfully retrieved payout details - lmsId: {}", lmsId))
            .doOnError(error -> 
                logger.error("Error retrieving payout details - lmsId: {}: {}", lmsId, error.getMessage()));
    }
    
    @DeleteMapping(value = "/year/{year}/month/{month}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<Object>> deletePayoutDetailsByYearAndMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        logger.info("Received request to delete payout details - year: {}, month: {}", year, month);
        
        return partnerPayoutDetailsAllService.deletePayoutDetailsByYearAndMonth(year, month)
            .then(Mono.just(ResponseEntity.noContent().build()))
            .doOnSuccess(response -> 
                logger.info("Successfully deleted payout details - year: {}, month: {}", year, month))
            .doOnError(error -> 
                logger.error("Error deleting payout details - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> healthCheck() {
        return Mono.just(ResponseEntity.ok("LMS Files API is healthy"));
    }
}
