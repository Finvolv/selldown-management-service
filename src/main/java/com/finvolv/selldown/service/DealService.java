package com.finvolv.selldown.service;

import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.Customer;
import com.finvolv.selldown.model.InterestRateChange;
import com.finvolv.selldown.repository.DealRepository;
import com.finvolv.selldown.repository.CustomerRepository;
import com.finvolv.selldown.repository.InterestRateChangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.util.HashMap;

@Service
public class DealService {

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InterestRateChangeRepository interestRateChangeRepository;

    public Mono<Deal> getDealById(Long id) {
        return dealRepository.findById(id);
    }

    public Mono<Deal> createDeal(Deal deal, String createdBy) {
        // Validate dealType is provided
        if (deal.getDealType() == null) {
            return Mono.error(new IllegalArgumentException("Deal type must be specified"));
        }
        deal.setAdditionalInfo(deal.getAdditionalInfo()==null? new HashMap<>() : new HashMap<>(deal.getAdditionalInfo()));
        deal.setChargesApplicable(deal.getChargesApplicable() == null ? false : deal.getChargesApplicable());
        deal.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        deal.setCreatedBy(createdBy);
        deal.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        deal.setUpdatedBy(createdBy);
        return checkCustomerExists(deal.getCustomerId())
                .then(dealRepository.save(deal))
                .flatMap(savedDeal -> {
                    // Create an entry in Interest Rate Change table
                    InterestRateChange interestRateChange = InterestRateChange.builder()
                            .dealId(savedDeal.getId())
                            .startDate(savedDeal.getStartDate())
                            .interestRate(savedDeal.getAnnualInterestRate())
                            .endDate(null)
                            .build();
                    return interestRateChangeRepository.save(interestRateChange)
                            .thenReturn(savedDeal);
                });
    }

    public Mono<Void> deleteDeal(Long id) {
        return dealRepository.deleteById(id);
    }

    public Mono<Deal> updateDeal(Long id, Deal deal, String updatedBy) {
        if (deal.getDealType() == null) {
            return Mono.error(new IllegalArgumentException("Deal type must be specified"));
        }
        return dealRepository.findById(id)
                .map(existing -> {
                    var builder = deal.toBuilder()
                            .id(existing.getId())
                            .additionalInfo(deal.getAdditionalInfo()==null? new HashMap<>() : new HashMap<>(deal.getAdditionalInfo()))
                            .createdAt(existing.getCreatedAt())
                            .createdBy(existing.getCreatedBy())
                            .updatedAt(new Timestamp(System.currentTimeMillis()))
                            .updatedBy(updatedBy);
                    // Set default value if chargesApplicable is null
                    if (deal.getChargesApplicable() == null) {
                        builder.chargesApplicable(false);
                    }
                    return builder.build();
                })
                .flatMap(dealRepository::save);
    }

    public Flux<Deal> getAllDeals() {
        return dealRepository.findAll();
    }

    private Mono<Customer> checkCustomerExists(Long customerId) {
        return customerRepository.findById(customerId);
    }
}


