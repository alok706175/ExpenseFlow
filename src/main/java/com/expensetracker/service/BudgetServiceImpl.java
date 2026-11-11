package com.expensetracker.service;

import com.expensetracker.dto.BudgetDTO;
import com.expensetracker.entity.Budget;
import com.expensetracker.repository.BudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetServiceImpl(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetDTO getBudgetForMonth(String monthYear) {
        return budgetRepository.findByMonthYear(monthYear)
                .map(this::mapToDTO)
                .orElse(new BudgetDTO(null, monthYear, BigDecimal.ZERO));
    }

    @Override
    public BudgetDTO setBudgetForMonth(BudgetDTO budgetDTO) {
        Budget budget = budgetRepository.findByMonthYear(budgetDTO.getMonthYear())
                .orElse(new Budget(budgetDTO.getMonthYear(), budgetDTO.getMonthlyLimit()));

        budget.setMonthlyLimit(budgetDTO.getMonthlyLimit());
        Budget saved = budgetRepository.save(budget);
        return mapToDTO(saved);
    }

    private BudgetDTO mapToDTO(Budget budget) {
        return new BudgetDTO(budget.getId(), budget.getMonthYear(), budget.getMonthlyLimit());
    }
}

