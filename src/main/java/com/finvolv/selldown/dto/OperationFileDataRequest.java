package com.finvolv.selldown.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationFileDataRequest {
    private String lmsLan;
    private Map<String, Object> metadata;
}
