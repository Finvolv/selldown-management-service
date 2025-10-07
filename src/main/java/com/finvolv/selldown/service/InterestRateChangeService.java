package com.finvolv.selldown.service;

import com.finvolv.selldown.model.InterestRateChange;
import com.finvolv.selldown.repository.InterestRateChangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
public class InterestRateChangeService {

    @Autowired
    private InterestRateChangeRepository interestRateChangeRepository;

    public Flux<InterestRateChange> getInterestRateChanges(Long dealId) {
        return interestRateChangeRepository.findByDealId(dealId);
    }

    public Mono<InterestRateChange> changeInterestRate(Long dealId, Double interestRate, LocalDate startDate, LocalDate endDate) {
        InterestRateChange change = InterestRateChange.builder()
                .dealId(dealId)
                .interestRate(interestRate)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return interestRateChangeRepository.save(change);
    }
}


