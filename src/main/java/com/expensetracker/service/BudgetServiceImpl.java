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
        String targetMonth = budgetDTO.getMonthYear().trim();
        BigDecimal limit = budgetDTO.getMonthlyLimit();

        Budget budget = budgetRepository.findFirstByUserAndMonthYearOrderByIdDesc(user, targetMonth)
                .orElse(null);

        if (budget == null) {
            budget = new Budget(user, targetMonth, limit);
        } else {
            budget.setUser(user);
            budget.setMonthlyLimit(limit);
        }

        Budget saved = budgetRepository.save(budget);
        log.info("Set monthly budget to {} for user [ID: {}] on month {}", saved.getMonthlyLimit(), user.getId(), saved.getMonthYear());
        return mapToDTO(saved);
    }

    private BudgetDTO mapToDTO(Budget budget) {
        return new BudgetDTO(budget.getId(), budget.getMonthYear(), budget.getMonthlyLimit());
    }
}
