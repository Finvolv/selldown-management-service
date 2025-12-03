package com.finvolv.selldown.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("\"sd-ssrs_file_data\"")
public class SSRSFileDataEntity {

    @Id
    private Long id;

    @Column("monthly_ssrs_id")
    private Long monthlySsrsId;
    
    @Column("lms_lan")
    private String lmsLan;
    
    private Map<String, Object> metadata;

    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("modified_at")
    private LocalDateTime modifiedAt;
}

