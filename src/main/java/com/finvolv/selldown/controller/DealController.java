package com.finvolv.selldown.controller;

import com.finvolv.selldown.dto.DealDto;
import com.finvolv.selldown.exception.DealNotFoundException;
import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.InterestRateChange;
import com.finvolv.selldown.service.DealService;
import com.finvolv.selldown.service.InterestRateChangeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/deals")
@CrossOrigin(origins = "*")
public class DealController {

    @Autowired
    private DealService dealService;

    @Autowired
    private InterestRateChangeService interestRateChangeService;

    // Create a new deal
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Deal> createDeal(@Valid @RequestBody Deal deal) {
        //TODO: replace admin with authenticated user
        return dealService.createDeal(deal, "admin");
    }

    // Get all deals
    @GetMapping
    public Flux<Deal> getAllDeals() {
        return dealService.getAllDeals();
    }

    // Get deal by ID
    @GetMapping("/{id}")
    public Mono<DealDto> getDealById(@PathVariable Long id) {
        return dealService.getDealById(id)
                .switchIfEmpty(Mono.error(new DealNotFoundException(id)))
                .flatMap(deal ->
                        interestRateChangeService.getInterestRateChanges(deal.getId())
                                .collectList()
                                .map(interestRateChanges -> DealDto.builder()
                                        .id(deal.getId())
                                        .name(deal.getName())
                                        .description(deal.getDescription())
                                        .openingAmount(deal.getOpeningAmount())
                                        .status(deal.getStatus())
                                        .startDate(deal.getStartDate())
                                        .annualInterestRate(deal.getAnnualInterestRate())
                                        .assignRatio(deal.getAssignRatio())
                                        .interestMethod(deal.getInterestMethod())
                                        .dealType(deal.getDealType())
                                        .additionalInfo(deal.getAdditionalInfo())
                                        .customerId(deal.getCustomerId())
                                        .createdBy(deal.getCreatedBy())
                                        .createdAt(deal.getCreatedAt())
                                        .updatedBy(deal.getUpdatedBy())
                                        .updatedAt(deal.getUpdatedAt())
                                        .interestRateTable(interestRateChanges)
                                        .monthOnMonthDay(deal.getMonthOnMonthDay())
                                        .chargesApplicable(deal.getChargesApplicable())
                                        .build())
                );
    }

    // Update deal
    @PutMapping("/{id}")
    public Mono<Deal> updateDeal(@PathVariable Long id, @Valid @RequestBody Deal deal) {
        //TODO: replace admin with authenticated user
        return dealService.updateDeal(id, deal, "admin");
    }

    // Delete deal
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteDeal(@PathVariable Long id) {
        return dealService.deleteDeal(id);
    }

    // Change interest rate
    @PostMapping("/interest-rate/update")
    public Mono<InterestRateChange> changeInterestRate(@RequestBody InterestRateChange interestRateChange) {
        Long dealId = interestRateChange.getDealId();
        Double interestRate = interestRateChange.getInterestRate();
        LocalDate startDate = interestRateChange.getStartDate();
        LocalDate endDate = interestRateChange.getEndDate();
        return interestRateChangeService.changeInterestRate(dealId, interestRate, startDate, endDate);
    }


    // Exception Handler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(DealNotFoundException.class)
    public Mono<String> handleNotFound(DealNotFoundException ex) {
        return Mono.just(ex.getMessage());
    }
}


