package com.finvolv.selldown.dto;

import com.finvolv.selldown.model.LoanDetail;
import com.finvolv.selldown.model.MonthlyDealProcessingStatus;
import com.finvolv.selldown.model.PartnerPayoutDetailsAll;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelGenerationResponse {
    
    private Long dealId;
    private Long partnerId;
    private Integer year;
    private Integer month;
    
    private List<LoanDetail> loanDetails;
    private List<PartnerPayoutDetailsAll> matchedPayoutDetails;
    private MonthlyDealProcessingStatus dealProcessingStatus;
    private List<OpeningPosDiscrepancy> openingPosDiscrepancies;
    
    private int totalLoanDetails;
    private int matchedPayoutDetailsCount;
    private int updatedPayoutDetailsCount;
    private int discrepancyCount;
    
    private String message;
    private boolean success;
}
