package com.finvolv.selldown.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OpeningPosDiscrepancy {
    private String lmsLan;
    private BigDecimal payoutOpeningPos;
    private BigDecimal loanDetailOpeningPos;
    private BigDecimal difference;
    private boolean isMismatch;
    private String discrepancyType; // "CURRENT_MONTH" or "PREVIOUS_MONTH"
    private String description;
}
