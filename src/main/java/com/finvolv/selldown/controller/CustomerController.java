package com.finvolv.selldown.controller;

import com.finvolv.selldown.model.Customer;
import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    // Create a new customer (partner)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Customer> createCustomer(@Valid @RequestBody Customer customer) {
        //TODO: replace admin with authenticated user
        return customerService.createCustomer(customer, "admin");
    }

    // Get all customers (partners)
    @GetMapping
    public Flux<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    // Get customer by ID
    @GetMapping("/{id}")
    public Mono<Customer> getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    // Get deals by customer
    @GetMapping("/{id}/deals")
    public Flux<Deal> getDealsByCustomer(@PathVariable Long id) {
        return customerService.getDealsByCustomerId(id);
    }

    // Update customer
    @PutMapping("/{id}")
    public Mono<Customer> updateCustomer(@PathVariable Long id,@Valid @RequestBody Customer customer) {
        //TODO: replace admin with authenticated user
        return customerService.updateCustomer(id, customer, "admin");
    }

    // Delete customer
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteCustomer(@PathVariable Long id) {
        return customerService.deleteCustomer(id);
    }
}


