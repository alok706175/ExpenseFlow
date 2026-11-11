package com.expensetracker.service;

import com.expensetracker.dto.MonthlyTrendDTO;
import com.expensetracker.dto.SummaryDTO;
import com.expensetracker.dto.TransactionRequestDTO;
import com.expensetracker.dto.TransactionResponseDTO;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.TransactionRepository;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository, BudgetRepository budgetRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    @Override
    public TransactionResponseDTO createTransaction(TransactionRequestDTO requestDTO, User user) {
        Transaction transaction = mapToEntity(requestDTO, user);
        Transaction saved = transactionRepository.save(transaction);
        log.info("Created transaction [ID: {}] for user [ID: {}]", saved.getId(), user.getId());
        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponseDTO getTransactionById(Long id, User user) {
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found or access denied for id: " + id));
        return mapToDTO(transaction);
    }

    @Override
    public TransactionResponseDTO updateTransaction(Long id, TransactionRequestDTO requestDTO, User user) {
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found or access denied for id: " + id));

        transaction.setTitle(requestDTO.getTitle().trim());
        transaction.setAmount(requestDTO.getAmount());
        transaction.setType(requestDTO.getType());
        transaction.setCategory(requestDTO.getCategory().trim());
        transaction.setTransactionDate(requestDTO.getTransactionDate());
        transaction.setNotes(requestDTO.getNotes() != null ? requestDTO.getNotes().trim() : null);

        Transaction updated = transactionRepository.save(transaction);
        log.info("Updated transaction [ID: {}] for user [ID: {}]", updated.getId(), user.getId());
        return mapToDTO(updated);
    }

    @Override
    public void deleteTransaction(Long id, User user) {
        Transaction transaction = transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found or access denied for id: " + id));

        transactionRepository.delete(transaction);
        log.info("Deleted transaction [ID: {}] for user [ID: {}]", id, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> getTransactions(
            User user,
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

        return transactionRepository.findWithFilters(user, startDate, endDate, sanitizedCategory, type, sanitizedSearch, pageable)
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryDTO getSummary(User user, LocalDate startDate, LocalDate endDate, String monthYear) {
        SummaryDTO summary = new SummaryDTO();

        // 1. Calculate Aggregated Income and Expense
        BigDecimal totalIncome;
        BigDecimal totalExpense;

        if (startDate != null && endDate != null) {
            totalIncome = transactionRepository.sumAmountByTypeAndDateRange(user, TransactionType.INCOME, startDate, endDate);
            totalExpense = transactionRepository.sumAmountByTypeAndDateRange(user, TransactionType.EXPENSE, startDate, endDate);
        } else {
            totalIncome = transactionRepository.sumAmountByType(user, TransactionType.INCOME);
            totalExpense = transactionRepository.sumAmountByType(user, TransactionType.EXPENSE);
        }

        totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        totalExpense = totalExpense != null ? totalExpense : BigDecimal.ZERO;

        summary.setTotalIncome(totalIncome);
        summary.setTotalExpense(totalExpense);
        summary.setBalance(totalIncome.subtract(totalExpense));

        // 2. Category Breakdown
        List<Object[]> categoryBreakdown = (startDate != null && endDate != null)
                ? transactionRepository.findCategoryExpenseBreakdownByDateRange(user, startDate, endDate)
                : transactionRepository.findCategoryExpenseBreakdown(user);

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

        // 3. OPTIMIZED Monthly Trends (Single Query replacing 12 separate DB calls!)
        YearMonth currentYm = (monthYear != null && !monthYear.trim().isEmpty())
                ? YearMonth.parse(monthYear.trim())
                : YearMonth.now();

        summary.setCurrentMonth(currentYm.toString());

        YearMonth earliestMonth = currentYm.minusMonths(5);
        LocalDate sixMonthsAgoStart = earliestMonth.atDay(1);

        List<Object[]> rawTrendRows = transactionRepository.findTrendsDataSince(user, sixMonthsAgoStart);

        // Group rows in memory: YYYY-MM -> [IncomeSum, ExpenseSum]
        Map<String, BigDecimal[]> monthlyAggregates = new HashMap<>();
        DateTimeFormatter ymFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Object[] row : rawTrendRows) {
            TransactionType type = (TransactionType) row[0];
            LocalDate txDate = (LocalDate) row[1];
            BigDecimal amt = (BigDecimal) row[2];

            String ym = txDate.format(ymFormatter);
            BigDecimal[] sums = monthlyAggregates.computeIfAbsent(ym, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            if (type == TransactionType.INCOME) {
                sums[0] = sums[0].add(amt);
            } else if (type == TransactionType.EXPENSE) {
                sums[1] = sums[1].add(amt);
            }
        }

        List<MonthlyTrendDTO> trends = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth targetMonth = currentYm.minusMonths(i);
            String targetYmStr = targetMonth.format(ymFormatter);

            BigDecimal[] sums = monthlyAggregates.getOrDefault(targetYmStr, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            trends.add(new MonthlyTrendDTO(targetYmStr, sums[0], sums[1]));
        }
        summary.setMonthlyTrends(trends);

        // 4. Budget & Threshold Alert for Target Month
        LocalDate curMonthStart = currentYm.atDay(1);
        LocalDate curMonthEnd = currentYm.atEndOfMonth();
        BigDecimal currentMonthExp = transactionRepository.sumAmountByTypeAndDateRange(
                user, TransactionType.EXPENSE, curMonthStart, curMonthEnd
        );
        currentMonthExp = currentMonthExp != null ? currentMonthExp : BigDecimal.ZERO;
        summary.setCurrentMonthExpense(currentMonthExp);

        Optional<Budget> budgetOpt = budgetRepository.findByUserAndMonthYear(user, currentYm.toString());
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
                summary.setAlertMessage(String.format("Critical: Monthly budget exceeded! Used %.1f%% of your limit.", percentage));
            } else if (percentage >= 80.0) {
                summary.setAlertStatus("WARNING");
                summary.setAlertMessage(String.format("Warning: Approaching budget limit. Used %.1f%% of your limit.", percentage));
            } else {
                summary.setAlertStatus("OK");
                summary.setAlertMessage(String.format("On track: Used %.1f%% of your monthly budget.", percentage));
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
            User user,
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
                user, startDate, endDate, sanitizedCategory, type, sanitizedSearch
        );

        log.info("Exporting {} transactions to CSV for user [ID: {}]", transactions.size(), user.getId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter writer = new CSVWriter(osw)) {

            writer.writeNext(new String[]{"ID", "Title", "Amount", "Currency", "Type", "Category", "Transaction Date", "Notes", "Created At"});

            for (Transaction t : transactions) {
                writer.writeNext(new String[]{
                        String.valueOf(t.getId()),
                        t.getTitle(),
                        t.getAmount().toPlainString(),
                        user.getCurrency(),
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
            log.error("Failed to generate CSV export", e);
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

    private Transaction mapToEntity(TransactionRequestDTO dto, User user) {
        return new Transaction(
                user,
                dto.getTitle().trim(),
                dto.getAmount(),
                dto.getType(),
                dto.getCategory().trim(),
                dto.getTransactionDate(),
                dto.getNotes() != null ? dto.getNotes().trim() : null
        );
    }
}
