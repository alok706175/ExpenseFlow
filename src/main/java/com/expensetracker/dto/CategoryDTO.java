package com.expensetracker.dto;

import com.expensetracker.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Category data transfer object")
public class CategoryDTO {

    private Long id;

    @NotBlank(message = "Category name is required")
    @Size(max = 50)
    private String name;

    private String color;
    private String icon;

    @NotNull(message = "Category type is required")
    private TransactionType type;

    private boolean isCustom;

    public CategoryDTO() {
    }

    public CategoryDTO(Long id, String name, String color, String icon, TransactionType type, boolean isCustom) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.type = type;
        this.isCustom = isCustom;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }
}
