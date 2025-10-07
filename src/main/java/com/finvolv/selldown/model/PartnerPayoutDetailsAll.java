package com.finvolv.selldown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("\"sd-partner_payout_details_all\"")
public class PartnerPayoutDetailsAll {
    @Id
    private Long id;
    
    @Column("lms_lan")
    private String lmsLan;
    
    @Column("opening_pos")
    private BigDecimal openingPos;
    
    @Column("closing_pos")
    private BigDecimal closingPos;
    
    @Column("total_principal_due")
    private BigDecimal totalPrincipalDue;
    
    @Column("principal_overdue")
    private BigDecimal principalOverdue;
    
    @Column("total_principal_component_paid")
    private BigDecimal totalPrincipalComponentPaid;
    
    @Column("principal_overdue_paid")
    private BigDecimal principalOverduePaid;
    
    @Column("total_interest_due")
    private BigDecimal totalInterestDue;
    
    @Column("interest_overdue")
    private BigDecimal interestOverdue;
    
    @Column("total_interest_component_paid")
    private BigDecimal totalInterestComponentPaid;
    
    @Column("interest_overdue_paid")
    private BigDecimal interestOverduePaid;
    
    @Column("foreclosure_paid")
    private BigDecimal foreclosurePaid;
    
    @Column("foreclosure_charges_paid")
    private BigDecimal foreclosureChargesPaid;
    
    @Column("prepayment_paid")
    private BigDecimal prepaymentPaid;
    
    @Column("prepayment_charges_paid")
    private BigDecimal prepaymentChargesPaid;
    
    @Column("total_charges_paid")
    private BigDecimal totalChargesPaid;
    
    @Column("total_paid")
    private BigDecimal totalPaid;
    
    @Column("opening_dpd")
    private Integer openingDpd;
    
    @Column("closing_dpd")
    private Integer closingDpd;
    
    @Column("seller_opening_pos")
    private BigDecimal sellerOpeningPos;
    
    @Column("seller_closing_pos")
    private BigDecimal sellerClosingPos;
    
    @Column("seller_total_principal_due")
    private BigDecimal sellerTotalPrincipalDue;
    
    @Column("seller_principal_overdue")
    private BigDecimal sellerPrincipalOverdue;
    
    @Column("seller_total_principal_component_paid")
    private BigDecimal sellerTotalPrincipalComponentPaid;
    
    @Column("seller_principal_overdue_paid")
    private BigDecimal sellerPrincipalOverduePaid;
    
    @Column("seller_total_interest_due")
    private BigDecimal sellerTotalInterestDue;
    
    @Column("seller_interest_overdue")
    private BigDecimal sellerInterestOverdue;
    
    @Column("seller_total_interest_component_paid")
    private BigDecimal sellerTotalInterestComponentPaid;
    
    @Column("seller_interest_overdue_paid")
    private BigDecimal sellerInterestOverduePaid;
    
    @Column("seller_foreclosure_paid")
    private BigDecimal sellerForeclosurePaid;
    
    @Column("seller_foreclosure_charges_paid")
    private BigDecimal sellerForeclosureChargesPaid;
    
    @Column("seller_prepayment_paid")
    private BigDecimal sellerPrepaymentPaid;
    
    @Column("seller_prepayment_charges_paid")
    private BigDecimal sellerPrepaymentChargesPaid;
    
    @Column("seller_total_charges_paid")
    private BigDecimal sellerTotalChargesPaid;
    
    @Column("seller_total_paid")
    private BigDecimal sellerTotalPaid;
    
    @Column("seller_opening_dpd")
    private Integer sellerOpeningDpd;
    
    @Column("seller_closing_dpd")
    private Integer sellerClosingDpd;
    
    @Column("lms_id")
    private Long lmsId;
    
    @Column("deal_status_id")
    private Long dealStatusId;
    
    @Column("cycle_start_date")
    private LocalDate cycleStartDate;
    
    @Column("cycle_end_date")
    private LocalDate cycleEndDate;
    
    @Column("last_cycle_end_date")
    private LocalDate lastCycleEndDate;
    
    @Column("is_opening_pos_mismatch")
    private Boolean isOpeningPosMisMatch;
    
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("modified_at")
    private LocalDateTime modifiedAt;
}
