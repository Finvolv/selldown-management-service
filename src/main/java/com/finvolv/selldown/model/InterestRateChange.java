package com.finvolv.selldown.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
@Table("\"sd-interest_rate_change\"")
public class InterestRateChange {
    @Id
    private Long id;
    private Long dealId;

    @DecimalMin(value = "0.0", inclusive = true, message = "Interest rate must be at least 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "Interest rate must not exceed 1")
    private Double interestRate;
    private LocalDate startDate;
    private LocalDate endDate;
}


