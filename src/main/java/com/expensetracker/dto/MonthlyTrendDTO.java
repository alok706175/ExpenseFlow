package com.expensetracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Monthly aggregation of income vs. expenses")
public class MonthlyTrendDTO {

    @Schema(example = "2026-11", description = "Year and month formatted as YYYY-MM")
    private String month;

    @Schema(example = "4500.00", description = "Total income for the month")
    private BigDecimal income;

    @Schema(example = "1850.25", description = "Total expense for the month")
    private BigDecimal expense;

    public MonthlyTrendDTO() {
    }

    public MonthlyTrendDTO(String month, BigDecimal income, BigDecimal expense) {
        this.month = month;
        this.income = income;
        this.expense = expense;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }
}

