package com.vivek.wallet_service.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    // secret key used for signing + validation
    private final SecretKey secretKey =
            Keys.hmacShaKeyFor(
                    "mysecretkeymysecretkeymysecretkey12"
                            .getBytes(StandardCharsets.UTF_8)
            );

    // generate JWT token
    public String generateToken(String email) {

        return Jwts.builder()

                // user identity
                .subject(email)

                // token creation time
                .issuedAt(new Date())

                // expiry time (1 hour)
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + 1000 * 60 * 60
                        )
                )

                // sign token
                .signWith(secretKey)

                // create token string
                .compact();
    }

    // extract email from token
    public String extractEmail(String token) {

        return extractClaims(token)
                .getSubject();
    }

    // validate token
    public boolean isTokenValid(String token) {

        try {

            extractClaims(token);

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    // parse token + get claims
    private Claims extractClaims(String token) {

        return Jwts.parser()

                // verify using same secret key
                .verifyWith(secretKey)

                // build parser
                .build()

                // parse token
                .parseSignedClaims(token)

                // get payload/body
                .getPayload();
    }
}