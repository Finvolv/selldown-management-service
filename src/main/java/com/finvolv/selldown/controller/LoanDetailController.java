
package com.finvolv.selldown.controller;

import com.finvolv.selldown.dto.ExcelGenerationResponse;
import com.finvolv.selldown.dto.OpeningPosDiscrepancy;
import com.finvolv.selldown.model.LoanDetail;
import com.finvolv.selldown.model.MonthlyDealProcessingStatus;
import com.finvolv.selldown.model.MonthlyDealStatus;
import com.finvolv.selldown.model.MonthlyLMSStatusEntity;
import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import com.finvolv.selldown.model.SSRSFileDataEntity;
import com.finvolv.selldown.repository.LoanDetailRepository;
import com.finvolv.selldown.repository.MonthlyDealProcessingStatusRepository;
import com.finvolv.selldown.repository.MonthlyLMSStatusRepository;
import com.finvolv.selldown.repository.PartnerPayoutDetailsAllRepository;
import com.finvolv.selldown.service.PartnerPayoutDetailsAllService;
import com.finvolv.selldown.service.ExcelExportService;
import com.finvolv.selldown.service.SSRSExcelExportService;
import com.finvolv.selldown.service.SSRSFileService;
import com.finvolv.selldown.service.DocumentUploadService;
import com.finvolv.selldown.service.LoanDetailService;
import com.finvolv.selldown.service.LoanDetailService.LoanDetailInputForDeal;
import com.finvolv.selldown.service.LoanDetailService.LoanDetailInputForPartner;
import com.finvolv.selldown.service.LoanDetailService.LoanDetailModification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/loan-details")
@RequiredArgsConstructor
public class LoanDetailController {

    private static final Logger logger = LoggerFactory.getLogger(LoanDetailController.class);

    private final LoanDetailService loanDetailService;
    private final MonthlyLMSStatusRepository monthlyLMSStatusRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final MonthlyDealProcessingStatusRepository monthlyDealProcessingStatusRepository;
    private final com.finvolv.selldown.repository.CustomerRepository customerRepository;
    private final PartnerPayoutDetailsAllRepository partnerPayoutDetailsAllRepository;
    private final PartnerPayoutDetailsAllService partnerPayoutDetailsAllService;
    private final ExcelExportService excelExportService;
    private final SSRSExcelExportService ssrsExcelExportService;
    private final SSRSFileService ssrsFileService;
    private final DocumentUploadService documentUploadService;

    @PostMapping(
        path = "/partners/{partnerId}/bulk",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Flux<LoanDetail>> bulkCreateLoanDetailsForPartner(
        @PathVariable Long partnerId,
        @RequestBody Flux<LoanDetailInputForPartner> loanDetails,
        @RequestParam(required = false, defaultValue = "false") Boolean removeExistingLoanDetails
    ) {
        return ResponseEntity.ok(
            loanDetails.collectList()
            .flatMapMany(loanDetailsList -> {
                logger.info("Received bulk create request - partnerId: {}, number of loans: {}", partnerId, loanDetailsList.size());
                return loanDetailService.bulkCreateLoanDetailsForPartner(partnerId, loanDetailsList, removeExistingLoanDetails)
                    .doOnComplete(() -> 
                        logger.info("Completed bulk create request - partnerId: {}, number of loans: {}", partnerId, loanDetailsList.size()))
                    .doOnError(error -> 
                        logger.error("Error processing bulk create request - partnerId: {}, number of loans: {}: {}", 
                            partnerId, loanDetailsList.size(), error.getMessage()));
            })
        );
    }

    @PostMapping(
        path = "/partners/{partnerId}/deals/{dealId}/bulk",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Flux<LoanDetail>> bulkCreateLoanDetailsForDeal(
            @PathVariable Long partnerId,
            @PathVariable Long dealId,
            @RequestBody Flux<LoanDetailInputForDeal> loanDetails) {
            return ResponseEntity.ok(
                loanDetails.collectList()
                .flatMapMany(loanDetailsList -> {
                    logger.info("Received bulk create request - dealId: {}, partnerId: {}, number of loans: {}", dealId, partnerId, loanDetailsList.size());
                    return loanDetailService.bulkCreateLoanDetailsForDeal(dealId, partnerId, loanDetailsList);
                })
            );
    }

    @PutMapping(
        path = "/partners/{partnerId}/deals/{dealId}/bulk",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Flux<LoanDetail>> bulkUpdateLoanDetailsForDeal(
            @PathVariable Long dealId,
            @PathVariable Long partnerId,
            @RequestBody Flux<LoanDetailModification> modifications) {
        return ResponseEntity.ok(
            modifications.collectList()
            .flatMapMany(modificationsList -> {
                logger.info("Received bulk update request - dealId: {}, partnerId: {}, number of modifications: {}", dealId, partnerId, modificationsList.size());
                return loanDetailService.bulkUpdateLoanDetails(dealId, partnerId, modificationsList);
            })
        );
    }

    @GetMapping("/partners/{partnerId}/deals/{dealId}")
    public ResponseEntity<Flux<LoanDetail>> getLoanDetailsForDeal(
        @PathVariable Long dealId,
        @PathVariable Long partnerId
    ) {
        logger.info("Received get request - dealId: {}, partnerId: {}", dealId, partnerId);
        
        return ResponseEntity.ok(
            loanDetailService.getLoanDetailsForDeal(dealId, partnerId)
                .doOnComplete(() -> 
                    logger.info("Completed get request - dealId: {}, partnerId: {}", dealId, partnerId))
                .doOnError(error -> 
                    logger.error("Error processing get request - dealId: {}, partnerId: {}: {}", 
                        dealId, partnerId, error.getMessage()))
        );
    }

    @GetMapping("/partners/{partnerId}")
    public ResponseEntity<Flux<LoanDetail>> getLoanDetailsForPartner(
        @PathVariable Long partnerId
    ) {
        logger.info("Received get request - partnerId: {}", partnerId);
        return ResponseEntity.ok(loanDetailService.getLoanDetailsForPartner(partnerId)
            .doOnComplete(() -> 
                logger.info("Completed get request - partnerId: {}", partnerId))
            .doOnError(error -> 
                logger.error("Error processing get request - partnerId: {}: {}", partnerId, error.getMessage())));
    }

   @GetMapping("/monthly-status")
   public ResponseEntity<Mono<MonthlyDealProcessingStatus>> getMonthlyDealProcessingStatus(
       @RequestParam Long dealId,
       @RequestParam Long partnerId,
       @RequestParam int year,
       @RequestParam int month
   ) {
       return ResponseEntity.ok(loanDetailService.getMonthlyLoanDetailsByDealId(dealId, partnerId, year, month));
   }

   @GetMapping(value = "/lms-status/year/{year}/month/{month}", 
               produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<Mono<MonthlyLMSStatusEntity>> getLMSStatusByYearAndMonth(
       @PathVariable Integer year,
       @PathVariable Integer month
   ) {
       logger.info("Received request to fetch LMS status - year: {}, month: {}", year, month);
       
       return ResponseEntity.ok(
           monthlyLMSStatusRepository.findByYearAndMonth(year, month)
               .doOnNext(lmsStatus -> logger.debug("Retrieved LMS status: {}", lmsStatus.getId()))
               .doOnSuccess(lmsStatus -> 
                   logger.info("Successfully retrieved LMS status - year: {}, month: {}, status: {}", 
                       year, month, lmsStatus != null ? lmsStatus.getStatus() : "NOT_FOUND"))
               .doOnError(error -> 
                   logger.error("Error retrieving LMS status - year: {}, month: {}: {}", 
                       year, month, error.getMessage()))
       );
   }

    @GetMapping(value = "/deal-processing-status/deal/{dealId}/partner/{partnerId}/year/{year}/month/{month}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MonthlyDealProcessingStatus>> getDealProcessingStatus(
        @PathVariable Long dealId,
        @PathVariable Long partnerId,
        @PathVariable Integer year,
        @PathVariable Integer month
    ) {
        logger.info("Received request to fetch deal processing status - dealId: {}, partnerId: {}, year: {}, month: {}",
            dealId, partnerId, year, month);

        return monthlyDealProcessingStatusRepository
            .findByDealIdAndPartnerIdAndYearAndMonth(dealId, partnerId, year, month)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
            .doOnError(error -> logger.error("Error fetching deal processing status: {}", error.getMessage(), error));
    }


    @GetMapping(value = "/excel-generation/deal/{dealId}/partner/{partnerId}/year/{year}/month/{month}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ExcelGenerationResponse>> generateExcel(
        @PathVariable Long dealId,
        @PathVariable Long partnerId,
        @PathVariable Integer year,
        @PathVariable Integer month
    ) {
        logger.info("Received Excel generation request - dealId: {}, partnerId: {}, year: {}, month: {}", 
            dealId, partnerId, year, month);
        
        return generateExcelData(dealId, partnerId, year, month)
            .doOnNext(response -> logger.info("Received response from generateExcelData: success={}, discrepancyCount={}", 
                response.isSuccess(), response.getDiscrepancyCount()))
            .map(response -> {
                logger.info("Returning response with {} discrepancies and {} payout details", 
                    response.getDiscrepancyCount(), response.getMatchedPayoutDetailsCount());
                return ResponseEntity.ok(response);
            })
            .doOnNext(responseEntity -> logger.info("ResponseEntity created with status: {}", responseEntity.getStatusCode()))
            .doOnError(error -> logger.error("Error in generateExcel: {}", error.getMessage(), error))
            .onErrorReturn(ResponseEntity.status(500).body(ExcelGenerationResponse.builder()
                .dealId(dealId)
                .partnerId(partnerId)
                .year(year)
                .month(month)
                .message("Internal server error")
                .success(false)
                .build()));
    }

    @GetMapping(value = "/excel-generation-file/deal/{dealId}/partner/{partnerId}/year/{year}/month/{month}")
    public Mono<ResponseEntity<?>> downloadOrUploadExcel(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "upload", required = false, defaultValue = "true") Boolean upload,
        @PathVariable Long dealId,
        @PathVariable Long partnerId,
        @PathVariable Integer year,
        @PathVariable Integer month
    ) {
        return generateExcelData(dealId, partnerId, year, month)
            .flatMap(resp -> {
                List<PartnerPayoutDetailsAll> payouts = resp.getMatchedPayoutDetails();
                if (payouts == null || payouts.isEmpty()) {
                    return Mono.just(ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No payout details found"
                    )));
                }

                String filename = String.format("partner-payout-%d-%d-%d-%d.xlsx", dealId, partnerId, year, month);
                
                return excelExportService.buildPartnerPayoutReport(payouts, dealId, partnerId)
                        .flatMap(bytes -> {
                            if (Boolean.TRUE.equals(upload)) {
                                if (authorization == null || authorization.isBlank()) {
                                    return Mono.just(ResponseEntity.badRequest().body(Map.of(
                                        "success", false,
                                        "message", "Authorization header is required for upload=true"
                                    )));
                                }

                                return partnerPayoutDetailsAllService.getDealById(dealId)
                                    .flatMap(deal -> customerNameFromDeal(deal.getCustomerId())
                                        .flatMap(partnerName -> documentUploadService.generateUploadId(authorization, partnerName, deal.getName(), year, month)
                                            .flatMap(generatedId -> documentUploadService.uploadExcel(authorization, generatedId, bytes, filename)
                                                .then(
                                                    monthlyDealProcessingStatusRepository
                                                        .findByDealIdAndPartnerIdAndYearAndMonth(dealId, partnerId, year, month)
                                                        .flatMap(status -> {
                                                            status.setStatus(MonthlyDealStatus.PAYOUT_FILE_GENERATED);
                                                            status.setModifiedAt(LocalDateTime.now());
                                                            return monthlyDealProcessingStatusRepository.save(status);
                                                        })
                                                )
                                                .thenReturn(ResponseEntity.ok(Map.of(
                                                    "success", true,
                                                    "generatedId", generatedId,
                                                    "message", "File uploaded successfully"
                                                ))))));
                            }

                            // Update status to PAYOUT_FILE_GENERATED for successful generation (download flow)
                            return monthlyDealProcessingStatusRepository
                                .findByDealIdAndPartnerIdAndYearAndMonth(dealId, partnerId, year, month)
                                .flatMap(status -> {
                                    status.setStatus(MonthlyDealStatus.PAYOUT_FILE_GENERATED);
                                    status.setModifiedAt(LocalDateTime.now());
                                    return monthlyDealProcessingStatusRepository.save(status);
                                })
                                .then(Mono.fromSupplier(() ->
                                    ResponseEntity.ok()
                                        .header("Content-Disposition", "attachment; filename=" + filename)
                                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                        .body(bytes)
                                ));
                        });
            });
    }

    @GetMapping(value = "/excel-generation-file/finance/deal/{dealId}/partner/{partnerId}/year/{year}/month/{month}")
    public Mono<ResponseEntity<?>> downloadOrUploadSSRSExcel(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "upload", required = false, defaultValue = "true") Boolean upload,
        @PathVariable Long dealId,
        @PathVariable Long partnerId,
        @PathVariable Integer year,
        @PathVariable Integer month
    ) {
        logger.info("Received SSRS Excel generation request - dealId: {}, partnerId: {}, year: {}, month: {}", 
            dealId, partnerId, year, month);
        
        // Step 1: Get loan details for deal and partner
        return loanDetailRepository.findByDealIdAndPartnerId(dealId, partnerId)
            .collectList()
            .flatMap(loanDetails -> {
                logger.info("Found {} loan details for dealId: {}, partnerId: {}", loanDetails.size(), dealId, partnerId);
                
                if (loanDetails.isEmpty()) {
                    return Mono.just(ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No loan details found"
                    )));
                }
                
                // Step 2: Get SSRS data for year and month
                return ssrsFileService.getSSRSFileDataByYearAndMonth(year, month)
                    .collectList()
                    .flatMap(ssrsDataList -> {
                        logger.info("Found {} SSRS records for year: {}, month: {}", ssrsDataList.size(), year, month);
                        
                        if (ssrsDataList.isEmpty()) {
                            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "message", "No SSRS data found for the specified year and month"
                            )));
                        }
                        
                        // Step 3: Match loan details with SSRS data by lmsLan
                        List<String> loanLans = loanDetails.stream()
                            .map(LoanDetail::getLmsLan)
                            .filter(lan -> lan != null && !lan.trim().isEmpty())
                            .toList();
                        
                        List<SSRSFileDataEntity> matchedSSRSData = ssrsDataList.stream()
                            .filter(ssrs -> loanLans.contains(ssrs.getLmsLan()))
                            .toList();
                        
                        logger.info("Matched {} SSRS records with loan details", matchedSSRSData.size());
                        
                        if (matchedSSRSData.isEmpty()) {
                            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "message", "No matching SSRS data found for loan details"
                            )));
                        }
                        
                        String filename = String.format("ssrs-finance-%d-%d-%d-%d.xlsx", dealId, partnerId, year, month);
                        
                        // Step 4: Generate Excel
                        return ssrsExcelExportService.buildSSRSReport(matchedSSRSData, year, month, dealId)
                            .flatMap(bytes -> {
                                if (Boolean.TRUE.equals(upload)) {
                                    if (authorization == null || authorization.isBlank()) {
                                        return Mono.just(ResponseEntity.badRequest().body(Map.of(
                                            "success", false,
                                            "message", "Authorization header is required for upload=true"
                                        )));
                                    }

                                    return partnerPayoutDetailsAllService.getDealById(dealId)
                                        .flatMap(deal -> customerNameFromDeal(deal.getCustomerId())
                                            .flatMap(partnerName -> documentUploadService.generateUploadIdForFinance(
                                                    authorization, partnerName, deal.getName(), year, month)
                                                .flatMap(generatedId -> documentUploadService.uploadExcel(authorization, generatedId, bytes, filename)
                                                    .thenReturn(ResponseEntity.ok(Map.of(
                                                        "success", true,
                                                        "generatedId", generatedId,
                                                        "message", "SSRS finance file uploaded successfully"
                                                    ))))));
                                }

                                // Download flow
                                return Mono.fromSupplier(() ->
                                    ResponseEntity.ok()
                                        .header("Content-Disposition", "attachment; filename=" + filename)
                                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                        .body(bytes)
                                );
                            });
                    });
            })
            .doOnError(error -> logger.error("Error generating SSRS Excel - dealId: {}, partnerId: {}, year: {}, month: {}: {}", 
                dealId, partnerId, year, month, error.getMessage(), error));
    }

    // Removed separate upload endpoint per request; unified into the single endpoint above controlled by ?upload=true

    private Mono<String> customerNameFromDeal(Long customerId) {
        return customerRepository.findById(customerId)
            .map(c -> c.getName());
    }

    private Mono<ExcelGenerationResponse> generateExcelData(Long dealId, Long partnerId, Integer year, Integer month) {
        logger.info("=== EXCEL GENERATION START ===");
        logger.info("Parameters: dealId={}, partnerId={}, year={}, month={}", dealId, partnerId, year, month);
        
        // Step 1: Get loan details
        return loanDetailRepository.findByDealIdAndPartnerId(dealId, partnerId)
            .collectList()
            .flatMap(loanDetails -> {
                logger.info("Found {} loan details for dealId: {}, partnerId: {}", loanDetails.size(), dealId, partnerId);
                
                if (loanDetails.isEmpty()) {
                    return Mono.just(createEmptyResponse(dealId, partnerId, year, month, loanDetails, "No loan details found"));
                }
                
                // Step 2: Get matching payout details
                return getMatchingPayoutDetails(dealId, partnerId, year, month, loanDetails);
            });
    }
    
    private Mono<ExcelGenerationResponse> getMatchingPayoutDetails(Long dealId, Long partnerId, Integer year, Integer month, List<LoanDetail> loanDetails) {
        List<String> lmsLans = loanDetails.stream()
            .map(LoanDetail::getLmsLan)
            .filter(lan -> lan != null && !lan.trim().isEmpty())
            .toList();
        
        logger.info("Extracted {} LMS LANs from loan details", lmsLans.size());
        
        // First get the lmsId from MonthlyLMSStatus using year and month
        // This is more reliable than extracting from cycle_start_date which may be in previous month
        return monthlyLMSStatusRepository.findByYearAndMonth(year, month)
            .switchIfEmpty(Mono.error(new RuntimeException(
                String.format("No LMS status found for year: %d, month: %d", year, month))))
            .flatMap(lmsStatus -> {
                Long lmsId = lmsStatus.getId();
                logger.info("Found LMS status with ID: {} for year: {}, month: {}", lmsId, year, month);
                
                // Query by lms_id and lms_lan instead of using cycle_start_date extraction
                return Flux.fromIterable(lmsLans)
                    .flatMap(lmsLan -> partnerPayoutDetailsAllRepository.findAllByLmsIdAndLmsLan(lmsId, lmsLan))
                    .collectList()
                    .flatMap(matchedPayoutDetails -> {
                        logger.info("Found {} matching payout details for year: {}, month: {}, lmsId: {}", 
                            matchedPayoutDetails.size(), year, month, lmsId);
                        
                        if (matchedPayoutDetails.isEmpty()) {
                            return Mono.just(createEmptyResponse(dealId, partnerId, year, month, loanDetails, 
                                "No matching payout details found for the specified year and month"));
                        }
                        
                        // Step 3: Get deal and process calculations
                        return processCalculationsAndDiscrepancies(dealId, partnerId, year, month, loanDetails, matchedPayoutDetails);
                    });
            });
    }
    
    private Mono<ExcelGenerationResponse> processCalculationsAndDiscrepancies(Long dealId, Long partnerId, Integer year, Integer month, 
                                                                             List<LoanDetail> loanDetails, List<PartnerPayoutDetailsAll> matchedPayoutDetails) {
        return partnerPayoutDetailsAllService.getDealById(dealId)
            .switchIfEmpty(Mono.error(new RuntimeException("Deal not found with ID: " + dealId)))
            .flatMap(deal -> {
                logger.info("Successfully fetched deal: ID={}, Name={}, AssignRatio={}", 
                    deal.getId(), deal.getName(), deal.getAssignRatio());
                
                // Create a map of loan details by lmsLan for quick lookup
                Map<String, LoanDetail> loanDetailMap = loanDetails.stream()
                    .filter(ld -> ld.getLmsLan() != null)
                    .collect(Collectors.toMap(LoanDetail::getLmsLan, ld -> ld, (existing, replacement) -> existing));
                
                // Apply calculations to matched payout details reactively
                return Flux.fromIterable(matchedPayoutDetails)
                    .flatMap(payoutDetail -> {
                        LoanDetail loanDetail = loanDetailMap.get(payoutDetail.getLmsLan());
                        return partnerPayoutDetailsAllService.calculateSellerFields(payoutDetail, deal, loanDetail, year, month);
                    })
                    .collectList()
                    .flatMap(calculatedPayoutDetails -> {
                        logger.info("Calculated seller fields for {} payout details", calculatedPayoutDetails.size());
                        
                        // Detect opening position mismatches (now reactive)
                        return partnerPayoutDetailsAllService.detectOpeningPosMismatches(
                            calculatedPayoutDetails, loanDetails, year, month)
                            .flatMap(discrepancies -> {
                                logger.info("Detected {} opening position mismatches", discrepancies.size());
                                return createDealStatusAndSaveData(dealId, partnerId, year, month, loanDetails, calculatedPayoutDetails, discrepancies);
                            });
                    });
            });
    }
    
    private Mono<ExcelGenerationResponse> createDealStatusAndSaveData(Long dealId, Long partnerId, Integer year, Integer month,
                                                                     List<LoanDetail> loanDetails, List<PartnerPayoutDetailsAll> calculatedPayoutDetails,
                                                                     List<OpeningPosDiscrepancy> discrepancies) {
        return createOrUpdateDealProcessingStatus(dealId, partnerId, year, month)
            .flatMap(dealStatus -> {
                logger.info("Created/updated deal processing status: {}", dealStatus.getId());
                
                // Save calculated payout details with seller fields
                return partnerPayoutDetailsAllRepository.saveAll(calculatedPayoutDetails)
                    .collectList()
                    .flatMap(savedPayoutDetails -> {
                        logger.info("Saved {} payout details with calculated seller fields", savedPayoutDetails.size());
                        
                        // Update deal status ID for matched payout details
                        return updatePayoutDetailsDealStatus(savedPayoutDetails, dealStatus.getId())
                            .doOnNext(updatedCount -> logger.info("Updated {} payout details with deal status ID", updatedCount))
                            .map(updatedCount -> {
                                logger.info("=== EXCEL GENERATION COMPLETE ===");
                                logger.info("Final result: {} discrepancies found, {} payout details processed", 
                                    discrepancies.size(), savedPayoutDetails.size());
                                
                                ExcelGenerationResponse response = ExcelGenerationResponse.builder()
                                    .dealId(dealId)
                                    .partnerId(partnerId)
                                    .year(year)
                                    .month(month)
                                    .loanDetails(loanDetails)
                                    .matchedPayoutDetails(savedPayoutDetails)
                                    .dealProcessingStatus(dealStatus)
                                    .openingPosDiscrepancies(discrepancies)
                                    .totalLoanDetails(loanDetails.size())
                                    .matchedPayoutDetailsCount(savedPayoutDetails.size())
                                    .updatedPayoutDetailsCount(updatedCount)
                                    .discrepancyCount(discrepancies.size())
                                    .message("Excel data generated successfully with calculated seller fields saved to database")
                                    .success(true)
                                    .build();
                                
                                logger.info("Built response: success={}, discrepancyCount={}, matchedCount={}", 
                                    response.isSuccess(), response.getDiscrepancyCount(), response.getMatchedPayoutDetailsCount());
                                
                                return response;
                            })
                            .doOnNext(response -> logger.info("Response ready to return: {}", response.getMessage()))
                            .doOnError(error -> logger.error("Error in final response building: {}", error.getMessage(), error));
                    });
            });
    }
    
    private ExcelGenerationResponse createEmptyResponse(Long dealId, Long partnerId, Integer year, Integer month, 
                                                       List<LoanDetail> loanDetails, String message) {
        return ExcelGenerationResponse.builder()
            .dealId(dealId)
            .partnerId(partnerId)
            .year(year)
            .month(month)
            .loanDetails(loanDetails)
            .matchedPayoutDetails(List.of())
            .dealProcessingStatus(null)
            .totalLoanDetails(loanDetails.size())
            .matchedPayoutDetailsCount(0)
            .updatedPayoutDetailsCount(0)
            .discrepancyCount(0)
            .message(message)
            .success(false)
            .build();
    }

   private Mono<MonthlyDealProcessingStatus> createOrUpdateDealProcessingStatus(Long dealId, Long partnerId, Integer year, Integer month) {
       return monthlyDealProcessingStatusRepository.findByDealIdAndPartnerIdAndYearAndMonth(dealId, partnerId, year, month)
           .switchIfEmpty(
               // Create new deal processing status if not exists
               monthlyDealProcessingStatusRepository.save(MonthlyDealProcessingStatus.builder()
                   .dealId(dealId)
                   .partnerId(partnerId)
                   .year(year)
                   .month(month)
                   .status(MonthlyDealStatus.PAYOUT_FILE_CREATED)
                   .cycleStartDate(LocalDate.of(year, month, 1))
                   .cycleEndDate(LocalDate.of(year, month, 1).withDayOfMonth(
                       LocalDate.of(year, month, 1).lengthOfMonth()))
                   .createdAt(LocalDateTime.now())
                   .modifiedAt(LocalDateTime.now())
                   .build())
           );
   }

   private Mono<Integer> updatePayoutDetailsDealStatus(List<PartnerPayoutDetailsAll> payoutDetails, Long dealStatusId) {
       if (payoutDetails.isEmpty()) {
           return Mono.just(0);
       }
       
       logger.info("Updating {} payout details with deal status ID: {} using individual updates", payoutDetails.size(), dealStatusId);
       
       // Use individual updates - process sequentially to ensure completion
       return Flux.fromIterable(payoutDetails)
           .concatMap(payout -> {
               logger.info("Updating payout detail ID: {} with deal status ID: {}", payout.getId(), dealStatusId);
               return partnerPayoutDetailsAllRepository.updateDealStatusIdById(payout.getId(), dealStatusId)
                   .doOnNext(result -> logger.info("Updated payout detail ID: {} - result: {}", payout.getId(), result));
           })
           .reduce(0, Integer::sum)
           .doOnNext(count -> logger.info("Completed updating {} payout details with deal status ID: {}", count, dealStatusId))
           .doOnError(error -> logger.error("Error updating payout details: {}", error.getMessage(), error));
   }
   

}
