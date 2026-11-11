package com.expensetracker;

import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.TransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@SpringBootApplication
public class ExpenseTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }

    /**
     * Seeds initial demo data if the database is empty, giving an immediate
     * out-of-the-box working experience with live charts and budget tracking.
     */
    @Bean
    public CommandLineRunner seedDemoData(TransactionRepository transactionRepo, BudgetRepository budgetRepo) {
        return args -> {
            if (transactionRepo.count() == 0) {
                LocalDate today = LocalDate.now();
                YearMonth currentMonth = YearMonth.from(today);

                // Set initial budget
                budgetRepo.save(new Budget(currentMonth.toString(), new BigDecimal("2500.00")));

                // Seed Transactions
                transactionRepo.save(new Transaction("Monthly Salary", new BigDecimal("5500.00"), TransactionType.INCOME, "Salary", today.minusDays(10), "Direct deposit payroll"));
                transactionRepo.save(new Transaction("Freelance Web Design", new BigDecimal("850.00"), TransactionType.INCOME, "Salary", today.minusDays(5), "Client project milestone"));

                transactionRepo.save(new Transaction("Apartment Rent", new BigDecimal("1200.00"), TransactionType.EXPENSE, "Rent", today.minusDays(9), "Monthly apartment lease"));
                transactionRepo.save(new Transaction("Whole Foods Groceries", new BigDecimal("142.50"), TransactionType.EXPENSE, "Food", today.minusDays(7), "Organic produce and pantry items"));
                transactionRepo.save(new Transaction("Electric & Water Utility", new BigDecimal("95.20"), TransactionType.EXPENSE, "Utilities", today.minusDays(6), "City power bill"));
                transactionRepo.save(new Transaction("Internet & Fiber Bill", new BigDecimal("60.00"), TransactionType.EXPENSE, "Utilities", today.minusDays(4), "1 Gbps high-speed internet"));
                transactionRepo.save(new Transaction("Dinner at Trattoria", new BigDecimal("68.00"), TransactionType.EXPENSE, "Food", today.minusDays(3), "Dinner with friends"));
                transactionRepo.save(new Transaction("Gym & Fitness Membership", new BigDecimal("45.00"), TransactionType.EXPENSE, "Health", today.minusDays(2), "Monthly fitness club access"));
                transactionRepo.save(new Transaction("Cinema & Popcorn", new BigDecimal("32.50"), TransactionType.EXPENSE, "Entertainment", today.minusDays(1), "Weekend movie"));
                transactionRepo.save(new Transaction("Metro Transit Card Refill", new BigDecimal("50.00"), TransactionType.EXPENSE, "Travel", today, "Public commuter pass"));

                // Historical data for trend visualization
                transactionRepo.save(new Transaction("Previous Month Salary", new BigDecimal("5500.00"), TransactionType.INCOME, "Salary", today.minusMonths(1).withDayOfMonth(1), "Payroll"));
                transactionRepo.save(new Transaction("Previous Month Rent", new BigDecimal("1200.00"), TransactionType.EXPENSE, "Rent", today.minusMonths(1).withDayOfMonth(3), "Rent"));
                transactionRepo.save(new Transaction("Previous Month Groceries", new BigDecimal("420.00"), TransactionType.EXPENSE, "Food", today.minusMonths(1).withDayOfMonth(15), "Food"));
            }
        };
    }
}

