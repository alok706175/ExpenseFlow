package com.expensetracker.service;

import com.expensetracker.dto.MonthlyTrendDTO;
import com.expensetracker.dto.SummaryDTO;
import com.expensetracker.dto.TransactionRequestDTO;
import com.expensetracker.dto.TransactionResponseDTO;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.TransactionRepository;
import com.opencsv.CSVWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository, BudgetRepository budgetRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    @Override
    public TransactionResponseDTO createTransaction(TransactionRequestDTO requestDTO) {
        Transaction transaction = mapToEntity(requestDTO);
        Transaction saved = transactionRepository.save(transaction);
        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponseDTO getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return mapToDTO(transaction);
    }

    @Override
    public TransactionResponseDTO updateTransaction(Long id, TransactionRequestDTO requestDTO) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        transaction.setTitle(requestDTO.getTitle().trim());
        transaction.setAmount(requestDTO.getAmount());
        transaction.setType(requestDTO.getType());
        transaction.setCategory(requestDTO.getCategory().trim());
        transaction.setTransactionDate(requestDTO.getTransactionDate());
        transaction.setNotes(requestDTO.getNotes() != null ? requestDTO.getNotes().trim() : null);

        Transaction updated = transactionRepository.save(transaction);
        return mapToDTO(updated);
    }

    @Override
    public void deleteTransaction(Long id) {
        if (!transactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Transaction not found with id: " + id);
        }
        transactionRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> getTransactions(
            LocalDate startDate,
            LocalDate endDate,
            String category,
            TransactionType type,
            String search,
            Pageable pageable) {

        String sanitizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String sanitizedCategory = (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL"))
                ? category.trim()
                : null;

        return transactionRepository.findWithFilters(startDate, endDate, sanitizedCategory, type, sanitizedSearch, pageable)
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryDTO getSummary(LocalDate startDate, LocalDate endDate, String monthYear) {
        SummaryDTO summary = new SummaryDTO();

        // 1. Calculate Income and Expense
        BigDecimal totalIncome;
        BigDecimal totalExpense;

        if (startDate != null && endDate != null) {
            totalIncome = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.INCOME, startDate, endDate);
            totalExpense = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.EXPENSE, startDate, endDate);
        } else {
            totalIncome = transactionRepository.sumAmountByType(TransactionType.INCOME);
            totalExpense = transactionRepository.sumAmountByType(TransactionType.EXPENSE);
        }

        totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        totalExpense = totalExpense != null ? totalExpense : BigDecimal.ZERO;

        summary.setTotalIncome(totalIncome);
        summary.setTotalExpense(totalExpense);
        summary.setBalance(totalIncome.subtract(totalExpense));

        // 2. Category Breakdown
        List<Object[]> categoryBreakdown = (startDate != null && endDate != null)
                ? transactionRepository.findCategoryExpenseBreakdownByDateRange(startDate, endDate)
                : transactionRepository.findCategoryExpenseBreakdown();

        Map<String, BigDecimal> categoryMap = new LinkedHashMap<>();
        String topCategory = "N/A";
        BigDecimal topAmount = BigDecimal.ZERO;

        for (Object[] row : categoryBreakdown) {
            String cat = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            categoryMap.put(cat, amt);

            if (amt.compareTo(topAmount) > 0) {
                topAmount = amt;
                topCategory = cat;
            }
        }
        summary.setCategoryExpenses(categoryMap);
        summary.setTopSpendingCategory(topCategory);

        // 3. Monthly Trends (Past 6 months)
        YearMonth currentYm = (monthYear != null && !monthYear.trim().isEmpty())
                ? YearMonth.parse(monthYear.trim())
                : YearMonth.now();

        summary.setCurrentMonth(currentYm.toString());

        List<MonthlyTrendDTO> trends = new ArrayList<>();
        DateTimeFormatter ymFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 5; i >= 0; i--) {
            YearMonth targetMonth = currentYm.minusMonths(i);
            LocalDate start = targetMonth.atDay(1);
            LocalDate end = targetMonth.atEndOfMonth();

            BigDecimal mIncome = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.INCOME, start, end);
            BigDecimal mExpense = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.EXPENSE, start, end);

            trends.add(new MonthlyTrendDTO(
                    targetMonth.format(ymFormatter),
                    mIncome != null ? mIncome : BigDecimal.ZERO,
                    mExpense != null ? mExpense : BigDecimal.ZERO
            ));
        }
        summary.setMonthlyTrends(trends);

        // 4. Budget & Threshold Alert for Current Month
        LocalDate curMonthStart = currentYm.atDay(1);
        LocalDate curMonthEnd = currentYm.atEndOfMonth();
        BigDecimal currentMonthExp = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.EXPENSE, curMonthStart, curMonthEnd
        );
        currentMonthExp = currentMonthExp != null ? currentMonthExp : BigDecimal.ZERO;
        summary.setCurrentMonthExpense(currentMonthExp);

        Optional<Budget> budgetOpt = budgetRepository.findByMonthYear(currentYm.toString());
        if (budgetOpt.isPresent() && budgetOpt.get().getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal limit = budgetOpt.get().getMonthlyLimit();
            summary.setMonthlyBudgetLimit(limit);

            double percentage = currentMonthExp
                    .divide(limit, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            summary.setBudgetUsedPercentage(Math.round(percentage * 100.0) / 100.0);

            if (percentage >= 100.0) {
                summary.setAlertStatus("EXCEEDED");
                summary.setAlertMessage(String.format("Critical: Monthly budget exceeded! Used %.1f%% of your $%.2f limit.", percentage, limit));
            } else if (percentage >= 80.0) {
                summary.setAlertStatus("WARNING");
                summary.setAlertMessage(String.format("Warning: Approaching budget limit. Used %.1f%% of your $%.2f limit.", percentage, limit));
            } else {
                summary.setAlertStatus("OK");
                summary.setAlertMessage(String.format("On track: Used %.1f%% of your $%.2f monthly budget.", percentage, limit));
            }
        } else {
            summary.setMonthlyBudgetLimit(BigDecimal.ZERO);
            summary.setBudgetUsedPercentage(0.0);
            summary.setAlertStatus("NOT_SET");
            summary.setAlertMessage("No budget limit configured for this month.");
        }

        return summary;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportTransactionsToCsv(
            LocalDate startDate,
            LocalDate endDate,
            String category,
            TransactionType type,
            String search) {

        String sanitizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String sanitizedCategory = (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL"))
                ? category.trim()
                : null;

        List<Transaction> transactions = transactionRepository.findAllWithFilters(
                startDate, endDate, sanitizedCategory, type, sanitizedSearch
        );

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter writer = new CSVWriter(osw)) {

            // CSV Header
            writer.writeNext(new String[]{"ID", "Title", "Amount", "Type", "Category", "Transaction Date", "Notes", "Created At"});

            // Rows
            for (Transaction t : transactions) {
                writer.writeNext(new String[]{
                        String.valueOf(t.getId()),
                        t.getTitle(),
                        t.getAmount().toPlainString(),
                        t.getType().name(),
                        t.getCategory(),
                        t.getTransactionDate().toString(),
                        t.getNotes() != null ? t.getNotes() : "",
                        t.getCreatedAt() != null ? t.getCreatedAt().toString() : ""
                });
            }

            writer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CSV export: " + e.getMessage(), e);
        }
    }

    private TransactionResponseDTO mapToDTO(Transaction entity) {
        return new TransactionResponseDTO(
                entity.getId(),
                entity.getTitle(),
                entity.getAmount(),
                entity.getType(),
                entity.getCategory(),
                entity.getTransactionDate(),
                entity.getNotes(),
                entity.getCreatedAt()
        );
    }

    private Transaction mapToEntity(TransactionRequestDTO dto) {
        return new Transaction(
                dto.getTitle().trim(),
                dto.getAmount(),
                dto.getType(),
                dto.getCategory().trim(),
                dto.getTransactionDate(),
                dto.getNotes() != null ? dto.getNotes().trim() : null
        );
    }
}

