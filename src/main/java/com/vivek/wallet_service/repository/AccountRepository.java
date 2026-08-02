package com.vivek.wallet_service.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.vivek.wallet_service.entity.Account;
import com.vivek.wallet_service.entity.User;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id in :accountIds order by account.id asc")
    List<Account> findAllByIdForUpdate(Collection<Long> accountIds);
}
