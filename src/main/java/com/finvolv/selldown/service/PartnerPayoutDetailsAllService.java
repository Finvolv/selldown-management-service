package com.finvolv.selldown.service;

import com.finvolv.selldown.dto.OpeningPosDiscrepancy;
import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.InterestRateChange;
import com.finvolv.selldown.model.LoanDetail;
import com.finvolv.selldown.model.MonthlyLMSStatus;
import com.finvolv.selldown.model.MonthlyLMSStatusEntity;
import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import com.finvolv.selldown.repository.DealRepository;
import com.finvolv.selldown.repository.InterestRateChangeRepository;
import com.finvolv.selldown.repository.LoanDetailRepository;
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
import java.time.temporal.ChronoUnit;
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
    private final LoanDetailRepository loanDetailRepository;
    private final InterestRateChangeRepository interestRateChangeRepository;
    
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
                
                // Build efficient lookup maps: LAN -> DealId -> MonthOnMonthDay
                // Load all LoanDetails and Deals once for efficient O(1) lookups
                Mono<Map<String, Integer>> lanToMonthOnMonthDayMap = loanDetailRepository.findAll()
                    .collectList()
                    .flatMap(loanDetails -> {
                        // Create map: lmsLan -> dealId
                        Map<String, Long> lanToDealIdMap = loanDetails.stream()
                            .filter(ld -> ld.getLmsLan() != null && ld.getDealId() != null)
                            .collect(Collectors.toMap(
                                LoanDetail::getLmsLan,
                                LoanDetail::getDealId,
                                (existing, replacement) -> existing // Keep first if duplicates
                            ));
                        
                        logger.debug("Created LAN to DealId map with {} entries", lanToDealIdMap.size());
                        
                                // Load all Deals and create map: dealId -> monthOnMonthDay
                                // Only include deals where monthOnMonthDay is not null (must come from Deal table)
                                return dealRepository.findAll()
                                    .collectList()
                                    .map(deals -> {
                                        Map<Long, Integer> dealIdToMonthOnMonthDayMap = deals.stream()
                                            .filter(d -> d.getId() != null && d.getMonthOnMonthDay() != null)
                                            .collect(Collectors.toMap(
                                                Deal::getId,
                                                Deal::getMonthOnMonthDay,
                                                (existing, replacement) -> existing
                                            ));
                                        
                                        logger.debug("Created DealId to MonthOnMonthDay map with {} entries (only deals with monthOnMonthDay set)", 
                                            dealIdToMonthOnMonthDayMap.size());
                                        
                                        // Combine maps: lmsLan -> monthOnMonthDay
                                        // Only include entries where monthOnMonthDay exists in Deal table
                                        Map<String, Integer> result = lanToDealIdMap.entrySet().stream()
                                            .filter(entry -> dealIdToMonthOnMonthDayMap.containsKey(entry.getValue()))
                                            .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                entry -> dealIdToMonthOnMonthDayMap.get(entry.getValue()),
                                                (existing, replacement) -> existing
                                            ));
                                        
                                        logger.debug("Created final LAN to MonthOnMonthDay map with {} entries (all from Deal table)", result.size());
                                        return result;
                                    });
                    });
                
                // Use the lookup map to prepare payout details reactively
                return lanToMonthOnMonthDayMap
                    .flatMapMany(monthOnMonthDayMap -> {
                        logger.info("Lookup map contains {} entries. Processing {} payout details.", 
                            monthOnMonthDayMap.size(), payoutDetails.size());
                        
                        // Process each payout detail reactively to ensure monthOnMonthDay is always found
                        return Flux.fromIterable(payoutDetails)
                            .flatMap(payout -> {
                                // Set the LMS ID and current timestamp
                                payout.setLmsId(lmsStatusId);
                                payout.setCreatedAt(LocalDateTime.now());
                                payout.setModifiedAt(LocalDateTime.now());
                                
                                // Get monthOnMonthDay from map
                                Integer monthOnMonthDay = monthOnMonthDayMap.get(payout.getLmsLan());
                                
                                // If not found in map, look it up directly from Deal table
                                Mono<Integer> monthOnMonthDayMono;
                                if (monthOnMonthDay != null) {
                                    monthOnMonthDayMono = Mono.just(monthOnMonthDay);
                                    logger.debug("Found monthOnMonthDay={} for LAN {} in lookup map (from Deal table)", monthOnMonthDay, payout.getLmsLan());
                                } else {
                                    logger.warn("monthOnMonthDay not found in lookup map for LAN: {}. Attempting direct lookup from Deal table.", payout.getLmsLan());
                                    // Look up directly from Deal table: find LoanDetail by LAN, then get Deal, then get monthOnMonthDay
                                    monthOnMonthDayMono = loanDetailRepository.findAll()
                                        .filter(ld -> payout.getLmsLan() != null && payout.getLmsLan().equals(ld.getLmsLan()))
                                        .next()
                                        .switchIfEmpty(Mono.defer(() -> {
                                            logger.warn("No LoanDetail found for LAN: {}. Skipping this record.", payout.getLmsLan());
                                            return Mono.empty();
                                        }))
                                        .flatMap(loanDetail -> {
                                            if (loanDetail.getDealId() == null) {
                                                logger.warn("LoanDetail for LAN {} has no dealId. Skipping this record.", payout.getLmsLan());
                                                return Mono.empty();
                                            }
                                            return dealRepository.findById(loanDetail.getDealId())
                                                .switchIfEmpty(Mono.defer(() -> {
                                                    logger.warn("Deal with ID {} not found for LAN {}. Skipping this record.", 
                                                        loanDetail.getDealId(), payout.getLmsLan());
                                                    return Mono.empty();
                                                }))
                                                .map(deal -> {
                                                    Integer momDay = deal.getMonthOnMonthDay();
                                                    if (momDay == null) {
                                                        logger.warn("monthOnMonthDay is null in Deal {} for LAN {}. Skipping this record.", 
                                                            deal.getId(), payout.getLmsLan());
                                                        return null;
                                                    }
                                                    logger.info("Retrieved monthOnMonthDay={} from Deal table for LAN {} (Deal {})", 
                                                        momDay, payout.getLmsLan(), deal.getId());
                                                    return momDay;
                                                })
                                                .filter(momDay -> momDay != null);
                                        });
                                }
                                
                                return monthOnMonthDayMono.map(momDay -> {
                                    // Set cycle dates if not already set, using monthOnMonthDay
                                    // Start date: previous month's day (e.g., if monthOnMonthDay=20 and month=9, start = Aug 20)
                                    // End date: current month's day (e.g., if monthOnMonthDay=20 and month=9, end = Sep 20)
                                    if (payout.getCycleStartDate() == null || payout.getCycleEndDate() == null) {
                                        // Calculate previous month and year
                                        int prevMonth = (month == 1) ? 12 : month - 1;
                                        int prevYear = (month == 1) ? year - 1 : year;
                                        
                                        // Ensure the day is valid for the previous month
                                        LocalDate prevMonthDate = LocalDate.of(prevYear, prevMonth, 1);
                                        int maxDayInPrevMonth = prevMonthDate.lengthOfMonth();
                                        int dayToUseStart = Math.min(momDay, maxDayInPrevMonth);
                                        
                                        // Ensure the day is valid for the current month
                                        LocalDate currentMonthDate = LocalDate.of(year, month, 1);
                                        int maxDayInCurrentMonth = currentMonthDate.lengthOfMonth();
                                        int dayToUseEnd = Math.min(momDay, maxDayInCurrentMonth);
                                        
                                        if (payout.getCycleStartDate() == null) {
                                            payout.setCycleStartDate(LocalDate.of(prevYear, prevMonth, dayToUseStart));
                                        }
                                        if (payout.getCycleEndDate() == null) {
                                            payout.setCycleEndDate(LocalDate.of(year, month, dayToUseEnd));
                                        }
                                        
                                        logger.info("Set cycle dates for LAN {}: start={}, end={}, monthOnMonthDay={}", 
                                            payout.getLmsLan(), payout.getCycleStartDate(), payout.getCycleEndDate(), momDay);
                                    }
                                    
                                    // Ensure isOpeningPosMisMatch is never null (default to false)
                                    if (payout.getIsOpeningPosMisMatch() == null) {
                                        payout.setIsOpeningPosMisMatch(false);
                                    }
                                    
                                    return payout;
                                });
                            })
                            .collectList();
                    })
                    .flatMap(preparedPayoutDetails -> 
                        // Save all payout details in a single transaction (update if exists, create if not)
                        saveAllPayoutDetails(preparedPayoutDetails, lmsStatusId)
                            .doOnComplete(() -> 
                                logger.info("Successfully uploaded LMS file - year: {}, month: {}, records: {}", 
                                    year, month, payoutDetails.size()))
                            .doOnError(error -> 
                                logger.error("Error uploading LMS file - year: {}, month: {}: {}", 
                                    year, month, error.getMessage()))
                    );
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
        Deal.InterestMethod interestMethod = deal.getInterestMethod();
        
        // Calculate days between cycle start and end date (make it final for lambda usage)
        final long daysBetween = (payoutDetail.getCycleStartDate() != null && payoutDetail.getCycleEndDate() != null) ?
            java.time.temporal.ChronoUnit.DAYS.between(
                payoutDetail.getCycleStartDate(), 
                payoutDetail.getCycleEndDate()
            ) : 0L;

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
       // payoutDetail.setSellerTotalPaid(calculateValue(payoutDetail.getTotalPaid(), assignRatio));

        // Calculate interest based on seller opening position, interest rate, and interest method
        // Calculate interest for each rate period separately and sum them (not using weighted average)
        BigDecimal baseAmount = payoutDetail.getSellerOpeningPos() != null && payoutDetail.getSellerPrincipalOverdue() != null ?
            payoutDetail.getSellerOpeningPos().subtract(payoutDetail.getSellerPrincipalOverdue()) : BigDecimal.ZERO;
        
        return calculateInterestByRatePeriodsReactive(deal.getId(), baseAmount, payoutDetail.getCycleStartDate(), payoutDetail.getCycleEndDate(), interestMethod, annualInterestRate)
            .switchIfEmpty(Mono.defer(() -> {
                // Fall back to deal's annual interest rate if no rate changes
                if (baseAmount.compareTo(BigDecimal.ZERO) > 0 && daysBetween > 0) {
                    Double interestMultiplier = calculateInterestMultiplier(
                        interestMethod, 
                        payoutDetail.getCycleStartDate(), 
                        payoutDetail.getCycleEndDate(),
                        daysBetween
                    );
                    BigDecimal calculatedInterest = baseAmount
                        .multiply(BigDecimal.valueOf(annualInterestRate))
                        .multiply(BigDecimal.valueOf(interestMultiplier))
                        .setScale(2, RoundingMode.HALF_UP);
                    return Mono.just(calculatedInterest);
                }
                return Mono.just(BigDecimal.ZERO);
            }))
            .flatMap(calculatedInterest -> {
                
                // Pass calculatedInterest through the reactive chain
                // First, get sellerInterestOverdueSplit from previous month or loan details
                return getSellerInterestOverdueSplitReactive(payoutDetail, loanDetail, year, month)
                    .flatMap(splitArray -> {
                        // Set the split array (from previous month or loan details)
                        payoutDetail.setSellerInterestOverdueSplit(splitArray);
                        
                        // If array is empty and DPD has a value, add calculated interest to the array
                        Integer closingDpd = payoutDetail.getClosingDpd() != null ? payoutDetail.getClosingDpd() : 0;
                        if ((splitArray == null || splitArray.isEmpty()) && closingDpd != null && closingDpd > 0) {
                            // Create a new ArrayList and add the calculated interest
                            List<BigDecimal> newSplitArray = new java.util.ArrayList<>();
                            newSplitArray.add(calculatedInterest);
                            payoutDetail.setSellerInterestOverdueSplit(newSplitArray);
                        }
                        
                        // Now calculate sellerInterestOverdue
                        return calculateSellerInterestOverdueReactive(payoutDetail, loanDetail, year, month)
                            .map(sellerInterestOverdue -> {
                                payoutDetail.setSellerInterestOverdue(sellerInterestOverdue);
                                
                                // Calculate sellerTotalInterestDue = calculatedInterest + sellerInterestOverdue
                                BigDecimal sellerTotalInterestDue = calculatedInterest.add(sellerInterestOverdue)
                                    .setScale(2, RoundingMode.HALF_UP);
                                payoutDetail.setSellerTotalInterestDue(sellerTotalInterestDue);
                                
                                // Get values needed for calculations
                                BigDecimal totalInterestComponentPaid = payoutDetail.getTotalInterestComponentPaid() != null ? 
                                    payoutDetail.getTotalInterestComponentPaid() : BigDecimal.ZERO;
                                BigDecimal interestOverduePaid = payoutDetail.getInterestOverduePaid() != null ? 
                                    payoutDetail.getInterestOverduePaid() : BigDecimal.ZERO;

                                
                                // Calculate sellerInterestOverduePaid based on DPD and interest overdue split:
                                // If sellerInterestOverdue > 0 and split array is available:
                                //   - DPD 0-30: use array[1] (second value, 0-indexed)
                                //   - DPD 31-60: use array[2] (third value, 0-indexed)
                                BigDecimal sellerInterestOverduePaid = BigDecimal.ZERO;
                                Integer closingDpdForCalculation = payoutDetail.getClosingDpd() != null ? payoutDetail.getClosingDpd() : 0;
                                
                                if (sellerInterestOverdue.compareTo(BigDecimal.ZERO) > 0) {
                                    // Use the split array that was set above (from previous month or loan details, or newly created)
                                    List<BigDecimal> splitArrayForCalculation = payoutDetail.getSellerInterestOverdueSplit();
                                    if (splitArrayForCalculation != null && !splitArrayForCalculation.isEmpty()) {
                                        if (closingDpdForCalculation == 0) {
                                            // DPD is 0: take all values from array and sum them up
                                            sellerInterestOverduePaid = splitArrayForCalculation.stream()
                                                .filter(val -> val != null)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                                            
                                            // Clear the array (remove all values)
                                            splitArrayForCalculation.clear();
                                            
                                            // Add the calculated interest to the array
                                            splitArrayForCalculation.add(calculatedInterest);
                                            
                                            // Update the array back to the payout detail
                                            payoutDetail.setSellerInterestOverdueSplit(splitArrayForCalculation);
                                        } else {
                                            int indexToUse = -1;
                                            if (closingDpdForCalculation >= 1 && closingDpdForCalculation <= 30) {
                                                // DPD 1-30: use first value (index 0)
                                                indexToUse = 0;
                                            } else if (closingDpdForCalculation >= 31 && closingDpdForCalculation <= 60) {
                                                // DPD 31-60: use second value (index 1)
                                                indexToUse = 1;
                                            }
                                            else if (closingDpdForCalculation >= 61 && closingDpdForCalculation <= 90) {
                                                // DPD 61-90: use third value (index 2)
                                                indexToUse = 2;
                                            }
                                            
                                            // If we can use a value from the array, get it and remove it
                                            if (indexToUse >= 0 && indexToUse < splitArrayForCalculation.size()) {
                                                // Get the value from the array
                                                sellerInterestOverduePaid = splitArrayForCalculation.get(indexToUse) != null ? 
                                                    splitArrayForCalculation.get(indexToUse) : BigDecimal.ZERO;
                                                
                                                // Remove the used value from the array
                                                splitArrayForCalculation.remove(indexToUse);
                                            }
                                            
                                            // Always add the calculated interest to the array (whether we used a value or not)
                                            splitArrayForCalculation.add(calculatedInterest);
                                            
                                            // Update the array back to the payout detail
                                            payoutDetail.setSellerInterestOverdueSplit(splitArrayForCalculation);
                                        }
                                    } else {
                                        // Fallback to original logic if split array is not available
                                        if (totalInterestComponentPaid.compareTo(BigDecimal.ZERO) > 0 && 
                                            interestOverduePaid.compareTo(BigDecimal.ZERO) > 0 && 
                                            sellerInterestOverdue.compareTo(interestOverduePaid) < 0) {
                                            sellerInterestOverduePaid = sellerInterestOverdue;
                                        }
                                    }
                                }
                                payoutDetail.setSellerInterestOverduePaid(sellerInterestOverduePaid);
                                
                                // Calculate sellerTotalInterestComponentPaid:
                                BigDecimal sellerTotalInterestComponentPaid = BigDecimal.ZERO;
                                
                                if (totalInterestComponentPaid.compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal interestAfterPrincipal = totalInterestComponentPaid.subtract(interestOverduePaid);

                                    if (interestAfterPrincipal.compareTo(new BigDecimal("100")) > 0 && 
                                    interestAfterPrincipal.compareTo(calculatedInterest) > 0) {
                                        sellerTotalInterestComponentPaid = calculatedInterest.add(sellerInterestOverduePaid)
                                            .setScale(4, RoundingMode.HALF_UP);
                                    }
                                    else{
                                        sellerTotalInterestComponentPaid = (sellerInterestOverduePaid)
                                                .setScale(4, RoundingMode.HALF_UP);
                                    }
                                }
                                // Log the value before setting
                                payoutDetail.setSellerTotalInterestComponentPaid(sellerTotalInterestComponentPaid);

                                //Adding Total paid
                                BigDecimal totalPaid = payoutDetail.getSellerTotalPrincipalComponentPaid().add(payoutDetail.getSellerTotalInterestComponentPaid()).add(payoutDetail.getSellerTotalChargesPaid().add(payoutDetail.getSellerPrepaymentPaid()).add(payoutDetail.getSellerForeclosurePaid()));
                                payoutDetail.setSellerTotalPaid(totalPaid);

                                logger.debug("Calculated seller fields for payout detail {} with assign ratio: {}, interest rate: {}, days: {}, calculatedInterest: {}, sellerInterestOverdue: {}, sellerTotalInterestDue: {}", 
                                    payoutDetail.getId(), assignRatio, annualInterestRate, daysBetween, calculatedInterest, sellerInterestOverdue, sellerTotalInterestDue);
                                
                                return payoutDetail;
                            });
                    });
            });
    }

    /**
     * Helper method to get sellerInterestOverdueSplit reactively
     * Logic:
     * - If previous month exists: use sellerInterestOverdueSplit from previous month
     * - If no previous month: use assignedInterestOverdueSplit from loan details
     */
    private Mono<List<BigDecimal>> getSellerInterestOverdueSplitReactive(PartnerPayoutDetailsAll payoutDetail, LoanDetail loanDetail, Integer year, Integer month) {
        if (payoutDetail.getLmsLan() == null) {
            return Mono.just(java.util.Collections.emptyList());
        }
        
        // Calculate previous month and year
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;
        
        // Helper method to get loan detail by LAN if not provided
        Mono<LoanDetail> loanDetailMono;
        if (loanDetail != null) {
            loanDetailMono = Mono.just(loanDetail);
        } else {
            // Look up loan detail by LAN if not provided
            loanDetailMono = loanDetailRepository.findAll()
                .filter(ld -> payoutDetail.getLmsLan().equals(ld.getLmsLan()))
                .next()
                .switchIfEmpty(Mono.empty());
        }
        
        // Find previous month's lmsId first
        return monthlyLMSStatusRepository.findByYearAndMonth(prevYear, prevMonth)
            .map(MonthlyLMSStatusEntity::getId)
            .flatMap(prevLmsId -> {
                // Found previous month's lmsId - now get the specific entry for this LAN
                return partnerPayoutDetailsAllRepository.findByLmsIdAndLmsLan(prevLmsId, payoutDetail.getLmsLan())
                    .map(previousEntry -> {
                        // Previous month entry found - use its sellerInterestOverdueSplit
                        List<BigDecimal> prevSplit = previousEntry.getSellerInterestOverdueSplit();
                        if (prevSplit != null && !prevSplit.isEmpty()) {
                            return prevSplit;
                        }
                        // If previous month has empty/null split, return empty list to trigger fallback
                        return java.util.Collections.<BigDecimal>emptyList();
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        // Previous month lmsId exists but no entry for this LAN - fall back to loan details
                        return Mono.just(java.util.Collections.<BigDecimal>emptyList());
                    }));
            })
            .switchIfEmpty(Mono.defer(() -> {
                // No previous month - fall back to loan details
                return Mono.just(java.util.Collections.<BigDecimal>emptyList());
            }))
            .flatMap(splitFromPrevMonth -> {
                if (splitFromPrevMonth != null && !splitFromPrevMonth.isEmpty()) {
                    return Mono.just(splitFromPrevMonth);
                }
                // Fall back to loan details
                return loanDetailMono
                    .map(ld -> {
                        if (ld.getAssignedInterestOverdueSplit() != null && !ld.getAssignedInterestOverdueSplit().isEmpty()) {
                            return ld.getAssignedInterestOverdueSplit();
                        }
                        return java.util.Collections.<BigDecimal>emptyList();
                    })
                    .switchIfEmpty(Mono.just(java.util.Collections.<BigDecimal>emptyList()));
            });
    }

    /**
     * Helper method to calculate sellerInterestOverdue reactively
     * Logic:
     * - If this is the first month (no previous month LMS status): use loan details data (currentAssignedOverdueInterest)
     * - If previous month data exists: calculate from previous month's unpaid interest
     *   sellerInterestOverdue = previousMonth.sellerTotalInterestDue - previousMonth.sellerTotalInterestComponentPaid
     */
    private Mono<BigDecimal> calculateSellerInterestOverdueReactive(PartnerPayoutDetailsAll payoutDetail, LoanDetail loanDetail, Integer year, Integer month) {
        if (payoutDetail.getLmsLan() == null) {
            return Mono.just(BigDecimal.ZERO);
        }
        
        // Calculate previous month and year
        int prevMonth = (month == 1) ? 12 : month - 1;
        int prevYear = (month == 1) ? year - 1 : year;
        
        // Helper method to get loan detail by LAN if not provided
        Mono<LoanDetail> loanDetailMono;
        if (loanDetail != null) {
            loanDetailMono = Mono.just(loanDetail);
        } else {
            // Look up loan detail by LAN if not provided
            loanDetailMono = loanDetailRepository.findAll()
                .filter(ld -> payoutDetail.getLmsLan().equals(ld.getLmsLan()))
                .next()
                .switchIfEmpty(Mono.empty());
        }
        
        // Find previous month's lmsId first (same approach as opening position mismatch detection)
        return monthlyLMSStatusRepository.findByYearAndMonth(prevYear, prevMonth)
            .map(MonthlyLMSStatusEntity::getId)
            .flatMap(prevLmsId -> {
                // Found previous month's lmsId - now get the specific entry for this LAN
                return partnerPayoutDetailsAllRepository.findByLmsIdAndLmsLan(prevLmsId, payoutDetail.getLmsLan())
                    .flatMap(previousEntry -> {
                        // Previous month entry found - calculate from it
                        BigDecimal prevSellerTotalInterestDue = previousEntry.getSellerTotalInterestDue() != null ? 
                            previousEntry.getSellerTotalInterestDue() : BigDecimal.ZERO;
                        BigDecimal prevSellerTotalInterestComponentPaid = previousEntry.getSellerTotalInterestComponentPaid() != null ? 
                            previousEntry.getSellerTotalInterestComponentPaid() : BigDecimal.ZERO;
                        
                        // Calculate unpaid interest: sellerTotalInterestDue - sellerTotalInterestComponentPaid
                        // This is what was due but not paid, which becomes overdue
                        BigDecimal sellerInterestOverdue = prevSellerTotalInterestDue.subtract(prevSellerTotalInterestComponentPaid)
                            .setScale(2, RoundingMode.HALF_UP);
                        
                        // Ensure non-negative (if overpaid, overdue is 0)
                        if (sellerInterestOverdue.compareTo(BigDecimal.ZERO) < 0) {
                            sellerInterestOverdue = BigDecimal.ZERO;
                        }
                        
                        return Mono.just(sellerInterestOverdue);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        // Previous month lmsId exists but no entry for this LAN - fall back to loan details
                        return loanDetailMono
                            .map(ld -> {
                                if (ld.getCurrentAssignedOverdueInterest() != null) {
                                    return BigDecimal.valueOf(ld.getCurrentAssignedOverdueInterest())
                                        .setScale(2, RoundingMode.HALF_UP);
                                }
                                return BigDecimal.ZERO;
                            })
                            .switchIfEmpty(Mono.just(BigDecimal.ZERO));
                    }));
            })
            .switchIfEmpty(
                // No previous month LMS status found - this is the first month, use loan details data
                loanDetailMono
                    .map(ld -> {
                        if (ld.getCurrentAssignedOverdueInterest() != null) {
                            return BigDecimal.valueOf(ld.getCurrentAssignedOverdueInterest())
                                .setScale(2, RoundingMode.HALF_UP);
                        }
                        return BigDecimal.ZERO;
                    })
                    .switchIfEmpty(Mono.just(BigDecimal.ZERO))
            );
    }

    /**
     * Calculate interest multiplier based on InterestMethod
     * Uses daysBetween (cycleEndDate - cycleStartDate) for actual day calculations
     */
    private Double calculateInterestMultiplier(Deal.InterestMethod interestMethod, LocalDate startDate, LocalDate endDate, long daysBetween) {
        if (interestMethod == null) {
            // Default fallback to ACTUAL_BY_360 if method is null
            return daysBetween / 360.0;
        }

        switch (interestMethod) {
            case ONE_TWELFTH:
                return 1.0 / 12.0;
                
            case ACTUAL_BY_360:
                return daysBetween / 360.0;
                
            case ACTUAL_BY_365:
                return daysBetween / 365.0;
                
            case ACTUAL_BY_ACTUAL:
                // Use 366 for leap year, 365 otherwise
                // Use the start date to determine if it's a leap year
                int actualDaysInYear = (startDate != null && startDate.isLeapYear()) ? 366 : 365;
                return daysBetween / (double) actualDaysInYear;
                
            default:
                // Default fallback to ACTUAL_BY_360
                return daysBetween / 360.0;
        }
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
        
        // Calculate previous month and year to check if we should look for previous month data
        final Integer prevMonth;
        final Integer prevYear;
        
        if (month == 1) {
            prevMonth = 12;
            prevYear = year - 1;
        } else {
            prevMonth = month - 1;
            prevYear = year;
        }
        
        // Get the previous month's lmsId to check if previous month data exists
        logger.info("Checking for previous month data: year={}, month={}, prevYear={}, prevMonth={}", year, month, prevYear, prevMonth);
        Mono<Long> prevMonthLmsIdMono = monthlyLMSStatusRepository.findByYearAndMonth(prevYear, prevMonth)
            .map(MonthlyLMSStatusEntity::getId)
            .doOnNext(lmsId -> logger.info("Found previous month LMS status with lmsId: {} for year={}, month={}", lmsId, prevYear, prevMonth))
            .switchIfEmpty(Mono.defer(() -> {
                logger.warn("No previous month LMS status found for year={}, month={}. This might be the first month.", prevYear, prevMonth);
                return Mono.empty();
            }));
        
        // Process each payout detail reactively
        return prevMonthLmsIdMono
            .flatMap(prevLmsId -> {
                // Fetch all previous month payout details once by lmsId
                logger.info("Fetching all previous month payout details for lmsId: {}", prevLmsId);
                return partnerPayoutDetailsAllRepository.findByLmsId(prevLmsId)
                    .collectList()
                    .map(prevMonthPayouts -> {
                        // Create a map of lmsLan -> closingPos
                        Map<String, BigDecimal> prevMonthClosingPosMap = new java.util.HashMap<>();
                        for (PartnerPayoutDetailsAll payout : prevMonthPayouts) {
                            if (payout.getLmsLan() != null && payout.getClosingPos() != null) {
                                // Keep first if duplicates (don't overwrite)
                                prevMonthClosingPosMap.putIfAbsent(payout.getLmsLan(), payout.getClosingPos());
                            }
                        }
                        logger.info("Loaded {} previous month closing positions into map from {} payout records", 
                            prevMonthClosingPosMap.size(), prevMonthPayouts.size());
                        return prevMonthClosingPosMap;
                    })
                    .flatMap(prevMonthClosingPosMap -> {
                        // Previous month data exists - check all payouts against previous month closing position
                        logger.info("Previous month data found (lmsId: {}), checking opening positions against previous month closing positions for {} payouts", 
                            prevLmsId, payoutDetails.size());
                        
                        List<OpeningPosDiscrepancy> discrepancies = new java.util.ArrayList<>();
                        
                        for (PartnerPayoutDetailsAll payout : payoutDetails) {
                            if (payout.getLmsLan() == null) {
                                continue;
                            }
                            
                            LoanDetail loanDetail = loanDetailMap.get(payout.getLmsLan());
                            if (loanDetail == null) {
                                discrepancies.add(OpeningPosDiscrepancy.builder()
                                    .lmsLan(payout.getLmsLan())
                                    .payoutOpeningPos(payout.getOpeningPos())
                                    .loanDetailOpeningPos(null)
                                    .difference(BigDecimal.ZERO)
                                    .isMismatch(false)
                                    .discrepancyType("NO_LOAN_DETAIL")
                                    .description("No loan detail found")
                                    .build());
                                continue;
                            }
                            
                            // For 2nd month and later, always check previous month closing position vs current month opening position
                            // Look up from the map we created
                            BigDecimal prevClosingPos = prevMonthClosingPosMap.get(payout.getLmsLan());
                            
                            if (prevClosingPos == null) {
                                // If no previous month data found for this LAN in month 2+, it's a data issue
                                logger.warn("No previous month closing position found for LAN {} in map. Using ZERO as expected.", 
                                    payout.getLmsLan());
                                BigDecimal expectedOpeningPos = BigDecimal.ZERO;
                                String discrepancyType = "PREVIOUS_MONTH_MISSING";
                                String description = String.format("Previous month closing position not found for LAN %s. Expected opening position from previous month closing, but no previous month data exists.", payout.getLmsLan());
                                boolean isMismatch = true; // This is a mismatch because we can't validate
                                OpeningPosDiscrepancy discrepancy = processComparison(payout, expectedOpeningPos, discrepancyType, description, isMismatch);
                                if (discrepancy.isMismatch()) {
                                    discrepancies.add(discrepancy);
                                }
                                continue;
                            }
                            
                            // Compare: Previous month's closing position (expected) vs Current month's opening position (actual)
                            BigDecimal expectedOpeningPos = prevClosingPos;
                            String discrepancyType = "PREVIOUS_MONTH";
                            String description = "";
                            boolean isMismatch = false;
                            
                            logger.debug("Comparing for LAN {}: Current opening={}, Previous closing={}", 
                                payout.getLmsLan(), payout.getOpeningPos(), expectedOpeningPos);
                            
                            OpeningPosDiscrepancy discrepancy = processComparison(payout, expectedOpeningPos, discrepancyType, description, isMismatch);
                            
                            if (discrepancy.isMismatch()) {
                                logger.warn("Opening position mismatch for LAN {}: Payout={}, Expected={}, Difference={}", 
                                    discrepancy.getLmsLan(), discrepancy.getPayoutOpeningPos(), 
                                    discrepancy.getLoanDetailOpeningPos(), discrepancy.getDifference());
                                discrepancies.add(discrepancy);
                            }
                        }
                        
                        return Mono.just(discrepancies);
                    });
            })
            .switchIfEmpty(
                // No previous month data exists - this is the first month, use loan details
                Mono.fromCallable(() -> {
                    List<OpeningPosDiscrepancy> discrepancies = new java.util.ArrayList<>();
                    
                    for (PartnerPayoutDetailsAll payout : payoutDetails) {
                        if (payout.getLmsLan() == null) {
                            continue;
                        }
                        
                        LoanDetail loanDetail = loanDetailMap.get(payout.getLmsLan());
                        if (loanDetail == null) {
                            discrepancies.add(OpeningPosDiscrepancy.builder()
                                .lmsLan(payout.getLmsLan())
                                .payoutOpeningPos(payout.getOpeningPos())
                                .loanDetailOpeningPos(null)
                                .difference(BigDecimal.ZERO)
                                .isMismatch(false)
                                .discrepancyType("NO_LOAN_DETAIL")
                                .description("No loan detail found")
                                .build());
                            continue;
                        }
                        
                        // First month - use loan details current position
                        BigDecimal expectedOpeningPos = loanDetail.getCurrentPOS() != null ?
                            BigDecimal.valueOf(loanDetail.getCurrentPOS()) : BigDecimal.ZERO;
                        String discrepancyType = "CURRENT_MONTH";
                        String description = "";
                        boolean isMismatch = false;
                        
                        OpeningPosDiscrepancy discrepancy = processComparison(payout, expectedOpeningPos, discrepancyType, description, isMismatch);
                        if (discrepancy.isMismatch()) {
                            discrepancies.add(discrepancy);
                        }
                    }
                    
                    return discrepancies;
                })
            );
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
     * Calculate interest by calculating for each rate period separately and summing them
     * Uses cycle_start_date and cycle_end_date, and respects the interest method from deal table
     * If there are gaps in rate coverage, uses default rate for uncovered days
     */
    private Mono<BigDecimal> calculateInterestByRatePeriodsReactive(Long dealId, BigDecimal baseAmount, LocalDate startDate, LocalDate endDate, Deal.InterestMethod interestMethod, Double defaultRate) {
        if (dealId == null || startDate == null || endDate == null || baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.empty();
        }
        
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (totalDays <= 0) {
            return Mono.empty();
        }
        
        return interestRateChangeRepository.findByDealId(dealId)
            .collectList()
            .flatMap(rateTable -> {
                if (rateTable == null || rateTable.isEmpty()) {
                    return Mono.empty();
                }
                
                // Calculate interest for each rate period separately and sum them
                BigDecimal totalInterest = BigDecimal.ZERO;
                long totalOverlapDays = 0;
                
                for (InterestRateChange entry : rateTable) {
                    LocalDate rateStart = entry.getStartDate();
                    if (rateStart == null) {
                        continue;
                    }
                    
                    LocalDate rateEnd = entry.getEndDate() != null ? entry.getEndDate() : LocalDate.MAX;
                    
                    // Calculate overlap between cycle period and rate period
                    LocalDate overlapStart = rateStart.isAfter(startDate) ? rateStart : startDate;
                    LocalDate overlapEnd = rateEnd.isBefore(endDate) ? rateEnd : endDate;
                    
                    if (overlapStart.isAfter(overlapEnd)) {
                        continue;
                    }
                    
                    // Calculate overlap days: ChronoUnit.DAYS.between is exclusive of end date
                    // This matches how daysBetween is calculated for the cycle period
                    long overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd);
                    
                    if (overlapDays > 0) {
                        double interestRate = entry.getInterestRate() != null ? entry.getInterestRate() : 0.0;
                        
                        // Calculate interest multiplier for this period using the interest method
                        Double periodMultiplier = calculateInterestMultiplier(interestMethod, overlapStart, overlapEnd, overlapDays);
                        
                        // Calculate interest for this period: baseAmount * rate * multiplier
                        // This uses the same logic as the main calculation
                        BigDecimal periodInterest = baseAmount
                            .multiply(BigDecimal.valueOf(interestRate))
                            .multiply(BigDecimal.valueOf(periodMultiplier))
                            .setScale(10, RoundingMode.HALF_UP);
                        
                        totalInterest = totalInterest.add(periodInterest);
                        totalOverlapDays += overlapDays;
                    }
                }
                
                // If there are gaps in rate coverage, use default rate for uncovered days
                if (totalOverlapDays < totalDays && defaultRate != null) {
                    long uncoveredDays = totalDays - totalOverlapDays;
                    
                    // Calculate interest multiplier for uncovered days using the interest method
                    Double uncoveredMultiplier = calculateInterestMultiplier(interestMethod, startDate, endDate, uncoveredDays);
                    
                    BigDecimal uncoveredInterest = baseAmount
                        .multiply(BigDecimal.valueOf(defaultRate))
                        .multiply(BigDecimal.valueOf(uncoveredMultiplier))
                        .setScale(10, RoundingMode.HALF_UP);
                    
                    totalInterest = totalInterest.add(uncoveredInterest);
                    logger.debug("Added interest for {} uncovered days using default rate {}: {}", uncoveredDays, defaultRate, uncoveredInterest);
                }
                
                if (totalInterest.compareTo(BigDecimal.ZERO) > 0) {
                    return Mono.just(totalInterest.setScale(2, RoundingMode.HALF_UP));
                }
                
                return Mono.empty();
            });
    }
    
}