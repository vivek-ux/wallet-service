package com.vivek.wallet_service.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vivek.wallet_service.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // get Authorization header
        String authHeader =
                request.getHeader("Authorization");

        // if no token exists
        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {

            // continue request
            filterChain.doFilter(request, response);
            return;
        }

        // remove "Bearer "
        String token =
                authHeader.substring(7);

        try {

            // validate token
            boolean valid =
                    jwtService.isTokenValid(token);

            if (!valid) {

                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

                response.getWriter()
                        .write("Invalid token");

                return;
            }

            // extract user email
            String email =
                    jwtService.extractEmail(token);

            // create authentication object
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            Collections.emptyList()
                    );

            // tell Spring user authenticated
            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authToken);

        } catch (Exception e) {

            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );

            response.getWriter()
                    .write("Invalid JWT token");

            return;
        }

        // continue request
        filterChain.doFilter(request, response);
    }
}