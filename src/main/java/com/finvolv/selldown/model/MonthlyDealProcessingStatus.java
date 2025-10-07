package com.finvolv.selldown.model;

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
@Table("\"sd-monthly_processing_status\"")
public class MonthlyDealProcessingStatus {

    @Id
    private Long id;

    @Column("deal_id")
    private Long dealId;
    
    @Column("partner_id")
    private Long partnerId;
    
    private Integer year;
    private Integer month;
    
    private MonthlyDealStatus status;
    
    @Column("cycle_start_date")
    private LocalDate cycleStartDate;
    
    @Column("cycle_end_date")
    private LocalDate cycleEndDate;

    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("modified_at")
    private LocalDateTime modifiedAt;
}
