package com.finvolv.selldown.dto;

import com.finvolv.selldown.model.Deal;
import com.finvolv.selldown.model.InterestRateChange;
import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DealDto {
    private Long id;
    private String name;
    private String description;
    private Double openingAmount;
    private Deal.DealStatus status;
    private LocalDate startDate;
    private Double annualInterestRate;
    private Double assignRatio;
    private Deal.InterestMethod interestMethod;
    private Deal.DealType dealType;
    private Map<String, Object> additionalInfo;
    private Long customerId;
  
    private String createdBy;
    private Timestamp createdAt;
    private String updatedBy;
    private Timestamp updatedAt;
    private List<InterestRateChange> interestRateTable;
}


