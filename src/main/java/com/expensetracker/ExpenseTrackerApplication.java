package com.expensetracker;

import com.expensetracker.entity.*;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@SpringBootApplication
public class ExpenseTrackerApplication {

    private static final Logger log = LoggerFactory.getLogger(ExpenseTrackerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }

    /**
     * Seeds initial default categories and a demo user account if database is empty.
     */
    @Bean
    public CommandLineRunner seedDemoData(
            UserRepository userRepo,
            TransactionRepository transactionRepo,
            BudgetRepository budgetRepo,
            CategoryRepository categoryRepo,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Seed Global Default Categories
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
            }

            // 2. Seed Demo User Account
            if (userRepo.count() == 0) {
                log.info("Seeding demo user (demo@example.com / Password123)...");
                User demoUser = new User(
                        "Demo User",
                        "demo@example.com",
                        passwordEncoder.encode("Password123"),
                        "INR"
                );
                demoUser = userRepo.save(demoUser);

                LocalDate today = LocalDate.now();
                YearMonth currentMonth = YearMonth.from(today);

                // Initial budget
                budgetRepo.save(new Budget(demoUser, currentMonth.toString(), new BigDecimal("35000.00")));

                // Seed Transactions in INR
                transactionRepo.save(new Transaction(demoUser, "Monthly Salary", new BigDecimal("75000.00"), TransactionType.INCOME, "Salary", today.minusDays(10), "Direct deposit payroll"));
                transactionRepo.save(new Transaction(demoUser, "Freelance Project", new BigDecimal("18500.00"), TransactionType.INCOME, "Freelance", today.minusDays(5), "Client milestone payment"));

                transactionRepo.save(new Transaction(demoUser, "House Rent", new BigDecimal("15000.00"), TransactionType.EXPENSE, "Rent", today.minusDays(9), "Monthly apartment lease"));
                transactionRepo.save(new Transaction(demoUser, "Grocery & Supermarket", new BigDecimal("4250.00"), TransactionType.EXPENSE, "Food", today.minusDays(7), "Weekly organic groceries"));
                transactionRepo.save(new Transaction(demoUser, "Electricity & Water Bill", new BigDecimal("1850.00"), TransactionType.EXPENSE, "Utilities", today.minusDays(6), "State power bill"));
                transactionRepo.save(new Transaction(demoUser, "Broadband Internet", new BigDecimal("999.00"), TransactionType.EXPENSE, "Utilities", today.minusDays(4), "Monthly fiber plan"));
                transactionRepo.save(new Transaction(demoUser, "Weekend Dinner", new BigDecimal("1650.00"), TransactionType.EXPENSE, "Food", today.minusDays(3), "Family restaurant"));
                transactionRepo.save(new Transaction(demoUser, "Gym Membership", new BigDecimal("1500.00"), TransactionType.EXPENSE, "Health", today.minusDays(2), "Monthly fitness club"));
                transactionRepo.save(new Transaction(demoUser, "Movie & Snacks", new BigDecimal("850.00"), TransactionType.EXPENSE, "Entertainment", today.minusDays(1), "Weekend cinema"));
                transactionRepo.save(new Transaction(demoUser, "Metro Commute Pass", new BigDecimal("1200.00"), TransactionType.EXPENSE, "Travel", today, "City rail smartcard"));

                // Historical trend data
                transactionRepo.save(new Transaction(demoUser, "Previous Month Salary", new BigDecimal("75000.00"), TransactionType.INCOME, "Salary", today.minusMonths(1).withDayOfMonth(1), "Payroll"));
                transactionRepo.save(new Transaction(demoUser, "Previous Month Rent", new BigDecimal("15000.00"), TransactionType.EXPENSE, "Rent", today.minusMonths(1).withDayOfMonth(3), "Rent"));
                transactionRepo.save(new Transaction(demoUser, "Previous Month Groceries", new BigDecimal("8500.00"), TransactionType.EXPENSE, "Food", today.minusMonths(1).withDayOfMonth(15), "Food"));

                log.info("Demo data seeding completed successfully.");
            }
        };
    }
}
