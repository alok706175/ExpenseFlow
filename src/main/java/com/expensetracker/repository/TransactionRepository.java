package com.expensetracker.repository;

import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionType;
import com.expensetracker.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndUser(Long id, User user);

    boolean existsByIdAndUser(Long id, User user);

    @Query("SELECT t FROM Transaction t WHERE t.user = :user AND " +
           "(:startDate IS NULL OR t.transactionDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transactionDate <= :endDate) AND " +
           "(:category IS NULL OR :category = '' OR LOWER(t.category) = LOWER(:category)) AND " +
           "(:type IS NULL OR t.type = :type) AND " +
           "(:search IS NULL OR :search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.notes) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Transaction> findWithFilters(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("category") String category,
            @Param("type") TransactionType type,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT t FROM Transaction t WHERE t.user = :user AND " +
           "(:startDate IS NULL OR t.transactionDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transactionDate <= :endDate) AND " +
           "(:category IS NULL OR :category = '' OR LOWER(t.category) = LOWER(:category)) AND " +
           "(:type IS NULL OR t.type = :type) AND " +
           "(:search IS NULL OR :search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.notes) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY t.transactionDate DESC, t.id DESC")
    List<Transaction> findAllWithFilters(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("category") String category,
            @Param("type") TransactionType type,
            @Param("search") String search
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user = :user AND t.type = :type")
    BigDecimal sumAmountByType(@Param("user") User user, @Param("type") TransactionType type);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user = :user AND t.type = :type AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate")
    BigDecimal sumAmountByTypeAndDateRange(
            @Param("user") User user,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t.category, COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user = :user AND t.type = com.expensetracker.entity.TransactionType.EXPENSE GROUP BY t.category ORDER BY SUM(t.amount) DESC")
    List<Object[]> findCategoryExpenseBreakdown(@Param("user") User user);

    @Query("SELECT t.category, COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user = :user AND t.type = com.expensetracker.entity.TransactionType.EXPENSE AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate GROUP BY t.category ORDER BY SUM(t.amount) DESC")
    List<Object[]> findCategoryExpenseBreakdownByDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t.type, t.transactionDate, t.amount FROM Transaction t WHERE t.user = :user AND t.transactionDate >= :startDate")
    List<Object[]> findTrendsDataSince(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate
    );

    @Modifying
    @Query("DELETE FROM Transaction t WHERE t.user = :user AND " +
           "(:startDate IS NULL OR t.transactionDate >= :startDate) AND " +
           "(:endDate IS NULL OR t.transactionDate <= :endDate)")
    int deleteByUserAndDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
