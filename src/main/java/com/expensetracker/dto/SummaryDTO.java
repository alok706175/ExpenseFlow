package com.expensetracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "Financial summary metrics and budget tracking analytics")
public class SummaryDTO {

    @Schema(example = "5200.00", description = "Total all-time or filtered income")
    private BigDecimal totalIncome;

    @Schema(example = "2150.50", description = "Total all-time or filtered expenses")
    private BigDecimal totalExpense;

    @Schema(example = "3049.50", description = "Net balance (Income - Expense)")
    private BigDecimal balance;

    @Schema(example = "Food", description = "Category with the highest expenditure")
    private String topSpendingCategory;

    @Schema(description = "Category to expense amount mapping for chart breakdown")
    private Map<String, BigDecimal> categoryExpenses;

    @Schema(description = "Recent monthly income vs expense trends")
    private List<MonthlyTrendDTO> monthlyTrends;

    @Schema(example = "2026-11", description = "Current month in YYYY-MM")
    private String currentMonth;

    @Schema(example = "1500.00", description = "Current month's expense sum")
    private BigDecimal currentMonthExpense;

    @Schema(example = "2000.00", description = "Monthly budget limit set by user")
    private BigDecimal monthlyBudgetLimit;

    @Schema(example = "75.0", description = "Budget utilization percentage")
    private Double budgetUsedPercentage;

    @Schema(example = "OK", allowableValues = {"OK", "WARNING", "EXCEEDED", "NOT_SET"}, description = "Budget health alert status")
    private String alertStatus;

    @Schema(example = "Budget is on track (75.0% used).", description = "Human-readable alert message")
    private String alertMessage;

    public SummaryDTO() {
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getTopSpendingCategory() {
        return topSpendingCategory;
    }

    public void setTopSpendingCategory(String topSpendingCategory) {
        this.topSpendingCategory = topSpendingCategory;
    }

    public Map<String, BigDecimal> getCategoryExpenses() {
        return categoryExpenses;
    }

    public void setCategoryExpenses(Map<String, BigDecimal> categoryExpenses) {
        this.categoryExpenses = categoryExpenses;
    }

    public List<MonthlyTrendDTO> getMonthlyTrends() {
        return monthlyTrends;
    }

    public void setMonthlyTrends(List<MonthlyTrendDTO> monthlyTrends) {
        this.monthlyTrends = monthlyTrends;
    }

    public String getCurrentMonth() {
        return currentMonth;
    }

    public void setCurrentMonth(String currentMonth) {
        this.currentMonth = currentMonth;
    }

    public BigDecimal getCurrentMonthExpense() {
        return currentMonthExpense;
    }

    public void setCurrentMonthExpense(BigDecimal currentMonthExpense) {
        this.currentMonthExpense = currentMonthExpense;
    }

    public BigDecimal getMonthlyBudgetLimit() {
        return monthlyBudgetLimit;
    }

    public void setMonthlyBudgetLimit(BigDecimal monthlyBudgetLimit) {
        this.monthlyBudgetLimit = monthlyBudgetLimit;
    }

    public Double getBudgetUsedPercentage() {
        return budgetUsedPercentage;
    }

    public void setBudgetUsedPercentage(Double budgetUsedPercentage) {
        this.budgetUsedPercentage = budgetUsedPercentage;
    }

    public String getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(String alertStatus) {
        this.alertStatus = alertStatus;
    }

    public String getAlertMessage() {
        return alertMessage;
    }

    public void setAlertMessage(String alertMessage) {
        this.alertMessage = alertMessage;
    }
}

