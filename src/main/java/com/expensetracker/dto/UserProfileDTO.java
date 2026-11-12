package com.expensetracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "User profile and preferences")
public class UserProfileDTO {

    private Long userId;

    @NotBlank(message = "Name cannot be empty")
    @Size(max = 100)
    private String name;

    private String email;

    @NotBlank(message = "Currency cannot be empty")
    private String currency;

    public UserProfileDTO() {
    }

    public UserProfileDTO(Long userId, String name, String email, String currency) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.currency = currency;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
