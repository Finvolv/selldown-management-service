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
    private Flux<PartnerPayoutDetailsAll> saveAllPayoutDetails(List<PartnerPayoutDetailsAll> payoutDetails, Long lmsId) {
        logger.debug("Saving {} payout details in batch for lmsId: {}", payoutDetails.size(), lmsId);
        
        // Process each payout detail: check if exists, update or create
        return Flux.fromIterable(payoutDetails)
            .flatMap(payout -> {
                // First, set lastCycleEndDate for each payout detail based on previous entry for same LMS LAN
                Mono<PartnerPayoutDetailsAll> payoutWithLastCycleDate = partnerPayoutDetailsAllRepository.findByLmsLan(payout.getLmsLan())
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
                
                // Check if entry already exists for this lmsId and lmsLan
                return payoutWithLastCycleDate
                    .flatMap(p -> {
                        // Use Flux to handle multiple results (duplicates)
                        return partnerPayoutDetailsAllRepository.findAllByLmsIdAndLmsLan(lmsId, p.getLmsLan())
                            .collectList()
                            .flatMap(existingList -> {
                                if (!existingList.isEmpty()) {
                                    // Entry exists - take the first one (latest by id DESC) and update it
                                    // Also delete any duplicates
                                    PartnerPayoutDetailsAll existing = existingList.get(0);
                                    logger.debug("Found existing entry for lmsId: {}, lmsLan: {} (found {} duplicate(s))", 
                                        lmsId, p.getLmsLan(), existingList.size());
                                    
                                    // If there are duplicates, delete them (keep only the first one)
                                    Mono<PartnerPayoutDetailsAll> existingMono;
                                    if (existingList.size() > 1) {
                                        logger.warn("Found {} duplicate entries for lmsId: {}, lmsLan: {}. Deleting duplicates.", 
                                            existingList.size(), lmsId, p.getLmsLan());
                                        
                                        // Delete duplicates (all except the first one)
                                        List<Long> duplicateIds = existingList.stream()
                                            .skip(1)
                                            .map(PartnerPayoutDetailsAll::getId)
                                            .toList();
                                        
                                        existingMono = Flux.fromIterable(duplicateIds)
                                            .flatMap(partnerPayoutDetailsAllRepository::deleteById)
                                            .then(Mono.just(existing));
                                    } else {
                                        existingMono = Mono.just(existing);
                                    }
                                    
                                    // Update all fields on the existing entry
                                    return existingMono.map(entityToUpdate -> {
                                        // Copy all fields from new payout to existing, preserving the ID and createdAt
                                        entityToUpdate.setLmsId(p.getLmsId());
                                        entityToUpdate.setLmsLan(p.getLmsLan());
                                        entityToUpdate.setOpeningPos(p.getOpeningPos());
                                        entityToUpdate.setClosingPos(p.getClosingPos());
                                        entityToUpdate.setTotalPrincipalDue(p.getTotalPrincipalDue());
                                        entityToUpdate.setPrincipalOverdue(p.getPrincipalOverdue());
                                        entityToUpdate.setTotalPrincipalComponentPaid(p.getTotalPrincipalComponentPaid());
                                        entityToUpdate.setPrincipalOverduePaid(p.getPrincipalOverduePaid());
                                        entityToUpdate.setTotalInterestDue(p.getTotalInterestDue());
                                        entityToUpdate.setInterestOverdue(p.getInterestOverdue());
                                        entityToUpdate.setTotalInterestComponentPaid(p.getTotalInterestComponentPaid());
                                        entityToUpdate.setInterestOverduePaid(p.getInterestOverduePaid());
                                        entityToUpdate.setForeclosurePaid(p.getForeclosurePaid());
                                        entityToUpdate.setForeclosureChargesPaid(p.getForeclosureChargesPaid());
                                        entityToUpdate.setPrepaymentPaid(p.getPrepaymentPaid());
                                        entityToUpdate.setPrepaymentChargesPaid(p.getPrepaymentChargesPaid());
                                        entityToUpdate.setTotalChargesPaid(p.getTotalChargesPaid());
                                        entityToUpdate.setTotalPaid(p.getTotalPaid());
                                        entityToUpdate.setOpeningDpd(p.getOpeningDpd());
                                        entityToUpdate.setClosingDpd(p.getClosingDpd());
                                        entityToUpdate.setSellerOpeningPos(p.getSellerOpeningPos());
                                        entityToUpdate.setSellerClosingPos(p.getSellerClosingPos());
                                        entityToUpdate.setSellerTotalPrincipalDue(p.getSellerTotalPrincipalDue());
                                        entityToUpdate.setSellerPrincipalOverdue(p.getSellerPrincipalOverdue());
                                        entityToUpdate.setSellerTotalPrincipalComponentPaid(p.getSellerTotalPrincipalComponentPaid());
                                        entityToUpdate.setSellerPrincipalOverduePaid(p.getSellerPrincipalOverduePaid());
                                        entityToUpdate.setSellerTotalInterestDue(p.getSellerTotalInterestDue());
                                        entityToUpdate.setSellerInterestOverdue(p.getSellerInterestOverdue());
                                        entityToUpdate.setSellerTotalInterestComponentPaid(p.getSellerTotalInterestComponentPaid());
                                        entityToUpdate.setSellerInterestOverduePaid(p.getSellerInterestOverduePaid());
                                        entityToUpdate.setSellerForeclosurePaid(p.getSellerForeclosurePaid());
                                        entityToUpdate.setSellerForeclosureChargesPaid(p.getSellerForeclosureChargesPaid());
                                        entityToUpdate.setSellerPrepaymentPaid(p.getSellerPrepaymentPaid());
                                        entityToUpdate.setSellerPrepaymentChargesPaid(p.getSellerPrepaymentChargesPaid());
                                        entityToUpdate.setSellerTotalChargesPaid(p.getSellerTotalChargesPaid());
                                        entityToUpdate.setSellerTotalPaid(p.getSellerTotalPaid());
                                        entityToUpdate.setSellerOpeningDpd(p.getSellerOpeningDpd());
                                        entityToUpdate.setSellerClosingDpd(p.getSellerClosingDpd());
                                        entityToUpdate.setDealStatusId(p.getDealStatusId());
                                        entityToUpdate.setCycleStartDate(p.getCycleStartDate());
                                        entityToUpdate.setCycleEndDate(p.getCycleEndDate());
                                        entityToUpdate.setLastCycleEndDate(p.getLastCycleEndDate());
                                        
                                        // Ensure isOpeningPosMisMatch is never null (use value from p, or default to false)
                                        Boolean isOpeningPosMisMatch = p.getIsOpeningPosMisMatch();
                                        if (isOpeningPosMisMatch == null) {
                                            isOpeningPosMisMatch = false;
                                        }
                                        entityToUpdate.setIsOpeningPosMisMatch(isOpeningPosMisMatch);
                                        
                                        // Update modified timestamp, but preserve created timestamp
                                        entityToUpdate.setModifiedAt(LocalDateTime.now());
                                        
                                        return entityToUpdate;
                                    });
                                } else {
                                    // Entry doesn't exist - create new one
                                    logger.debug("Creating new entry for lmsId: {}, lmsLan: {}", lmsId, p.getLmsLan());
                                    return Mono.just(p);
                                }
                            });
                    });
            })
            .flatMap(partnerPayoutDetailsAllRepository::save);
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
                        
                        // Ensure isOpeningPosMisMatch is never null (default to false)
                        if (payout.getIsOpeningPosMisMatch() == null) {
                            payout.setIsOpeningPosMisMatch(false);
                        }
                        
                        return payout;
                    })
                    .toList();
                
                // Save all payout details in a single transaction (update if exists, create if not)
                return saveAllPayoutDetails(preparedPayoutDetails, lmsStatusId)
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
     * Reactive version that handles previous month data lookup
     */
    public Mono<PartnerPayoutDetailsAll> calculateSellerFields(PartnerPayoutDetailsAll payoutDetail, Deal deal, LoanDetail loanDetail, Integer year, Integer month) {
        if (deal == null || deal.getAssignRatio() == null || deal.getAnnualInterestRate() == null) {
            logger.warn("Deal or required fields are null, skipping calculation for payout detail: {}", payoutDetail.getId());
            return Mono.just(payoutDetail);
        }

        Double assignRatio = deal.getAssignRatio();
        Double annualInterestRate = deal.getAnnualInterestRate();
        
        // Calculate days between cycle start and end date (make it final for lambda usage)
        final long daysBetween = (payoutDetail.getCycleStartDate() != null && payoutDetail.getCycleEndDate() != null) ?
            java.time.temporal.ChronoUnit.DAYS.between(
                payoutDetail.getCycleStartDate(), 
                payoutDetail.getCycleEndDate()
            ) : 0L;

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
logger.info("annualInterestRate: {}", annualInterestRate);
logger.info("daysBetween: {}", daysBetween);
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

        // Calculate sellerInterestOverdue reactively:
        // - If previous month is empty: from LoanDetail currentAssignedOverdueInterest
        // - If previous month exists: from previous month (sellerTotalInterestDue - sellerTotalInterestComponentPaid)
        return calculateSellerInterestOverdueReactive(payoutDetail, loanDetail, year, month)
            .map(sellerInterestOverdue -> {
                payoutDetail.setSellerInterestOverdue(sellerInterestOverdue);
                
                // Calculate sellerInterestOverduePaid:
                // - If sellerInterestOverdue > 0: sellerTotalInterestComponentPaid - (sellerTotalInterestDue - sellerInterestOverdue)
                // - Otherwise: 0
                BigDecimal sellerInterestOverduePaid = BigDecimal.ZERO;
                if (sellerInterestOverdue != null && sellerInterestOverdue.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal sellerTotalInterestDue = payoutDetail.getSellerTotalInterestDue() != null ? 
                        payoutDetail.getSellerTotalInterestDue() : BigDecimal.ZERO;
                    BigDecimal sellerTotalInterestComponentPaid = payoutDetail.getSellerTotalInterestComponentPaid() != null ? 
                        payoutDetail.getSellerTotalInterestComponentPaid() : BigDecimal.ZERO;
                    
                    sellerInterestOverduePaid = sellerTotalInterestComponentPaid
                        .subtract(sellerTotalInterestDue.subtract(sellerInterestOverdue))
                        .setScale(2, RoundingMode.HALF_UP);
                    
                    // Ensure non-negative
                    if (sellerInterestOverduePaid.compareTo(BigDecimal.ZERO) < 0) {
                        sellerInterestOverduePaid = BigDecimal.ZERO;
                    }
                }
                payoutDetail.setSellerInterestOverduePaid(sellerInterestOverduePaid);
                
                logger.debug("Calculated seller fields for payout detail {} with assign ratio: {}, interest rate: {}, days: {}", 
                    payoutDetail.getId(), assignRatio, annualInterestRate, daysBetween);
                
                return payoutDetail;
            });
    }

    /**
     * Helper method to calculate sellerInterestOverdue reactively
     * - If previous month is empty: from LoanDetail currentAssignedOverdueInterest
     * - If previous month exists: from previous month (sellerTotalInterestDue - sellerTotalInterestComponentPaid)
     */
    private Mono<BigDecimal> calculateSellerInterestOverdueReactive(PartnerPayoutDetailsAll payoutDetail, LoanDetail loanDetail, Integer year, Integer month) {
        // Calculate previous month and year
        final Integer prevMonth;
        final Integer prevYear;
        
        if (month == null) {
            prevMonth = null;
            prevYear = year;
        } else if (month == 1) {
            prevMonth = 12;
            prevYear = year != null ? year - 1 : null;
        } else {
            prevMonth = month - 1;
            prevYear = year;
        }
        
        // Try to get previous month payout detail reactively
        if (payoutDetail.getLmsLan() != null && prevYear != null && prevMonth != null) {
            return partnerPayoutDetailsAllRepository
                .findByLmsLanAndYearAndMonth(payoutDetail.getLmsLan(), prevYear, prevMonth)
                .next()
                .map(previousPayout -> {
                    // Previous month exists - calculate from previous month
                    BigDecimal prevSellerTotalInterestDue = previousPayout.getSellerTotalInterestDue() != null ? 
                        previousPayout.getSellerTotalInterestDue() : BigDecimal.ZERO;
                    BigDecimal prevSellerTotalInterestComponentPaid = previousPayout.getSellerTotalInterestComponentPaid() != null ? 
                        previousPayout.getSellerTotalInterestComponentPaid() : BigDecimal.ZERO;
                    
                    BigDecimal sellerInterestOverdue = prevSellerTotalInterestDue.subtract(prevSellerTotalInterestComponentPaid)
                        .setScale(2, RoundingMode.HALF_UP);
                    
                    // Ensure non-negative
                    if (sellerInterestOverdue.compareTo(BigDecimal.ZERO) < 0) {
                        sellerInterestOverdue = BigDecimal.ZERO;
                    }
                    
                    return sellerInterestOverdue;
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    // No previous month data - use LoanDetail currentAssignedOverdueInterest for first month
                    if (loanDetail != null && loanDetail.getCurrentAssignedOverdueInterest() != null) {
                        return BigDecimal.valueOf(loanDetail.getCurrentAssignedOverdueInterest())
                            .setScale(2, RoundingMode.HALF_UP);
                    }
                    return BigDecimal.ZERO;
                }));
        }
        
        // No previous month data - use LoanDetail currentAssignedOverdueInterest for first month
        return Mono.fromCallable(() -> {
            if (loanDetail != null && loanDetail.getCurrentAssignedOverdueInterest() != null) {
                return BigDecimal.valueOf(loanDetail.getCurrentAssignedOverdueInterest())
                    .setScale(2, RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO;
        });
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