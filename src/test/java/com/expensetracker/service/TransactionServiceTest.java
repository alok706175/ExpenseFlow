package com.expensetracker.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("Alok", "alok@example.com", "hashed_pwd", "INR");
        testUser.setId(1L);
    }

    @Test
    @DisplayName("Should successfully create a transaction for user")
    void testCreateTransaction() {
        TransactionRequestDTO requestDTO = new TransactionRequestDTO(
                "Coffee & Snack",
                new BigDecimal("250.00"),
                TransactionType.EXPENSE,
                "Food",
                LocalDate.now(),
                "Coffee shop"
        );

        Transaction savedTx = new Transaction(
                testUser,
                requestDTO.getTitle(),
                requestDTO.getAmount(),
                requestDTO.getType(),
                requestDTO.getCategory(),
                requestDTO.getTransactionDate(),
                requestDTO.getNotes()
        );
        savedTx.setId(10L);

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTx);

        TransactionResponseDTO response = transactionService.createTransaction(requestDTO, testUser);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Coffee & Snack", response.getTitle());
        assertEquals(new BigDecimal("250.00"), response.getAmount());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when transaction does not belong to user")
    void testGetTransactionNotFoundOrUnauthorized() {
        when(transactionRepository.findByIdAndUser(99L, testUser)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.getTransactionById(99L, testUser));
    }

    @Test
    @DisplayName("Should calculate budget alert as EXCEEDED when expenses cross 100%")
    void testBudgetAlertExceeded() {
        LocalDate today = LocalDate.now();
        YearMonth currentYm = YearMonth.from(today);

        when(transactionRepository.sumAmountByType(testUser, TransactionType.INCOME)).thenReturn(new BigDecimal("50000.00"));
        when(transactionRepository.sumAmountByType(testUser, TransactionType.EXPENSE)).thenReturn(new BigDecimal("35000.00"));
        when(transactionRepository.findCategoryExpenseBreakdown(testUser)).thenReturn(Collections.emptyList());
        when(transactionRepository.findTrendsDataSince(eq(testUser), any(LocalDate.class))).thenReturn(Collections.emptyList());

        // Target month expense is 32000 against a budget of 30000 (exceeded)
        when(transactionRepository.sumAmountByTypeAndDateRange(
                eq(testUser),
                eq(TransactionType.EXPENSE),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(new BigDecimal("32000.00"));

        Budget budget = new Budget(testUser, currentYm.toString(), new BigDecimal("30000.00"));
        when(budgetRepository.findByUserAndMonthYear(testUser, currentYm.toString())).thenReturn(Optional.of(budget));

        SummaryDTO summary = transactionService.getSummary(testUser, null, null, currentYm.toString());

        assertNotNull(summary);
        assertEquals("EXCEEDED", summary.getAlertStatus());
        assertTrue(summary.getBudgetUsedPercentage() > 100.0);
    }
}
