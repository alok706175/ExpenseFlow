package com.expensetracker.controller;

import com.expensetracker.dto.SummaryDTO;
import com.expensetracker.dto.TransactionRequestDTO;
import com.expensetracker.dto.TransactionResponseDTO;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Endpoints for managing user expenses, income, summaries, and CSV data export")
public class TransactionController {

    private final TransactionService transactionService;
    private final AuthService authService;

    public TransactionController(TransactionService transactionService, AuthService authService) {
        this.transactionService = transactionService;
        this.authService = authService;
    }

    @PostMapping
    @Operation(summary = "Create a new transaction", description = "Adds a new income or expense transaction for the authenticated user")
    public ResponseEntity<TransactionResponseDTO> createTransaction(@Valid @RequestBody TransactionRequestDTO requestDTO) {
        User user = authService.getCurrentAuthenticatedUser();
        TransactionResponseDTO created = transactionService.createTransaction(requestDTO, user);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID", description = "Retrieves a single transaction by ID owned by current user")
    public ResponseEntity<TransactionResponseDTO> getTransactionById(@PathVariable Long id) {
        User user = authService.getCurrentAuthenticatedUser();
        return ResponseEntity.ok(transactionService.getTransactionById(id, user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing transaction", description = "Updates details of a transaction owned by current user")
    public ResponseEntity<TransactionResponseDTO> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequestDTO requestDTO) {
        User user = authService.getCurrentAuthenticatedUser();
        return ResponseEntity.ok(transactionService.updateTransaction(id, requestDTO, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete transaction", description = "Permanently removes a transaction owned by current user")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        User user = authService.getCurrentAuthenticatedUser();
        transactionService.deleteTransaction(id, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List transactions with filters and pagination",
               description = "Filter by date range, category, type, and search keyword for the current user")
    public ResponseEntity<Page<TransactionResponseDTO>> getTransactions(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Category name")
            @RequestParam(required = false) String category,

            @Parameter(description = "Transaction type (INCOME or EXPENSE)")
            @RequestParam(required = false) TransactionType type,

            @Parameter(description = "Keyword search in title or notes")
            @RequestParam(required = false) String search,

            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Sort property (e.g., transactionDate, amount, id)")
            @RequestParam(defaultValue = "transactionDate") String sortBy,

            @Parameter(description = "Sort direction (ASC or DESC)")
            @RequestParam(defaultValue = "DESC") @NonNull Sort.Direction direction) {

        User user = authService.getCurrentAuthenticatedUser();
        Sort.Direction sortDir = Objects.requireNonNull(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy).and(Sort.by(sortDir, "id")));
        Page<TransactionResponseDTO> result = transactionService.getTransactions(
                user, startDate, endDate, category, type, search, pageable
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/summary")
    @Operation(summary = "Financial summary & budget analytics",
               description = "Aggregated totals, net balance, category breakdown, optimized trends, and budget alerts")
    public ResponseEntity<SummaryDTO> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String monthYear) {

        User user = authService.getCurrentAuthenticatedUser();
        SummaryDTO summary = transactionService.getSummary(user, startDate, endDate, monthYear);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export transactions to date-stamped CSV file",
               description = "Generates and downloads a CSV spreadsheet applying the provided active filters")
    public ResponseEntity<byte[]> exportToCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String search) {

        User user = authService.getCurrentAuthenticatedUser();
        byte[] csvBytes = transactionService.exportTransactionsToCsv(user, startDate, endDate, category, type, search);

        String filename = "expenses_export_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Export bank-grade PDF statement report",
               description = "Generates and downloads a clean, date-stamped PDF statement for personal audit and tax reporting")
    public ResponseEntity<byte[]> exportToPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String search) {

        User user = authService.getCurrentAuthenticatedUser();
        byte[] pdfBytes = transactionService.exportTransactionsToPdf(user, startDate, endDate, category, type, search);

        String filename = "expenses_statement_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";
        MediaType pdfMediaType = Objects.requireNonNull(MediaType.APPLICATION_PDF);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(pdfMediaType)
                .body(pdfBytes);
    }

    @DeleteMapping("/history")
    @Operation(summary = "Bulk delete transaction history by date range",
               description = "Permanently deletes user transactions matching the specified start and end date range")
    public ResponseEntity<Map<String, Object>> deleteHistory(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        User user = authService.getCurrentAuthenticatedUser();
        int deletedCount = transactionService.deleteTransactionsByDateRange(user, startDate, endDate);

        return ResponseEntity.ok(Map.of(
                "deletedCount", deletedCount,
                "message", deletedCount + " transaction(s) deleted successfully."
        ));
    }
}
