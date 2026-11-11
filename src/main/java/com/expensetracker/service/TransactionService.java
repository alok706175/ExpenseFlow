package com.expensetracker.service;

import com.expensetracker.dto.SummaryDTO;
import com.expensetracker.dto.TransactionRequestDTO;
import com.expensetracker.dto.TransactionResponseDTO;
import com.expensetracker.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface TransactionService {

    TransactionResponseDTO createTransaction(TransactionRequestDTO requestDTO);

    TransactionResponseDTO getTransactionById(Long id);

    TransactionResponseDTO updateTransaction(Long id, TransactionRequestDTO requestDTO);

    void deleteTransaction(Long id);

    Page<TransactionResponseDTO> getTransactions(
            LocalDate startDate,
            LocalDate endDate,
            String category,
            TransactionType type,
            String search,
            Pageable pageable
    );

    SummaryDTO getSummary(LocalDate startDate, LocalDate endDate, String monthYear);

    byte[] exportTransactionsToCsv(
            LocalDate startDate,
            LocalDate endDate,
            String category,
            TransactionType type,
            String search
    );
}

