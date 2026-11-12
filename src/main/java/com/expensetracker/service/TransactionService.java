package com.expensetracker.service;

import com.expensetracker.dto.SummaryDTO;
import com.expensetracker.dto.TransactionRequestDTO;
import com.expensetracker.dto.TransactionResponseDTO;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface TransactionService {

    TransactionResponseDTO createTransaction(TransactionRequestDTO requestDTO, User user);

    TransactionResponseDTO getTransactionById(Long id, User user);

    TransactionResponseDTO updateTransaction(Long id, TransactionRequestDTO requestDTO, User user);

    void deleteTransaction(Long id, User user);

    Page<TransactionResponseDTO> getTransactions(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            String category,
            TransactionType type,
            String search,
            Pageable pageable
    );

    SummaryDTO getSummary(User user, LocalDate startDate, LocalDate endDate, String monthYear);

    byte[] exportTransactionsToCsv(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            String category,
            TransactionType type,
            String search
    );

    byte[] exportTransactionsToPdf(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            String category,
            TransactionType type,
            String search
    );

    int deleteTransactionsByDateRange(User user, LocalDate startDate, LocalDate endDate);
}
