package com.example.matminne.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Kjører enkle schema-migrasjoner ved oppstart for å legge til nye kolonner
 * i eksisterende databaser uten å miste data.
 */
@Component
public class DatabaseMigrasjon {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrasjon.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrer() {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE brukere ADD COLUMN IF NOT EXISTS har_abonnement BOOLEAN DEFAULT FALSE"
            );
            jdbcTemplate.execute(
                "ALTER TABLE brukere ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255)"
            );
            log.info("Database-migrasjoner fullført");
        } catch (Exception e) {
            log.warn("Migrasjonsadvarsel (kan ignoreres hvis kolonner allerede finnes): {}", e.getMessage());
        }
    }
}
