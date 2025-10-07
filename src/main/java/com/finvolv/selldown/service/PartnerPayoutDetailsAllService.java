package com.finvolv.selldown.service;

import com.finvolv.selldown.dto.OpeningPosDiscrepancy;
import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.LoanDetail;
import com.finvolv.selldown.model.MonthlyLMSStatus;
import com.finvolv.selldown.model.MonthlyLMSStatusEntity;
import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import com.finvolv.selldown.repository.DealRepository;
import com.finvolv.selldown.repository.MonthlyLMSStatusRepository;
import com.finvolv.selldown.repository.PartnerPayoutDetailsAllRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerPayoutDetailsAllService {
    
    private static final Logger logger = LoggerFactory.getLogger(PartnerPayoutDetailsAllService.class);
    
    private final PartnerPayoutDetailsAllRepository partnerPayoutDetailsAllRepository;
    private final MonthlyLMSStatusRepository monthlyLMSStatusRepository;
    private final DealRepository dealRepository;
    
    @Transactional
    private Flux<PartnerPayoutDetailsAll> saveAllPayoutDetails(List<PartnerPayoutDetailsAll> payoutDetails) {
        logger.debug("Saving {} payout details in batch", payoutDetails.size());
        
        // Set lastCycleEndDate for each payout detail based on previous entry for same LMS LAN
        return Flux.fromIterable(payoutDetails)
            .flatMap(payout -> {
                // Find the latest previous entry for this LMS LAN
                return partnerPayoutDetailsAllRepository.findByLmsLan(payout.getLmsLan())
                    .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) // Sort by created_at desc
                    .next() // Get the latest one
                    .map(latestEntry -> {
                        // Set lastCycleEndDate to the latest entry's cycle_end_date
                        payout.setLastCycleEndDate(latestEntry.getCycleEndDate());
                        logger.debug("Set lastCycleEndDate for {} to {}", payout.getLmsLan(), latestEntry.getCycleEndDate());
                        return payout;
                    })
                    .switchIfEmpty(Mono.just(payout).doOnNext(p -> {
                        // No previous entry found, set to null
                        p.setLastCycleEndDate(null);
                        logger.debug("No previous entry found for {}, set lastCycleEndDate to null", p.getLmsLan());
                    }));
            })
            .collectList()
            .flatMapMany(updatedPayouts -> partnerPayoutDetailsAllRepository.saveAll(updatedPayouts));
    }
    
    public Flux<PartnerPayoutDetailsAll> uploadLMSFile(Integer year, Integer month, List<PartnerPayoutDetailsAll> payoutDetails) {
        logger.info("Uploading LMS file - year: {}, month: {}, number of records: {}", 
            year, month, payoutDetails.size());
        
        return createOrUpdateLMSStatus(year, month)
            .flatMapMany(lmsStatusId -> {
                logger.debug("Created/Updated LMS status with ID: {}", lmsStatusId);
                
                // Prepare all payout details with LMS ID and timestamps
                List<PartnerPayoutDetailsAll> preparedPayoutDetails = payoutDetails.stream()
                    .map(payout -> {
                        // Set the LMS ID and current timestamp
                        payout.setLmsId(lmsStatusId);
                        payout.setCreatedAt(LocalDateTime.now());
                        payout.setModifiedAt(LocalDateTime.now());
                        
                        // Set cycle dates if not already set
                        if (payout.getCycleStartDate() == null) {
                            payout.setCycleStartDate(LocalDate.of(year, month, 1));
                        }
                        if (payout.getCycleEndDate() == null) {
                            payout.setCycleEndDate(LocalDate.of(year, month, 1).withDayOfMonth(
                                LocalDate.of(year, month, 1).lengthOfMonth()));
                        }
                        
                        return payout;
                    })
                    .toList();
                
                // Save all payout details in a single transaction
                return saveAllPayoutDetails(preparedPayoutDetails)
                    .doOnComplete(() -> 
                        logger.info("Successfully uploaded LMS file - year: {}, month: {}, records: {}", 
                            year, month, payoutDetails.size()))
                    .doOnError(error -> 
                        logger.error("Error uploading LMS file - year: {}, month: {}: {}", 
                            year, month, error.getMessage()));
            });
    }
    
    @Transactional
    private Mono<Long> createOrUpdateLMSStatus(Integer year, Integer month) {
        logger.debug("Creating or updating LMS status - year: {}, month: {}", year, month);
        
        return monthlyLMSStatusRepository.findByYearAndMonth(year, month)
            .switchIfEmpty(
                // Create new LMS status if not exists
                createNewLMSStatus(year, month)
            )
            .map(MonthlyLMSStatusEntity::getId)
            .doOnNext(id -> logger.debug("Using LMS status ID: {} for year: {}, month: {}", id, year, month))
            .doOnError(error -> 
                logger.error("Error creating/updating LMS status - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    private Mono<MonthlyLMSStatusEntity> createNewLMSStatus(Integer year, Integer month) {
        logger.debug("Creating new LMS status - year: {}, month: {}", year, month);
        
        MonthlyLMSStatusEntity lmsStatus = MonthlyLMSStatusEntity.builder()
            .year(year)
            .month(month)
            .status(MonthlyLMSStatus.LMS_INITIALIZED)
            .cycleStartDate(LocalDate.of(year, month, 1))
            .cycleEndDate(LocalDate.of(year, month, 1).withDayOfMonth(
                LocalDate.of(year, month, 1).lengthOfMonth()))
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
            
        return monthlyLMSStatusRepository.save(lmsStatus)
            .doOnNext(saved -> logger.debug("Created new LMS status: {}", saved));
    }
    
    @Transactional(readOnly = true)
    public Flux<PartnerPayoutDetailsAll> getPayoutDetailsByYearAndMonth(Integer year, Integer month) {
        logger.info("Fetching payout details - year: {}, month: {}", year, month);
        
        return monthlyLMSStatusRepository.findByYearAndMonth(year, month)
            .flatMapMany(lmsStatus -> 
                partnerPayoutDetailsAllRepository.findByLmsId(lmsStatus.getId())
            )
            .doOnComplete(() -> 
                logger.info("Completed fetching payout details - year: {}, month: {}", year, month))
            .doOnError(error -> 
                logger.error("Error fetching payout details - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }
    
    @Transactional(readOnly = true)
    public Flux<PartnerPayoutDetailsAll> getPayoutDetailsByLmsId(Long lmsId) {
        logger.info("Fetching payout details - lmsId: {}", lmsId);
        
        return partnerPayoutDetailsAllRepository.findByLmsId(lmsId)
            .doOnComplete(() -> 
                logger.info("Completed fetching payout details - lmsId: {}", lmsId))
            .doOnError(error -> 
                logger.error("Error fetching payout details - lmsId: {}: {}", lmsId, error.getMessage()));
    }
    
    @Transactional
    public Mono<Void> deletePayoutDetailsByYearAndMonth(Integer year, Integer month) {
        logger.info("Deleting payout details - year: {}, month: {}", year, month);
        
        return monthlyLMSStatusRepository.findByYearAndMonth(year, month)
            .flatMap(lmsStatus -> 
                partnerPayoutDetailsAllRepository.deleteByLmsId(lmsStatus.getId())
            )
            .then()
            .doOnSuccess(unused -> 
                logger.info("Completed deleting payout details - year: {}, month: {}", year, month))
            .doOnError(error -> 
                logger.error("Error deleting payout details - year: {}, month: {}: {}", 
                    year, month, error.getMessage()));
    }

    /**
     * Fetches deal information by ID
     */
    public Mono<Deal> getDealById(Long dealId) {
        return dealRepository.findById(dealId);
    }

    /**
     * Calculates seller fields based on assigned ratio and interest rate from deal table
     * This method can be easily modified to change calculation logic
     */
    public PartnerPayoutDetailsAll calculateSellerFields(PartnerPayoutDetailsAll payoutDetail, Deal deal) {
        if (deal == null || deal.getAssignRatio() == null || deal.getAnnualInterestRate() == null) {
            logger.warn("Deal or required fields are null, skipping calculation for payout detail: {}", payoutDetail.getId());
            return payoutDetail;
        }

        Double assignRatio = deal.getAssignRatio();
        Double annualInterestRate = deal.getAnnualInterestRate();
        
        // Calculate days between cycle start and end date
        long daysBetween = 0;
        if (payoutDetail.getCycleStartDate() != null && payoutDetail.getCycleEndDate() != null) {
            daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                payoutDetail.getCycleStartDate(), 
                payoutDetail.getCycleEndDate()
            );
        }

        // Calculate daily interest rate
        Double dailyInterestRate = annualInterestRate / 365.0;

        // Calculate seller fields by multiplying with assigned ratio for POS related fields
        payoutDetail.setSellerOpeningPos(calculateValue(payoutDetail.getOpeningPos(), assignRatio));

        payoutDetail.setSellerClosingPos(calculateValue(payoutDetail.getClosingPos(), assignRatio)); 
        payoutDetail.setSellerTotalPrincipalDue(calculateValue(payoutDetail.getTotalPrincipalDue(), assignRatio));
        payoutDetail.setSellerPrincipalOverdue(calculateValue(payoutDetail.getPrincipalOverdue(), assignRatio));
        payoutDetail.setSellerTotalPrincipalComponentPaid(calculateValue(payoutDetail.getTotalPrincipalComponentPaid(), assignRatio));
        payoutDetail.setSellerPrincipalOverduePaid(calculateValue(payoutDetail.getPrincipalOverduePaid(), assignRatio));
        payoutDetail.setSellerForeclosurePaid(calculateValue(payoutDetail.getForeclosurePaid(), assignRatio));
        payoutDetail.setSellerForeclosureChargesPaid(calculateValue(payoutDetail.getForeclosureChargesPaid(), assignRatio));
        payoutDetail.setSellerPrepaymentPaid(calculateValue(payoutDetail.getPrepaymentPaid(), assignRatio));
        payoutDetail.setSellerPrepaymentChargesPaid(calculateValue(payoutDetail.getPrepaymentChargesPaid(), assignRatio));
        payoutDetail.setSellerTotalChargesPaid(calculateValue(payoutDetail.getTotalChargesPaid(), assignRatio));
        payoutDetail.setSellerTotalPaid(calculateValue(payoutDetail.getTotalPaid(), assignRatio));



        //Not sure about this
        payoutDetail.setSellerInterestOverduePaid(calculateValue(payoutDetail.getInterestOverduePaid(), assignRatio)); ///Not sure about this
        payoutDetail.setSellerInterestOverdue(calculateValue(payoutDetail.getInterestOverdue(), assignRatio)); //Not sure about this

      

        // Calculate interest based on seller opening position, interest rate, and days
        if (payoutDetail.getSellerOpeningPos() != null && daysBetween > 0) {
            BigDecimal calculatedInterest = payoutDetail.getSellerOpeningPos()
                .multiply(BigDecimal.valueOf(dailyInterestRate))
                .multiply(BigDecimal.valueOf(daysBetween))
                .setScale(2, RoundingMode.HALF_UP);
            
            payoutDetail.setSellerTotalInterestDue(calculatedInterest);
        }
        // Calculate seller total interest component paid
        BigDecimal totalInterestComponentPaid = payoutDetail.getTotalInterestComponentPaid();
        if (totalInterestComponentPaid != null && totalInterestComponentPaid.compareTo(BigDecimal.ZERO) > 0) {
            payoutDetail.setSellerTotalInterestComponentPaid(payoutDetail.getSellerTotalInterestDue());
        } else {
            payoutDetail.setSellerTotalInterestComponentPaid(BigDecimal.ZERO);
        }
        
       


        logger.debug("Calculated seller fields for payout detail {} with assign ratio: {}, interest rate: {}, days: {}", 
            payoutDetail.getId(), assignRatio, annualInterestRate, daysBetween);
        
        return payoutDetail;
    }

    /**
     * Helper method to calculate BigDecimal value by multiplying with ratio
     */
    private BigDecimal calculateValue(BigDecimal value, Double ratio) {
        if (value == null || ratio == null) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(ratio)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Helper method to calculate integer value by multiplying with ratio and rounding
     */
    private Integer calculateIntegerValue(Integer value, Double ratio) {
        if (value == null || ratio == null) {
            return 0;
        }
        return (int) Math.round(value * ratio);
    }

    /**
     * Detect opening position mismatches between payout details and loan details
     */
    public Mono<List<OpeningPosDiscrepancy>> detectOpeningPosMismatches(
            List<PartnerPayoutDetailsAll> payoutDetails, 
            List<LoanDetail> loanDetails,
            Integer year, 
            Integer month) {
        
        // Create a map of loan details for quick lookup
        Map<String, LoanDetail> loanDetailMap = loanDetails.stream()
            .filter(ld -> ld.getLmsLan() != null)
            .collect(Collectors.toMap(LoanDetail::getLmsLan, ld -> ld));
        
        // Process each payout detail reactively
        return Flux.fromIterable(payoutDetails)
            .filter(payout -> payout.getLmsLan() != null)
            .flatMap(payout -> {
                LoanDetail loanDetail = loanDetailMap.get(payout.getLmsLan());
                if (loanDetail == null) {
                    return Mono.just(OpeningPosDiscrepancy.builder()
                        .lmsLan(payout.getLmsLan())
                        .payoutOpeningPos(payout.getOpeningPos())
                        .loanDetailOpeningPos(null)
                        .difference(BigDecimal.ZERO)
                        .isMismatch(false)
                        .discrepancyType("NO_LOAN_DETAIL")
                        .description("No loan detail found")
                        .build());
                }
                
                // Check if last cycle end date is present to determine which logic to use
                if (payout.getLastCycleEndDate() != null) {
                    
                    return getPreviousMonthClosingPosReactive(payout.getLmsLan(), year, month)
                        .map(prevClosingPos -> {
                            BigDecimal expectedOpeningPos = prevClosingPos != null ? prevClosingPos : BigDecimal.ZERO;
                            String discrepancyType = "PREVIOUS_MONTH";
                            String description = "";
                            boolean isMismatch = false;
                            return processComparison(payout, expectedOpeningPos, discrepancyType, description, isMismatch);
                        })
                        .flatMap(discrepancy -> discrepancy.isMismatch() ? Mono.just(discrepancy) : Mono.empty())
                        .switchIfEmpty(Mono.fromCallable(() -> {
                            // If no previous month data, use loan details
                            BigDecimal expectedOpeningPos = loanDetail.getCurrentPOS() != null ?
                                BigDecimal.valueOf(loanDetail.getCurrentPOS()) : BigDecimal.ZERO;
                            String discrepancyType = "CURRENT_MONTH";
                            String description = "";
                            boolean isMismatch = false;
                            return processComparison(payout, expectedOpeningPos, discrepancyType, description, isMismatch);
                        }))
                        .flatMap(discrepancy -> discrepancy.isMismatch() ? Mono.just(discrepancy) : Mono.empty());
                } else {
                    // If last cycle end date is NOT present, use loan details current position
                    BigDecimal expectedOpeningPos = loanDetail.getCurrentPOS() != null ?
                        BigDecimal.valueOf(loanDetail.getCurrentPOS()) : BigDecimal.ZERO;
                    String discrepancyType = "CURRENT_MONTH";
                    String description = "";
                    boolean isMismatch = false;
                    
                    return Mono.fromCallable(() -> processComparison(payout, expectedOpeningPos, discrepancyType, description, isMismatch))
                        .flatMap(discrepancy -> discrepancy.isMismatch() ? Mono.just(discrepancy) : Mono.empty());
                }
            })
            .collectList();
    }
    
    private OpeningPosDiscrepancy processComparison(PartnerPayoutDetailsAll payout, BigDecimal expectedOpeningPos, 
                                                   String discrepancyType, String description, boolean isMismatch) {
        BigDecimal payoutOpeningPos = payout.getOpeningPos();
        
        // Handle null values by converting to BigDecimal.ZERO
        if (payoutOpeningPos == null) {
            payoutOpeningPos = BigDecimal.ZERO;
        }
        if (expectedOpeningPos == null) {
            expectedOpeningPos = BigDecimal.ZERO;
        }
        
        // Compare the values
        if (payoutOpeningPos.compareTo(expectedOpeningPos) != 0) {
            isMismatch = true;
            description = String.format("Opening position mismatch: Payout has %s, Expected %s (%s)", 
                payoutOpeningPos, expectedOpeningPos, discrepancyType);
        }
        
        // Set the mismatch flag in the payout detail
        payout.setIsOpeningPosMisMatch(isMismatch);
        
        if (isMismatch) {
            BigDecimal difference = BigDecimal.ZERO;
            if (payoutOpeningPos != null && expectedOpeningPos != null) {
                difference = payoutOpeningPos.subtract(expectedOpeningPos);
            }
            
            return OpeningPosDiscrepancy.builder()
                .lmsLan(payout.getLmsLan())
                .payoutOpeningPos(payoutOpeningPos)
                .loanDetailOpeningPos(expectedOpeningPos) // This now represents expected position
                .difference(difference)
                .isMismatch(isMismatch)
                .discrepancyType(discrepancyType)
                .description(description)
                .build();
        } else {
            // Return a special "no discrepancy" object instead of null
            return OpeningPosDiscrepancy.builder()
                .lmsLan(payout.getLmsLan())
                .payoutOpeningPos(payoutOpeningPos)
                .loanDetailOpeningPos(expectedOpeningPos)
                .difference(BigDecimal.ZERO)
                .isMismatch(false)
                .discrepancyType(discrepancyType)
                .description("No discrepancy")
                .build();
        }
    }
    
    
    
    /**
     * Get the closing position from the previous month for a given LMS LAN (reactive)
     */
    private Mono<BigDecimal> getPreviousMonthClosingPosReactive(String lmsLan, Integer year, Integer month) {
        // Calculate previous month and year
        final Integer prevMonth;
        final Integer prevYear;
        
        if (month == 1) {
            prevMonth = 12;
            prevYear = year - 1;
        } else {
            prevMonth = month - 1;
            prevYear = year;
        }
        
        // Find the payout detail for the previous month
        return partnerPayoutDetailsAllRepository.findByLmsLanAndYearAndMonth(lmsLan, prevYear, prevMonth)
            .map(PartnerPayoutDetailsAll::getClosingPos)
            .next() // Get the first result
            .onErrorResume(error -> Mono.empty()) // Return empty if error
            .switchIfEmpty(Mono.empty()); // Return empty if no results found
    }

   
}