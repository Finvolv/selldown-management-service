package com.finvolv.selldown.model;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Map;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@Table("\"sd-deal\"")
public class Deal {
    @Id
    protected Long id;

    @Size(max = 50, message = "Name must not exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s_-]+$", message = "Name can only contain letters, numbers, spaces, underscores and hyphens")
    protected String name;

    @Size(max = 256, message = "Name must not exceed 256 characters")
    protected String description;

    @DecimalMin(value = "0", inclusive = true, message = "Opening Amount must be greater than or equal to 0")
    protected Double openingAmount;
    protected DealStatus status;
    protected LocalDate startDate;

    @DecimalMin(value = "0.0", inclusive = true, message = "Annual interest rate must be at least 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "Annual interest rate must not exceed 1")
    protected Double annualInterestRate;

    @DecimalMin(value = "0.0", inclusive = true, message = "Assign ratio must be at least 0")
    @DecimalMax(value = "1.0", inclusive = true, message = "Assign ratio must not exceed 1")
    protected Double assignRatio;
    protected InterestMethod interestMethod;
    protected DealType dealType;
    protected Map<String, Object> additionalInfo;
    protected Long customerId;

    protected String createdBy;
    protected String updatedBy;
    protected Timestamp createdAt;
    protected Timestamp updatedAt;

    @Min(value = 1, message = "Month on month day must be at least 1")
    @Max(value = 31, message = "Month on month day must be at most 31")
    protected Integer monthOnMonthDay;

    protected Boolean chargesApplicable;

    public enum DealStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public enum InterestMethod {
        ONE_TWELFTH, ACTUAL_BY_360, ACTUAL_BY_365, ACTUAL_BY_ACTUAL
    }

    public enum DealType {
        PTC, DA, COLENDING
    }
}


