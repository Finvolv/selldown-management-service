package com.finvolv.selldown.service;

import com.finvolv.selldown.model.Customer;
import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.repository.CustomerRepository;
import com.finvolv.selldown.repository.DealRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.util.HashMap;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DealRepository dealRepository;

    public Mono<Customer> createCustomer(Customer customer, String createdBy) {
        customer.setAdditionalInfo(customer.getAdditionalInfo()==null ? new HashMap<>() : new HashMap<>(customer.getAdditionalInfo()));
        customer.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        customer.setCreatedBy(createdBy);
        customer.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        customer.setUpdatedBy(createdBy);
        return customerRepository.save(customer);
    }

    public Flux<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Mono<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    public Flux<Deal> getDealsByCustomerId(Long customerId) {
        return dealRepository.findByCustomerId(customerId);
    }

    public Mono<Customer> updateCustomer(Long id, Customer customer, String updatedBy) {
        return customerRepository.findById(id)
                .map(existing -> customer.toBuilder()
                        .id(existing.getId())
                        .additionalInfo(customer.getAdditionalInfo()==null ? new HashMap<>() : new HashMap<>(customer.getAdditionalInfo()))
                        .createdAt(existing.getCreatedAt())
                        .createdBy(existing.getCreatedBy())
                        .updatedAt(new Timestamp(System.currentTimeMillis()))
                        .updatedBy(updatedBy)
                        .build())
                .flatMap(customerRepository::save);
    }

    public Mono<Void> deleteCustomer(Long id) {
        return customerRepository.deleteById(id);
    }
}


