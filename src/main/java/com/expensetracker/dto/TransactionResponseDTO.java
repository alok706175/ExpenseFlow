package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Representation of a stored transaction")
public class TransactionResponseDTO {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "Grocery Shopping")
    private String title;

    @Schema(example = "75.50")
    private BigDecimal amount;

    @Schema(example = "EXPENSE")
    private TransactionType type;

    @Schema(example = "Food")
    private String category;

    @Schema(example = "2026-11-11")
    private LocalDate transactionDate;

    @Schema(example = "Weekly organic groceries from local market")
    private String notes;

    @Schema(example = "2026-11-11T12:00:00")
    private LocalDateTime createdAt;

    public TransactionResponseDTO() {
    }

    public TransactionResponseDTO(Long id, String title, BigDecimal amount, TransactionType type, String category, LocalDate transactionDate, String notes, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.transactionDate = transactionDate;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

