package com.expensetracker;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@SpringBootApplication
public class ExpenseTrackerApplication {

    private static final Logger log = LoggerFactory.getLogger(ExpenseTrackerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }

    /**
     * Seeds initial default global categories if database is empty.
     * No demo user or dummy transactions are created.
     */
    @Bean
    @SuppressWarnings("null")
    public CommandLineRunner seedDefaultCategories(CategoryRepository categoryRepo) {
        return args -> {
            if (categoryRepo.count() == 0) {
                log.info("Seeding global default categories...");
                List<Category> defaultCategories = List.of(
                        new Category("Food", "#f59e0b", "fa-utensils", TransactionType.EXPENSE, null),
                        new Category("Rent", "#6366f1", "fa-house", TransactionType.EXPENSE, null),
                        new Category("Utilities", "#8b5cf6", "fa-bolt", TransactionType.EXPENSE, null),
                        new Category("Salary", "#10b981", "fa-money-bill-wave", TransactionType.INCOME, null),
                        new Category("Freelance", "#059669", "fa-laptop-code", TransactionType.INCOME, null),
                        new Category("Entertainment", "#ec4899", "fa-film", TransactionType.EXPENSE, null),
                        new Category("Travel", "#f97316", "fa-plane", TransactionType.EXPENSE, null),
                        new Category("Health", "#14b8a6", "fa-heart-pulse", TransactionType.EXPENSE, null),
                        new Category("Investment", "#0284c7", "fa-chart-line", TransactionType.EXPENSE, null),
                        new Category("Other", "#64748b", "fa-tag", TransactionType.EXPENSE, null)
                );
                categoryRepo.saveAll(defaultCategories);
                log.info("Default categories seeded successfully.");
            }
        };
    }

    /**
     * Safely drops legacy single-tenant unique constraint on month_year
     * to prevent 'Duplicate entry for key uk_budget_month_year' when multiple users set budgets.
     */
    @Bean
    public CommandLineRunner dropLegacyBudgetConstraint(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE budgets DROP INDEX uk_budget_month_year");
                log.info("Successfully dropped legacy single-tenant index uk_budget_month_year");
            } catch (Exception e) {
                // Index does not exist or already dropped, completely normal
                log.debug("No legacy index uk_budget_month_year to drop: {}", e.getMessage());
            }
        };
    }
}
