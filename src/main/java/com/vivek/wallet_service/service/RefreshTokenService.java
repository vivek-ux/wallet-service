package com.vivek.wallet_service.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vivek.wallet_service.entity.RefreshToken;
import com.vivek.wallet_service.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private static final long REFRESH_TOKEN_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 Days

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        String tokenFamily = UUID.randomUUID().toString();
        return generateAndSaveToken(userId, tokenFamily);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Reuse detection: If used, kill the family
        if (refreshToken.isUsed()) {
            refreshTokenRepository.deleteByTokenFamily(refreshToken.getTokenFamily());
            throw new RuntimeException("Security alert: Token reuse detected. Session terminated.");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token expired");
        }

        refreshToken.setUsed(true);
        refreshTokenRepository.save(refreshToken);

        return generateAndSaveToken(refreshToken.getUserId(), refreshToken.getTokenFamily());
    }

    private RefreshToken generateAndSaveToken(Long userId, String tokenFamily) {
        String tokenString = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(REFRESH_TOKEN_VALIDITY_MS);

        RefreshToken refreshToken = new RefreshToken(tokenString, userId, tokenFamily, expiryDate);
        return refreshTokenRepository.save(refreshToken);
    }
}
