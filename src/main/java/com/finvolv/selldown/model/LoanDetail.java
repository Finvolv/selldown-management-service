package com.finvolv.selldown.model;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("\"sd-loan_details\"")
public class LoanDetail {
    @Id
    private Long id;
    
    private Long dealId;
    private Long partnerId;
    
    @Size(max = 50, message = "LMS LAN must not exceed 50 characters")
    private String lmsLan;
    
    @Size(max = 50, message = "Name must not exceed 50 characters")
    @Column("current_pos")
    private Double currentPOS;
    
    @Column("current_assigned_pos")
    private Double currentAssignedPOS;
    
    @Column("current_interest_rate")
    private Double currentInterestRate;
    
    @Column("assigned_interest_rate_override")
    private Double assignedInterestRateOverride;
    
    @Column("current_assigned_overdue_interest")
    private Double currentAssignedOverdueInterest;
    
    @Builder.Default
    @Column("current_dpd")
    private Integer currentDpd = 0;
    
    private LoanStatus status;

    @Column("last_cycle_end_date")
    private LocalDate lastCycleEndDate;
    
    @Column("loan_type")
    private LoanType loanType;
    
    @Column("loan_started_date")
    private String loanStartedDate;
    
    @Builder.Default
    @Column("loan_age")
    private Integer loanAge = 0;
    
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("modified_at")
    private LocalDateTime modifiedAt;

    public enum LoanStatus {
        ACTIVE,
        CLOSED,
        DEFAULTED,
        WRITTEN_OFF,
        FORECLOSED   
     }

    public enum LoanType {
        PERSONAL,
        HOME,
        VEHICLE,
        BUSINESS,
        EDUCATION
    }
}
