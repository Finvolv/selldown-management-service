package com.finvolv.selldown.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.LoanDetail;
import com.finvolv.selldown.model.MonthlyDealProcessingStatus;
import com.finvolv.selldown.repository.DealRepository;
import com.finvolv.selldown.repository.LoanDetailRepository;
import com.finvolv.selldown.repository.MonthlyDealProcessingStatusRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanDetailService {
    
    private static final Logger logger = LoggerFactory.getLogger(LoanDetailService.class);
    
    private final LoanDetailRepository loanDetailRepository;
    private final DealRepository dealRepository;
    private final MonthlyDealProcessingStatusRepository monthlyDealProcessingStatusRepository;
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoanDetailInputForDeal(
        Double currentPOS,
        String lmsLAN,
        LoanDetail.LoanStatus status,
        Double currentInterestRateInPercent,
        Double currentOverdueInterest,
        Double currentAssignedOverdueInterest,
        LoanDetail.LoanType loanType,
        Integer loanStartedDate,
        Integer loanAge,
        Double currentInterestRate,
        LoanDetail.LoanSource source,
        java.util.List<java.math.BigDecimal> assignedInterestOverdueSplit
    ) {
        public LoanDetailInputForDeal {
            if (lmsLAN == null || currentPOS == null || currentInterestRate == null || status == null) {
                throw new IllegalArgumentException("Required fields cannot be null");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoanDetailInputForPartner(
        String dealName,
        String lmsLAN,
        Double currentPOS,
        Double currentInterestRate,
        Double assignedInterestRateOverride,
        LoanDetail.LoanStatus status,
        Double currentAssignedOverdueInterest,
        Integer currentDpd,
        LoanDetail.LoanType loanType,
        String loanStartedDate,
        LoanDetail.LoanSource source,
        Integer loanAge,
        java.util.List<java.math.BigDecimal> assignedInterestOverdueSplit
    ) {
        public LoanDetailInputForPartner {
            if (dealName == null || lmsLAN == null || currentPOS == null || 
                currentInterestRate == null || status == null) {
                throw new IllegalArgumentException("Required fields cannot be null");
            }
        }
    }

    public record LoanDetailModification(
        String lmsLAN,
        Double currentPOS,
        Double currentInterestRate,
        Double assignedInterestRateOverride,
        Double currentAssignedOverdueInterest,
        Integer currentDpd,
        LoanDetail.LoanStatus status,
        LoanDetail.LoanType loanType,
        String loanStartedDate,
        LoanDetail.LoanSource source,
        Integer loanAge
    ) {}

    @Transactional
    public Flux<LoanDetail> bulkCreateLoanDetailsForDeal(Long dealId, Long partnerId, List<LoanDetailInputForDeal> inputs) {
        logger.info("Bulk create request - dealId: {}, partnerId: {}, number of loans: {}", 
            dealId, partnerId, inputs.size());
        logger.debug("Bulk create input details: {}", inputs);

        return dealRepository.findById(dealId)
            .flatMapMany(deal -> {
                double assignedPercentage = deal.getAssignRatio();
                logger.debug("Retrieved assigned percentage: {} for dealId: {}", assignedPercentage, dealId);
                return Flux.fromIterable(inputs)
                    .map(input -> createLoanDetail(dealId, partnerId, input, assignedPercentage))
                    .flatMap(loanDetailRepository::save)
                    .doOnComplete(() -> 
                        logger.info("Bulk create completed for dealId: {}, partnerId: {}", dealId, partnerId))
                    .doOnError(error -> 
                        logger.error("Error in bulk create for dealId: {}, partnerId: {}: {}", 
                            dealId, partnerId, error.getMessage()));
            });
    }

    @Transactional
    public Flux<LoanDetail> bulkCreateLoanDetailsForPartner(Long partnerId, List<LoanDetailInputForPartner> inputs, boolean removeExistingLoanDetails) {
        logger.info("Bulk create request - partnerId: {}, number of loans: {}", 
            partnerId, inputs.size());
        logger.debug("Bulk create input details: {}", inputs);
        
        return dealRepository.findByCustomerId(partnerId)
            .collectList()
            .flatMapMany(dealList -> {
                if(removeExistingLoanDetails) {
                    return loanDetailRepository.deleteByPartnerId(partnerId)
                        .thenMany(Flux.fromIterable(dealList));
                }
                return Flux.fromIterable(dealList);
            })
            .collectList()
            .flatMapMany(dealList -> {
                Map<String, Deal> dealMap = dealList.stream()
                    .collect(Collectors.toMap(Deal::getName, d -> d));
                return Flux.fromIterable(inputs)
                    .map(input -> {
                        Deal deal = dealMap.get(input.dealName());
                        if(deal == null) {
                            throw new IllegalArgumentException("Deal not found for dealName: " + input.dealName());
                        }
                        return createLoanDetail(deal.getId(), partnerId, input, deal.getAssignRatio());
                    })
                    .flatMap(loanDetailRepository::save)
                    .doOnComplete(() -> 
                        logger.info("Bulk create completed for partnerId: {}, number of loans: {}", partnerId, inputs.size()))
                    .doOnError(error -> 
                        logger.error("Error in bulk create for partnerId: {}, number of loans: {}: {}", 
                            partnerId, inputs.size(), error.getMessage()));
            });
    }   

    @Transactional
    public Flux<LoanDetail> bulkUpdateLoanDetails(Long dealId, Long partnerId, List<LoanDetailModification> modifications) {
        logger.info("Bulk update request - dealId: {}, partnerId: {}, number of modifications: {}", 
            dealId, partnerId, modifications.size());
        logger.debug("Bulk update modification details: {}", modifications);

        return dealRepository.findById(dealId)
            .flatMapMany(deal -> {
                double assignedPercentage = deal.getAssignRatio();
                logger.debug("Retrieved assigned percentage: {} for dealId: {}", assignedPercentage, dealId);
                return Flux.fromIterable(modifications)
                    .flatMap(modification -> updateLoanDetail(dealId, partnerId, modification, assignedPercentage))
                    .doOnComplete(() -> 
                        logger.info("Bulk update completed for dealId: {}, partnerId: {}", dealId, partnerId))
                    .doOnError(error -> 
                        logger.error("Error in bulk update for dealId: {}, partnerId: {}: {}", 
                            dealId, partnerId, error.getMessage()));
            });
    }

    private LoanDetail createLoanDetail(
        Long dealId,
        Long partnerId,
        LoanDetailInputForDeal input,
        Double assignedPercentage
    ) {
        logger.debug("Creating loan detail - dealId: {}, partnerId: {}, input: {}", dealId, partnerId, input);

        LocalDateTime now = LocalDateTime.now();
        LoanDetail loanDetail = LoanDetail.builder()
            .dealId(dealId)
            .partnerId(partnerId)
            .lmsLan(input.lmsLAN())
            .currentPOS(input.currentPOS())
            .currentInterestRate(input.currentInterestRate()) // Use the decimal rate
            .assignedInterestRateOverride(null) // Not provided in input
            .currentAssignedOverdueInterest(input.currentAssignedOverdueInterest())
            .currentDpd(0) // Default value, not provided in input
            .status(input.status())
            .currentAssignedPOS(calculateAssignedPOS(input.currentPOS(), assignedPercentage))
            .loanStartedDate(input.loanStartedDate() != null ? input.loanStartedDate().toString() : null)
            .loanType(input.loanType())
            .source(input.source() != null ? input.source() : LoanDetail.LoanSource.FINRETAIL)
            .loanAge(input.loanAge())
            .assignedInterestOverdueSplit(input.assignedInterestOverdueSplit() != null ? input.assignedInterestOverdueSplit() : java.util.Collections.emptyList())
            .createdAt(now)
            .modifiedAt(now)
            .build();

        logger.debug("Created loan detail: {}", loanDetail);
        return loanDetail;
    }


    private Double calculateAssignedPOS(Double currentPOS, Double assignedPercentage) {
        return currentPOS * assignedPercentage;
    }

    private Mono<LoanDetail> updateLoanDetail(
        Long dealId,
        Long partnerId,
        LoanDetailModification modification,
        Double assignedPercentage
    ) {
        logger.debug("Updating loan detail - dealId: {}, partnerId: {}, modification: {}", 
            dealId, partnerId, modification);

        return loanDetailRepository.findByDealIdAndPartnerId(dealId, partnerId)
            .filter(loan -> loan.getLmsLan().equals(modification.lmsLAN()))
            .next()
            .map(existingLoan -> {
                logger.debug("Found existing loan to update: {}", existingLoan);
                if (modification.currentPOS() != null) {
                    existingLoan.setCurrentPOS(modification.currentPOS());
                    existingLoan.setCurrentAssignedPOS(
                        calculateAssignedPOS(modification.currentPOS(), assignedPercentage)
                    );
                }
                if (modification.currentInterestRate() != null) {
                    existingLoan.setCurrentInterestRate(modification.currentInterestRate());
                }
                if (modification.status() != null) {
                    existingLoan.setStatus(modification.status());
                }
                if (modification.currentAssignedOverdueInterest() != null) {
                    existingLoan.setCurrentAssignedOverdueInterest(modification.currentAssignedOverdueInterest());
                }
                if (modification.currentDpd() != null) {
                    existingLoan.setCurrentDpd(modification.currentDpd());
                }
                if (modification.loanType() != null) {
                    existingLoan.setLoanType(modification.loanType());
                }
                if (modification.loanStartedDate() != null) {
                    existingLoan.setLoanStartedDate(modification.loanStartedDate());
                }
                if (modification.source() != null) {
                    existingLoan.setSource(modification.source());
                }
                if (modification.loanAge() != null) {
                    existingLoan.setLoanAge(modification.loanAge());
                }
                if (modification.assignedInterestRateOverride() != null) {
                    existingLoan.setAssignedInterestRateOverride(modification.assignedInterestRateOverride());
                }
                existingLoan.setModifiedAt(LocalDateTime.now());
                logger.debug("Updated loan detail: {}", existingLoan);
                return existingLoan;
            })
            .flatMap(loanDetailRepository::save)
            .doOnError(error -> 
                logger.error("Error updating loan detail - dealId: {}, partnerId: {}, lmsLAN: {}: {}", 
                    dealId, partnerId, modification.lmsLAN(), error.getMessage()));
    }

    @Transactional(readOnly = true)
    public Flux<LoanDetail> getLoanDetailsForPartner(Long partnerId) {
        logger.info("Fetching loan details - partnerId: {}", partnerId);
        
        return loanDetailRepository.findByPartnerId(partnerId)
            .doOnComplete(() -> 
                logger.info("Completed fetching loan details - partnerId: {}", partnerId))
            .doOnError(error -> 
                logger.error("Error fetching loan details - partnerId: {}: {}", partnerId, error.getMessage()));
    }

    @Transactional(readOnly = true)
    public Flux<LoanDetail> getLoanDetailsForDeal(Long dealId, Long partnerId) {
        logger.info("Fetching loan details - dealId: {}, partnerId: {}", dealId, partnerId);
        
        return loanDetailRepository.findByDealIdAndPartnerId(dealId, partnerId)
            .doOnComplete(() -> 
                logger.info("Completed fetching loan details - dealId: {}, partnerId: {}", dealId, partnerId))
            .doOnError(error -> 
                logger.error("Error fetching loan details - dealId: {}, partnerId: {}: {}", 
                    dealId, partnerId, error.getMessage()));
    }

    @Transactional(readOnly = true)
    public Mono<MonthlyDealProcessingStatus> getMonthlyLoanDetailsByDealId(Long dealId, Long partnerId, int year, int month) {
        return monthlyDealProcessingStatusRepository.findByDealIdAndPartnerIdAndYearAndMonth(dealId, partnerId, year, month)
            .doOnNext(result -> 
                logger.info("Completed fetching monthly loan details - dealId: {}, partnerId: {}, year: {}, month: {}", dealId, partnerId, year, month))
            .doOnError(error -> 
                logger.error("Error fetching monthly loan details - dealId: {}, partnerId: {}, year: {}, month: {}: {}", 
                    dealId, partnerId, year, month, error.getMessage()));
    }

    private LoanDetail createLoanDetail(
        Long dealId,
        Long partnerId,
        LoanDetailInputForPartner input,
        Double assignedPercentage
    ) {
        return LoanDetail.builder()
            .dealId(dealId)
            .partnerId(partnerId)
            .lmsLan(input.lmsLAN())
            .status(input.status())
            .currentPOS(input.currentPOS())
            .currentInterestRate(input.currentInterestRate())
            .assignedInterestRateOverride(input.assignedInterestRateOverride())
            .currentAssignedOverdueInterest(input.currentAssignedOverdueInterest())
            .currentDpd(input.currentDpd())
            .currentAssignedPOS(calculateAssignedPOS(input.currentPOS(), assignedPercentage))
            .loanType(input.loanType())
            .loanStartedDate(input.loanStartedDate())
            .source(input.source() != null ? input.source() : LoanDetail.LoanSource.FINRETAIL)
            .loanAge(input.loanAge())
            .assignedInterestOverdueSplit(input.assignedInterestOverdueSplit() != null ? input.assignedInterestOverdueSplit() : java.util.Collections.emptyList())
            .createdAt(LocalDateTime.now())
            .modifiedAt(LocalDateTime.now())
            .build();
    }
}
