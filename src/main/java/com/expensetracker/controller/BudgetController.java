package com.expensetracker.controller;

import com.expensetracker.dto.BudgetDTO;
import com.expensetracker.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/budget")
@Tag(name = "Budgets", description = "Endpoints for configuring and querying monthly budget limits")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(summary = "Get budget for a specific month", description = "Returns monthly budget limit for YYYY-MM")
    public ResponseEntity<BudgetDTO> getBudget(
            @RequestParam(required = false) String monthYear) {
        String targetMonth = (monthYear != null && !monthYear.trim().isEmpty())
                ? monthYear.trim()
                : YearMonth.now().toString();

        return ResponseEntity.ok(budgetService.getBudgetForMonth(targetMonth));
    }

    @PostMapping
    @Operation(summary = "Set or update monthly budget", description = "Configures monthly budget limit threshold")
    public ResponseEntity<BudgetDTO> setBudget(@Valid @RequestBody BudgetDTO budgetDTO) {
        return ResponseEntity.ok(budgetService.setBudgetForMonth(budgetDTO));
    }
}

