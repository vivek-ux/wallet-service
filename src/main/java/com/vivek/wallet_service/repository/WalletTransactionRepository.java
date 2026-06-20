package com.vivek.wallet_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.vivek.wallet_service.entity.User;
import com.vivek.wallet_service.entity.WalletTransaction;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    @Query("""
            select walletTransaction
            from WalletTransaction walletTransaction
            where walletTransaction.fromAccount.user = :user
               or walletTransaction.toAccount.user = :user
            order by walletTransaction.createdAt desc
            """)
    List<WalletTransaction> findTransactionHistory(User user);

    @Query("""
            select count(walletTransaction)
            from WalletTransaction walletTransaction
            where walletTransaction.fromAccount.user = :user
               or walletTransaction.toAccount.user = :user
            """)
    long countTransactionHistory(User user);
}
