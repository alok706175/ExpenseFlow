package com.expensetracker.service;

import com.expensetracker.dto.BudgetDTO;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.User;
import com.expensetracker.repository.BudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class BudgetServiceImpl implements BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetServiceImpl.class);

    private final BudgetRepository budgetRepository;

    public BudgetServiceImpl(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetDTO getBudgetForMonth(User user, String monthYear) {
        return budgetRepository.findByUserAndMonthYear(user, monthYear)
                .map(this::mapToDTO)
                .orElse(new BudgetDTO(null, monthYear, BigDecimal.ZERO));
    }

    @Override
    public BudgetDTO setBudgetForMonth(User user, BudgetDTO budgetDTO) {
        Budget budget = budgetRepository.findByUserAndMonthYear(user, budgetDTO.getMonthYear())
                .orElse(new Budget(user, budgetDTO.getMonthYear(), budgetDTO.getMonthlyLimit()));

        budget.setMonthlyLimit(budgetDTO.getMonthlyLimit());
        Budget saved = budgetRepository.save(budget);
        log.info("Set monthly budget to {} for user [ID: {}] on month {}", saved.getMonthlyLimit(), user.getId(), saved.getMonthYear());
        return mapToDTO(saved);
    }

    private BudgetDTO mapToDTO(Budget budget) {
        return new BudgetDTO(budget.getId(), budget.getMonthYear(), budget.getMonthlyLimit());
    }
}
