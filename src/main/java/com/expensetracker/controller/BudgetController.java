package com.expensetracker.controller;

import com.expensetracker.dto.BudgetDTO;
import com.expensetracker.entity.User;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/budget")
@Tag(name = "Budgets", description = "Endpoints for configuring and querying user monthly budget limits")
public class BudgetController {

    private final BudgetService budgetService;
    private final AuthService authService;

    public BudgetController(BudgetService budgetService, AuthService authService) {
        this.budgetService = budgetService;
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "Get budget for a specific month", description = "Returns monthly budget limit for YYYY-MM for the current user")
    public ResponseEntity<BudgetDTO> getBudget(
            @RequestParam(required = false) String monthYear) {
        User user = authService.getCurrentAuthenticatedUser();
        String targetMonth = (monthYear != null && !monthYear.trim().isEmpty())
                ? monthYear.trim()
                : YearMonth.now().toString();

        return ResponseEntity.ok(budgetService.getBudgetForMonth(user, targetMonth));
    }

    @PostMapping
    @Operation(summary = "Set or update monthly budget", description = "Configures monthly budget limit threshold for the current user")
    public ResponseEntity<BudgetDTO> setBudget(@Valid @RequestBody BudgetDTO budgetDTO) {
        User user = authService.getCurrentAuthenticatedUser();
        return ResponseEntity.ok(budgetService.setBudgetForMonth(user, budgetDTO));
    }
}
