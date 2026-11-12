package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Data transfer object for creating or updating a transaction")
public class TransactionRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    @Schema(example = "Grocery Shopping", description = "Transaction title or short description")
    private String title;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Schema(example = "75.50", description = "Monetary amount")
    private BigDecimal amount;

    @NotNull(message = "Transaction type (INCOME or EXPENSE) is required")
    @Schema(example = "EXPENSE", description = "Type of transaction: INCOME or EXPENSE")
    private TransactionType type;

    @NotBlank(message = "Category is required")
    @Schema(example = "Food", description = "Category (e.g. Food, Rent, Utilities, Salary, Entertainment, Travel, Health, Other)")
    private String category;

    @NotNull(message = "Transaction date is required")
    @Schema(example = "2026-11-11", description = "Date when transaction occurred")
    private LocalDate transactionDate;

    @Size(max = 255, message = "Notes cannot exceed 255 characters")
    @Schema(example = "Weekly organic groceries from local market", description = "Additional notes or description")
    private String notes;

    public TransactionRequestDTO() {
    }

    public TransactionRequestDTO(String title, BigDecimal amount, TransactionType type, String category, LocalDate transactionDate, String notes) {
        this.title = title;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.transactionDate = transactionDate;
        this.notes = notes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

