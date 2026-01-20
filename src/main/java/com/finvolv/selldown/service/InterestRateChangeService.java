package com.finvolv.selldown.service;

import com.finvolv.selldown.model.InterestRateChange;
import com.finvolv.selldown.repository.InterestRateChangeRepository;
import com.finvolv.selldown.repository.DealRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
public class InterestRateChangeService {

    @Autowired
    private InterestRateChangeRepository interestRateChangeRepository;

    @Autowired
    private DealRepository dealRepository;

    public Flux<InterestRateChange> getInterestRateChanges(Long dealId) {
        return interestRateChangeRepository.findByDealId(dealId);
    }

    public Mono<InterestRateChange> changeInterestRate(Long id, Long dealId, Double interestRate, LocalDate startDate, LocalDate endDate) {
        // If id is provided, update existing entry
        if (id != null) {
            return interestRateChangeRepository.findById(id)
                    .flatMap(existingRate -> {
                        existingRate.setInterestRate(interestRate);
                        existingRate.setStartDate(startDate);
                        existingRate.setEndDate(endDate);
                        return interestRateChangeRepository.save(existingRate);
                    })
                    .flatMap(updatedRate ->
                            dealRepository.findById(dealId)
                                    .flatMap(deal -> {
                                        deal.setAnnualInterestRate(interestRate);
                                        return dealRepository.save(deal);
                                    })
                                    .thenReturn(updatedRate)
                    );
        }

        // Find the most recent interest rate entry for the deal
        return interestRateChangeRepository.findTopByDealIdOrderByStartDateDesc(dealId)
                .flatMap(previousRate -> {
                    // Update the previous rate's end date to be one day before new start date
                    previousRate.setEndDate(startDate.minusDays(1));
                    return interestRateChangeRepository.save(previousRate); // Save the updated previous entry
                })
                .switchIfEmpty(Mono.empty()) // If no previous rate exists, continue
                .then( // Insert the new interest rate change
                        interestRateChangeRepository.save(
                                InterestRateChange.builder()
                                        .dealId(dealId)
                                        .interestRate(interestRate)
                                        .startDate(startDate)
                                        .endDate(endDate)
                                        .build()
                        )
                )
                .flatMap(newRateChange ->
                        // After saving the new interest rate, update the deal table
                        dealRepository.findById(dealId)
                                .flatMap(deal -> {
                                    deal.setAnnualInterestRate(interestRate);
                                    return dealRepository.save(deal);
                                })
                                .thenReturn(newRateChange)
                );
    }
}


