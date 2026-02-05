package com.finvolv.selldown.controller;

import com.finvolv.selldown.dto.OperationFileDataRequest;
import com.finvolv.selldown.model.MonthlyOperationStatusEntity;
import com.finvolv.selldown.model.OperationFileDataEntity;
import com.finvolv.selldown.service.OperationFileService;
import com.finvolv.selldown.service.DocumentUploadService;
import com.finvolv.selldown.service.DocumentDownloadService;
import com.finvolv.selldown.service.OperationExcelExportService;
import com.finvolv.selldown.service.PartnerPayoutDetailsAllService;
import com.finvolv.selldown.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operation-files")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OperationFileController {

    private static final Logger logger = LoggerFactory.getLogger(OperationFileController.class);

    private final OperationFileService operationFileService;
    private final DocumentUploadService documentUploadService;
    private final DocumentDownloadService documentDownloadService;
    private final OperationExcelExportService operationExcelExportService;
    private final PartnerPayoutDetailsAllService partnerPayoutDetailsAllService;
    private final CustomerRepository customerRepository;

    @PostMapping(
        value = "/year/{year}/month/{month}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Flux<OperationFileDataEntity>> uploadOrUpdateOperationFile(
            @PathVariable Integer year,
            @PathVariable Integer month,
            @RequestBody List<Map<String, Object>> requestData) {
        
        logger.info("Received Operation file upload/update request - year: {}, month: {}, records: {}", 
            year, month, requestData.size());
        
        List<OperationFileDataRequest> operationFileDataRequests = requestData.stream()
            .map(data -> {
                String lmsLan = (String) data.get("lmsLan");
                
                Map<String, Object> metadata = data.entrySet().stream()
                    .filter(entry -> !"lmsLan".equals(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                    ));
                
                return OperationFileDataRequest.builder()
                    .lmsLan(lmsLan)
                    .metadata(metadata)
                    .build();
            })
            .toList();
        
        return ResponseEntity.ok(
            operationFileService.uploadOrUpdateOperationFile(year, month, operationFileDataRequests)
                .doOnNext(saved -> logger.debug("Saved Operation file data: {}", saved.getId()))
                .doOnComplete(() -> 
                    logger.info("Successfully processed Operation file upload/update - year: {}, month: {}, records: {}", 
                        year, month, requestData.size()))
                .doOnError(error -> 
                    logger.error("Error processing Operation file upload/update - year: {}, month: {}: {}", 
                        year, month, error.getMessage()))
        );
    }
    
    @GetMapping(
        value = "/year/{year}/month/{month}/status",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Mono<MonthlyOperationStatusEntity>> getOperationStatusByYearAndMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        logger.info("Received request to fetch Operation status - year: {}, month: {}", year, month);
        
        return ResponseEntity.ok(
            operationFileService.getOperationStatusByYearAndMonth(year, month)
                .doOnNext(status -> logger.debug("Retrieved Operation status: {}", status != null ? status.getId() : "NOT_FOUND"))
                .doOnSuccess(status -> 
                    logger.info("Successfully retrieved Operation status - year: {}, month: {}, status: {}", 
                        year, month, status != null ? status.getStatus() : "NOT_FOUND"))
                .doOnError(error -> 
                    logger.error("Error retrieving Operation status - year: {}, month: {}: {}", 
                        year, month, error.getMessage()))
        );
    }
    
    @GetMapping(
        value = "/year/{year}/month/{month}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Flux<OperationFileDataEntity>> getOperationFileDataByYearAndMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        logger.info("Received request to fetch Operation file data - year: {}, month: {}", year, month);
        
        return ResponseEntity.ok(
            operationFileService.getOperationFileDataByYearAndMonth(year, month)
                .doOnComplete(() -> 
                    logger.info("Successfully retrieved Operation file data - year: {}, month: {}", year, month))
                .doOnError(error -> 
                    logger.error("Error retrieving Operation file data - year: {}, month: {}: {}", 
                        year, month, error.getMessage()))
        );
    }

    @PostMapping(value = "/excel-generation-file/deal/{dealId}/partner/{partnerId}/year/{year}/month/{month}")
    public Mono<ResponseEntity<Map<String, Object>>> generateOperationOutputExcel(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "upload", required = false, defaultValue = "true") Boolean upload,
        @PathVariable Long dealId,
        @PathVariable Long partnerId,
        @PathVariable Integer year,
        @PathVariable Integer month,
        @RequestBody List<Map<String, Object>> selectedPayoutFiles
    ) {

        logger.info("Received Operation Output Excel generation request - dealId: {}, partnerId: {}, year: {}, month: {}, selected payouts: {}", 
            dealId, partnerId, year, month, selectedPayoutFiles != null ? selectedPayoutFiles.size() : 0);
        
        if (selectedPayoutFiles == null || selectedPayoutFiles.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "No payout files selected");
            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
        }

        if (authorization == null || authorization.isBlank()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Authorization header is required");
            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
        }

        String token = authorization;
        
        // Use the first selected payout file
        Map<String, Object> selectedPayoutFile = selectedPayoutFiles.get(0);
        String selectedPayoutDocId = (String) selectedPayoutFile.get("documentId");
        String selectedPayoutFolderId = (String) selectedPayoutFile.get("folderId");
        
        logger.info("Selected payout - documentId: {}, folderId: {}", selectedPayoutDocId, selectedPayoutFolderId);
        
        return partnerPayoutDetailsAllService.getDealById(dealId)
            .flatMap(deal -> customerNameFromDeal(deal.getCustomerId())
                .flatMap(partnerName -> {
                    String dealName = sanitizeForFilename(deal.getName());
                    String monthName = getMonthName(month);
                    
                    logger.info("Processing payout file for operation output - documentId: {}, folderId: {}", 
                        selectedPayoutDocId, selectedPayoutFolderId);
                    
                    // Step 1: Download the payout file using DocumentDownloadService
                    return documentDownloadService.downloadPayoutFileToTemp(
                        token, 
                        partnerName, 
                        deal.getName(), 
                        year, 
                        month, 
                        selectedPayoutDocId,
                        selectedPayoutFolderId
                    )
                    .flatMap(downloadedFilePath -> {
                        logger.info("Downloaded payout file to temp location: {}", downloadedFilePath);
                        
                        // Step 2: Process Excel - Add metadata columns from operation_file_data
                        return operationExcelExportService.processOperationExcel(downloadedFilePath, year, month)
                            .flatMap(processedFilePath -> {
                                logger.info("Processed Excel with metadata columns: {}", processedFilePath);
                                
                                // Step 3: Upload with new name (operation-output-*)
                                String operationOutputFilename = String.format("operation-output-%s-%s-%d-%s.xlsx", 
                                    dealName, sanitizeForFilename(partnerName), year, monthName);
                                
                                logger.info("Uploading as operation output file: {}", operationOutputFilename);
                                
                                // Step 4: Upload the file with OPERATION_OUTPUT_SD type
                                return documentUploadService.generateUploadIdForOperation(
                                        authorization, partnerName, deal.getName(), year, month)
                                    .flatMap(generatedId -> {
                                        logger.info("Generated upload ID for operation output: {}", generatedId);
                                        return documentUploadService.uploadExcelFromFile(
                                            authorization, 
                                            generatedId, 
                                            processedFilePath, 
                                            operationOutputFilename
                                        )
                                        .then(Mono.fromCallable(() -> {
                                            Map<String, Object> successResponse = new HashMap<>();
                                            successResponse.put("success", true);
                                            successResponse.put("generatedId", generatedId);
                                            successResponse.put("filename", operationOutputFilename);
                                            successResponse.put("message", "Operation output file uploaded successfully");
                                            return ResponseEntity.ok(successResponse);
                                        }))
                                        .doFinally(signal -> {
                                            // Cleanup temp file
                                            documentDownloadService.cleanupTempFile(processedFilePath);
                                            logger.info("Cleaned up temp file: {}", processedFilePath);
                                        });
                                    });
                            });
                    });
                }))
            .doOnSuccess(response -> logger.info("Operation output excel generation completed successfully"))
            .doOnError(error -> logger.error("Error generating Operation Output Excel - dealId: {}, partnerId: {}, year: {}, month: {}: {}", 
                dealId, partnerId, year, month, error.getMessage(), error));
    }

    private Mono<String> customerNameFromDeal(Long customerId) {
        return customerRepository.findById(customerId)
            .map(c -> c.getName());
    }

    private String getMonthName(int month) {
        return Month.of(month).name().substring(0, 3);
    }

    private String sanitizeForFilename(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
    }
}