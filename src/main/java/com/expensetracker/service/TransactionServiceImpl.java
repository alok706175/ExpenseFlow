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
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
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

        // 1. Calculate Aggregated Income and Expense with support for single or dual date bounds
        BigDecimal totalIncome = transactionRepository.sumAmountByTypeAndDateRange(user, TransactionType.INCOME, startDate, endDate);
        BigDecimal totalExpense = transactionRepository.sumAmountByTypeAndDateRange(user, TransactionType.EXPENSE, startDate, endDate);

        totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
        totalExpense = totalExpense != null ? totalExpense : BigDecimal.ZERO;

        summary.setTotalIncome(totalIncome);
        summary.setTotalExpense(totalExpense);
        summary.setBalance(totalIncome.subtract(totalExpense));

        // 2. Category Breakdown with optional single/dual date bounds
        List<Object[]> categoryBreakdown = transactionRepository.findCategoryExpenseBreakdownByDateRange(user, startDate, endDate);

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

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Write UTF-8 BOM so Microsoft Excel on Windows renders UTF-8 characters without corruption
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
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
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate CSV export", e);
            throw new RuntimeException("Failed to generate CSV export: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportTransactionsToPdf(
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

        log.info("Exporting {} transactions to PDF statement for user [ID: {}]", transactions.size(), user.getId());

        // Calculate KPI Totals
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(t.getAmount());
            } else {
                totalExpense = totalExpense.add(t.getAmount());
            }
        }
        BigDecimal netBalance = totalIncome.subtract(totalExpense);

        String currency = (user.getCurrency() != null && !user.getCurrency().trim().isEmpty())
                ? user.getCurrency().trim()
                : "INR";
        DecimalFormat df = new DecimalFormat("#,##0.00");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Color Palette
            Color primaryGreen = new Color(5, 150, 105);      // Tailwind emerald-600
            Color textDark = new Color(15, 23, 42);          // slate-900
            Color textMuted = new Color(100, 116, 139);      // slate-500
            Color borderColor = new Color(226, 232, 240);    // slate-200
            Color bgLight = new Color(248, 250, 252);        // slate-50
            Color tableHeaderBg = new Color(30, 41, 59);     // slate-800
            Color incomeColor = new Color(5, 150, 105);      // emerald-600
            Color expenseColor = new Color(225, 29, 72);     // rose-600

            // Fonts
            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, primaryGreen);
            Font brandSubFont = FontFactory.getFont(FontFactory.HELVETICA, 8, textMuted);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, textDark);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, textDark);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, textMuted);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, textDark);
            Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, textDark);
            Font cellMonoFont = FontFactory.getFont(FontFactory.HELVETICA, 8, textMuted);
            Font incomeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, incomeColor);
            Font expenseFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, expenseColor);
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 7, textMuted);

            // 1. Header Bar: Brand Logo & Title on Left, Statement Info on Right
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100f);
            headerTable.setWidths(new float[]{60f, 40f});

            PdfPCell brandCell = new PdfPCell();
            brandCell.setBorder(Rectangle.NO_BORDER);
            brandCell.addElement(new Paragraph("ExpenseFlow", brandFont));
            brandCell.addElement(new Paragraph("SMART PERSONAL FINANCE & AUDIT REPORT", brandSubFont));
            headerTable.addCell(brandCell);

            PdfPCell metaCell = new PdfPCell();
            metaCell.setBorder(Rectangle.NO_BORDER);
            metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph docTitle = new Paragraph("ACCOUNT STATEMENT", titleFont);
            docTitle.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(docTitle);
            String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
            Paragraph genDate = new Paragraph("Generated: " + generatedAt, brandSubFont);
            genDate.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(genDate);
            headerTable.addCell(metaCell);

            document.add(headerTable);

            // Divider Line
            PdfPTable divider = new PdfPTable(1);
            divider.setWidthPercentage(100f);
            divider.setSpacingBefore(8f);
            divider.setSpacingAfter(10f);
            PdfPCell divCell = new PdfPCell();
            divCell.setFixedHeight(2f);
            divCell.setBackgroundColor(primaryGreen);
            divCell.setBorder(Rectangle.NO_BORDER);
            divider.addCell(divCell);
            document.add(divider);

            // 2. Info Card: User details & Filter Criteria
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100f);
            infoTable.setWidths(new float[]{50f, 50f});
            infoTable.setSpacingAfter(12f);

            // Left Box: User Info
            PdfPCell userBox = new PdfPCell();
            userBox.setBackgroundColor(bgLight);
            userBox.setBorderColor(borderColor);
            userBox.setBorderWidth(1f);
            userBox.setPadding(8f);
            userBox.addElement(new Paragraph("ACCOUNT HOLDER DETAILS", labelFont));
            userBox.addElement(new Paragraph("Name: " + (user.getName() != null ? user.getName() : "Valued User"), valueFont));
            userBox.addElement(new Paragraph("Email: " + user.getEmail(), valueFont));
            userBox.addElement(new Paragraph("Currency: " + currency, valueFont));
            infoTable.addCell(userBox);

            // Right Box: Filter & Period Info
            PdfPCell filterBox = new PdfPCell();
            filterBox.setBackgroundColor(bgLight);
            filterBox.setBorderColor(borderColor);
            filterBox.setBorderWidth(1f);
            filterBox.setPadding(8f);
            filterBox.addElement(new Paragraph("STATEMENT SCOPE", labelFont));
            String dateRangeStr = (startDate != null ? startDate.toString() : "All Past")
                    + "  to  "
                    + (endDate != null ? endDate.toString() : "Present");
            filterBox.addElement(new Paragraph("Period: " + dateRangeStr, valueFont));
            filterBox.addElement(new Paragraph("Category: " + (sanitizedCategory != null ? sanitizedCategory : "All Categories"), valueFont));
            filterBox.addElement(new Paragraph("Type: " + (type != null ? type.name() : "All Types (Income & Expense)"), valueFont));
            infoTable.addCell(filterBox);

            document.add(infoTable);

            // 3. KPI Summary Cards (Income, Expense, Net Balance)
            PdfPTable kpiTable = new PdfPTable(3);
            kpiTable.setWidthPercentage(100f);
            kpiTable.setWidths(new float[]{33.33f, 33.33f, 33.34f});
            kpiTable.setSpacingAfter(14f);

            // KPI Card 1: Total Inflow
            PdfPCell incCell = new PdfPCell();
            incCell.setBackgroundColor(new Color(236, 253, 245)); // emerald-50
            incCell.setBorderColor(new Color(167, 243, 208));    // emerald-200
            incCell.setBorderWidth(1f);
            incCell.setPadding(8f);
            incCell.addElement(new Paragraph("TOTAL INCOME", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, incomeColor)));
            Paragraph incVal = new Paragraph("+ " + currency + " " + df.format(totalIncome), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, incomeColor));
            incCell.addElement(incVal);
            kpiTable.addCell(incCell);

            // KPI Card 2: Total Outflow
            PdfPCell expCell = new PdfPCell();
            expCell.setBackgroundColor(new Color(255, 241, 242)); // rose-50
            expCell.setBorderColor(new Color(254, 205, 211));    // rose-200
            expCell.setBorderWidth(1f);
            expCell.setPadding(8f);
            expCell.addElement(new Paragraph("TOTAL EXPENSE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, expenseColor)));
            Paragraph expVal = new Paragraph("- " + currency + " " + df.format(totalExpense), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, expenseColor));
            expCell.addElement(expVal);
            kpiTable.addCell(expCell);

            // KPI Card 3: Net Balance
            Color balanceColor = netBalance.compareTo(BigDecimal.ZERO) >= 0 ? incomeColor : expenseColor;
            PdfPCell balCell = new PdfPCell();
            balCell.setBackgroundColor(new Color(241, 245, 249)); // slate-100
            balCell.setBorderColor(borderColor);
            balCell.setBorderWidth(1f);
            balCell.setPadding(8f);
            balCell.addElement(new Paragraph("NET CASH FLOW", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, textDark)));
            String sign = netBalance.compareTo(BigDecimal.ZERO) >= 0 ? "+ " : "- ";
            Paragraph balVal = new Paragraph(sign + currency + " " + df.format(netBalance.abs()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, balanceColor));
            balCell.addElement(balVal);
            kpiTable.addCell(balCell);

            document.add(kpiTable);

            // Section Title for Transactions
            Paragraph txHeader = new Paragraph("ITEMIZED TRANSACTIONS (" + transactions.size() + ")", sectionFont);
            txHeader.setSpacingAfter(6f);
            document.add(txHeader);

            // 4. Itemized Transactions Table
            PdfPTable txTable = new PdfPTable(6);
            txTable.setWidthPercentage(100f);
            txTable.setWidths(new float[]{14f, 26f, 15f, 13f, 16f, 16f});

            // Table Header Cells
            String[] headers = new String[]{"Date", "Title", "Category", "Type", "Amount (" + currency + ")", "Notes"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, thFont));
                cell.setBackgroundColor(tableHeaderBg);
                cell.setPadding(6f);
                cell.setBorderColor(tableHeaderBg);
                if (h.startsWith("Amount")) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                } else {
                    cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                }
                txTable.addCell(cell);
            }

            // Table Data Rows
            if (transactions.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No transactions found matching the selected filter criteria.", cellFont));
                emptyCell.setColspan(6);
                emptyCell.setPadding(14f);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setBorderColor(borderColor);
                txTable.addCell(emptyCell);
            } else {
                boolean alternate = false;
                for (Transaction t : transactions) {
                    Color rowBg = alternate ? bgLight : Color.WHITE;
                    alternate = !alternate;

                    // Date
                    PdfPCell dateCell = new PdfPCell(new Phrase(t.getTransactionDate().toString(), cellMonoFont));
                    dateCell.setBackgroundColor(rowBg);
                    dateCell.setBorderColor(borderColor);
                    dateCell.setPadding(5f);
                    txTable.addCell(dateCell);

                    // Title
                    PdfPCell titleCell = new PdfPCell(new Phrase(t.getTitle(), cellFont));
                    titleCell.setBackgroundColor(rowBg);
                    titleCell.setBorderColor(borderColor);
                    titleCell.setPadding(5f);
                    txTable.addCell(titleCell);

                    // Category
                    PdfPCell catCell = new PdfPCell(new Phrase(t.getCategory(), cellFont));
                    catCell.setBackgroundColor(rowBg);
                    catCell.setBorderColor(borderColor);
                    catCell.setPadding(5f);
                    txTable.addCell(catCell);

                    // Type
                    boolean isIncome = t.getType() == TransactionType.INCOME;
                    PdfPCell typeCell = new PdfPCell(new Phrase(t.getType().name(), isIncome ? incomeFont : expenseFont));
                    typeCell.setBackgroundColor(rowBg);
                    typeCell.setBorderColor(borderColor);
                    typeCell.setPadding(5f);
                    txTable.addCell(typeCell);

                    // Amount
                    String formattedAmt = (isIncome ? "+ " : "- ") + df.format(t.getAmount());
                    PdfPCell amtCell = new PdfPCell(new Phrase(formattedAmt, isIncome ? incomeFont : expenseFont));
                    amtCell.setBackgroundColor(rowBg);
                    amtCell.setBorderColor(borderColor);
                    amtCell.setPadding(5f);
                    amtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    txTable.addCell(amtCell);

                    // Notes
                    String notesStr = (t.getNotes() != null && !t.getNotes().trim().isEmpty()) ? t.getNotes().trim() : "-";
                    PdfPCell notesCell = new PdfPCell(new Phrase(notesStr, cellMonoFont));
                    notesCell.setBackgroundColor(rowBg);
                    notesCell.setBorderColor(borderColor);
                    notesCell.setPadding(5f);
                    txTable.addCell(notesCell);
                }
            }

            document.add(txTable);

            // 5. Footer & Legal Disclaimer
            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingBefore(10f);
            document.add(spacer);

            PdfPTable footerTable = new PdfPTable(1);
            footerTable.setWidthPercentage(100f);
            PdfPCell footerCell = new PdfPCell();
            footerCell.setBorder(Rectangle.TOP);
            footerCell.setBorderColor(borderColor);
            footerCell.setBorderWidth(0.5f);
            footerCell.setPaddingTop(6f);

            Paragraph disclaimer = new Paragraph(
                    "This is an official computer-generated statement issued by ExpenseFlow. Generated securely for personal audit, accounting, and tax computation purposes. For any discrepancies, please review your recorded entries in ExpenseFlow.",
                    footerFont
            );
            disclaimer.setAlignment(Element.ALIGN_CENTER);
            footerCell.addElement(disclaimer);
            footerTable.addCell(footerCell);

            document.add(footerTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF statement", e);
            throw new RuntimeException("Failed to generate PDF statement: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public int deleteTransactionsByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        log.info("Deleting transactions for user [ID: {}] between {} and {}", user.getId(), startDate, endDate);
        int deletedCount = transactionRepository.deleteByUserAndDateRange(user, startDate, endDate);
        log.info("Successfully deleted {} transactions for user [ID: {}]", deletedCount, user.getId());
        return deletedCount;
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
