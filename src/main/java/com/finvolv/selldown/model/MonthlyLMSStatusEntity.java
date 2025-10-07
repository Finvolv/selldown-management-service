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
@Table("\"sd-monthly_lms_status\"")
public class MonthlyLMSStatusEntity {

    @Id
    private Long id;

    private Integer year;
    private Integer month;
    
    private MonthlyLMSStatus status;

    @Column("cycle_start_date")
    private LocalDate cycleStartDate;
    
    @Column("cycle_end_date")
    private LocalDate cycleEndDate;

    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("modified_at")
    private LocalDateTime modifiedAt;
}
