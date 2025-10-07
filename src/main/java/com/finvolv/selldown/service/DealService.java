package com.finvolv.selldown.service;

import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.Customer;
import com.finvolv.selldown.repository.DealRepository;
import com.finvolv.selldown.repository.CustomerRepository;
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

    public Mono<Deal> getDealById(Long id) {
        return dealRepository.findById(id);
    }

    public Mono<Deal> createDeal(Deal deal, String createdBy) {
        if (deal.getDealType() == null) {
            return Mono.error(new IllegalArgumentException("Deal type must be specified"));
        }
        deal.setAdditionalInfo(deal.getAdditionalInfo()==null? new HashMap<>() : new HashMap<>(deal.getAdditionalInfo()));
        deal.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        deal.setCreatedBy(createdBy);
        deal.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        deal.setUpdatedBy(createdBy);
        return checkCustomerExists(deal.getCustomerId())
                .then(dealRepository.save(deal));
    }

    public Mono<Void> deleteDeal(Long id) {
        return dealRepository.deleteById(id);
    }

    public Mono<Deal> updateDeal(Long id, Deal deal, String updatedBy) {
        if (deal.getDealType() == null) {
            return Mono.error(new IllegalArgumentException("Deal type must be specified"));
        }
        return dealRepository.findById(id)
                .map(existing -> deal.toBuilder()
                        .id(existing.getId())
                        .additionalInfo(deal.getAdditionalInfo()==null? new HashMap<>() : new HashMap<>(deal.getAdditionalInfo()))
                        .createdAt(existing.getCreatedAt())
                        .createdBy(existing.getCreatedBy())
                        .updatedAt(new Timestamp(System.currentTimeMillis()))
                        .updatedBy(updatedBy)
                        .build())
                .flatMap(dealRepository::save);
    }

    public Flux<Deal> getAllDeals() {
        return dealRepository.findAll();
    }

    private Mono<Customer> checkCustomerExists(Long customerId) {
        return customerRepository.findById(customerId);
    }
}


