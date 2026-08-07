package com.vivek.wallet_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vivek.wallet_service.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findFirstByEmailOrderByIdAsc(String email);

    boolean existsByEmail(String email);
}
