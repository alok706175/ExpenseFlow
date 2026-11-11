package com.expensetracker.service;

import com.expensetracker.dto.BudgetDTO;

public interface BudgetService {

    BudgetDTO getBudgetForMonth(String monthYear);

    BudgetDTO setBudgetForMonth(BudgetDTO budgetDTO);
}

