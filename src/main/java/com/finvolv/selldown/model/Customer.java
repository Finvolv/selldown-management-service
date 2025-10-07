package com.finvolv.selldown.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@Table("\"sd-customer\"")
public class Customer {
    @Id
    private Long id;

    @Size(max = 50, message = "Name must not exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s_-]+$", message = "Name can only contain letters, numbers, spaces, underscores and hyphens")
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    @Email(message = "Invalid legal contact email format")
    private String legalContactEmail;

    @Email(message = "Invalid operations contact email format")
    private String operationsContactEmail;

    @Email(message = "Invalid business sponsor email format")
    private String businessSponsorEmail;

    @Size(max = 256, message = "Address must not exceed 256 characters")
    private String address;

    private Map<String, Object> additionalInfo;

    private String createdBy;
    private String updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}


