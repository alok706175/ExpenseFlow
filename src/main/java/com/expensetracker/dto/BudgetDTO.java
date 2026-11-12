package com.expensetracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@Schema(description = "DTO for retrieving and updating monthly budget limit")
public class BudgetDTO {

    private Long id;

    @NotBlank(message = "monthYear is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Format must be YYYY-MM")
    @Schema(example = "2026-11", description = "Target budget month (YYYY-MM)")
    private String monthYear;

    @NotNull(message = "Monthly limit is required")
    @DecimalMin(value = "0.00", message = "Limit must be positive or zero")
    @Schema(example = "2500.00", description = "Monthly budget threshold")
    private BigDecimal monthlyLimit;

    public BudgetDTO() {
    }

    public BudgetDTO(Long id, String monthYear, BigDecimal monthlyLimit) {
        this.id = id;
        this.monthYear = monthYear;
        this.monthlyLimit = monthlyLimit;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMonthYear() {
        return monthYear;
    }

    public void setMonthYear(String monthYear) {
        this.monthYear = monthYear;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }
}

