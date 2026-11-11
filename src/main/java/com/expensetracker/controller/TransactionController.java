package com.expensetracker.controller;

import com.expensetracker.dto.SummaryDTO;
import com.expensetracker.dto.TransactionRequestDTO;
import com.expensetracker.dto.TransactionResponseDTO;
import com.expensetracker.entity.TransactionType;
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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Endpoints for managing expenses, income, summaries, and CSV data export")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(summary = "Create a new transaction", description = "Adds a new income or expense transaction")
    public ResponseEntity<TransactionResponseDTO> createTransaction(@Valid @RequestBody TransactionRequestDTO requestDTO) {
        TransactionResponseDTO created = transactionService.createTransaction(requestDTO);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID", description = "Retrieves a single transaction by its primary key")
    public ResponseEntity<TransactionResponseDTO> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing transaction", description = "Updates details of an existing transaction")
    public ResponseEntity<TransactionResponseDTO> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequestDTO requestDTO) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, requestDTO));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete transaction", description = "Permanently removes a transaction record")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List transactions with filters and pagination",
               description = "Filter by date range, category, type, and keyword search across title and notes")
    public ResponseEntity<Page<TransactionResponseDTO>> getTransactions(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Category name (e.g. Food, Rent, Salary)")
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
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy).and(Sort.by(direction, "id")));
        Page<TransactionResponseDTO> result = transactionService.getTransactions(
                startDate, endDate, category, type, search, pageable
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/summary")
    @Operation(summary = "Financial summary & budget analytics",
               description = "Returns aggregated totals, net balance, category breakdown, monthly trends, and budget alerts")
    public ResponseEntity<SummaryDTO> getSummary(
            @Parameter(description = "Start date filter (optional)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "End date filter (optional)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Budget month (YYYY-MM), defaults to current month")
            @RequestParam(required = false) String monthYear) {

        SummaryDTO summary = transactionService.getSummary(startDate, endDate, monthYear);
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

        byte[] csvBytes = transactionService.exportTransactionsToCsv(startDate, endDate, category, type, search);

        String filename = "expenses_export_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }
}

