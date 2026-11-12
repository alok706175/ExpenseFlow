package com.expensetracker.service;

import com.expensetracker.dto.BudgetDTO;
import com.expensetracker.entity.User;

public interface BudgetService {

    BudgetDTO getBudgetForMonth(User user, String monthYear);

    BudgetDTO setBudgetForMonth(User user, BudgetDTO budgetDTO);
}
