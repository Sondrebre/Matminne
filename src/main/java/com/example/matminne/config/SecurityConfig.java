package com.example.matminne.config;

import com.example.matminne.model.*;
import com.example.matminne.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

    @Autowired
    private TilgangsSjekk tilgangsSjekk;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF: cookie-basert token — JavaScript leser XSRF-TOKEN og sender X-XSRF-TOKEN header
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            // ── Sikkerhetshoder ──────────────────────────────────
            .headers(headers -> headers
                // Forhindrer clickjacking
                .frameOptions(frame -> frame.sameOrigin())
                // Forhindrer MIME-sniffing
                .contentTypeOptions(Customizer.withDefaults())
                // XSS-beskyttelse i eldre nettlesere
                .xssProtection(Customizer.withDefaults())
                // Referrer-policy
                .referrerPolicy(ref -> ref
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            // ── Tilgangskontroll ──────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/ingen-tilgang", "/css/**", "/js/**", "/images/**", "/webjars/**", "/manifest.json", "/sw.js", "/icons/**", "/installer-app").permitAll()
                .requestMatchers("/h2-console/**").denyAll()  // Aldri eksponér H2-console
                .anyRequest().authenticated()
            )
            // ── Google OAuth2 innlogging ──────────────────────────
            .oauth2Login(oauth2 -> oauth2
                .successHandler(tilgangsSjekk)
            )
            // ── Utlogging ─────────────────────────────────────────
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            // ── Sesjonssikkerhet ──────────────────────────────────
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
                .maximumSessions(5)
            );
        return http.build();
    }
}
