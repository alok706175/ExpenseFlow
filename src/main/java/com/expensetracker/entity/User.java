package com.expensetracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_email", columnNames = {"email"})
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @JsonIgnore
    @Column(nullable = true) // Nullable for OAuth2 users who authenticate via Google
    private String password;

    @Column(nullable = false, length = 20)
    private String authProvider = "LOCAL"; // "LOCAL" or "GOOGLE"

    @Column(length = 100)
    private String providerId; // Google user ID (sub claim)

    @Column(nullable = false, length = 10)
    private String currency = "INR"; // Default currency (INR, USD, EUR, GBP)

    @Column(nullable = false, length = 20)
    private String role = "ROLE_USER";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String name, String email, String password, String currency) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.currency = (currency != null && !currency.trim().isEmpty()) ? currency : "INR";
        this.role = "ROLE_USER";
        this.authProvider = "LOCAL";
    }

    public User(String name, String email, String authProvider, String providerId, String currency) {
        this.name = name;
        this.email = email;
        this.authProvider = authProvider;
        this.providerId = providerId;
        this.currency = (currency != null && !currency.trim().isEmpty()) ? currency : "INR";
        this.role = "ROLE_USER";
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
