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
            jdbcTemplate.execute(
                "ALTER TABLE brukere ADD COLUMN IF NOT EXISTS har_godtatt_vilkar BOOLEAN DEFAULT FALSE"
            );
            // Engangsreset: fjern alle eksisterende abonnenter (gamle test-data)
            jdbcTemplate.execute("UPDATE brukere SET har_abonnement = FALSE");

            // ── Skaperprogram: Stripe Connect og betalte samlinger ──
            jdbcTemplate.execute(
                "ALTER TABLE brukere ADD COLUMN IF NOT EXISTS stripe_connect_id VARCHAR(255)"
            );
            jdbcTemplate.execute(
                "ALTER TABLE brukere ADD COLUMN IF NOT EXISTS connect_klar BOOLEAN DEFAULT FALSE"
            );
            jdbcTemplate.execute(
                "ALTER TABLE samlinger ADD COLUMN IF NOT EXISTS pris INTEGER"
            );
            jdbcTemplate.execute(
                "ALTER TABLE samlinger ADD COLUMN IF NOT EXISTS er_publisert BOOLEAN DEFAULT FALSE"
            );
            jdbcTemplate.execute(
                "ALTER TABLE samlinger ADD COLUMN IF NOT EXISTS bilde_url TEXT"
            );

            // ── Godkjenningsløp for betalte kokebøker ──
            jdbcTemplate.execute(
                "ALTER TABLE samlinger ADD COLUMN IF NOT EXISTS status VARCHAR(20)"
            );
            jdbcTemplate.execute(
                "ALTER TABLE samlinger ADD COLUMN IF NOT EXISTS avslagsgrunn VARCHAR(500)"
            );
            jdbcTemplate.execute(
                "ALTER TABLE samlinger ADD COLUMN IF NOT EXISTS dato_sendt_inn TIMESTAMP"
            );
            jdbcTemplate.execute(
                "ALTER TABLE samlinger ADD COLUMN IF NOT EXISTS dato_behandlet TIMESTAMP"
            );

            // Overgang fra er_publisert-boolean til status: alt som var
            // publisert regnes som godkjent, resten som utkast.
            migrerPublisertTilStatus();

            // ── Nivådelt abonnement ──
            jdbcTemplate.execute(
                "ALTER TABLE brukere ADD COLUMN IF NOT EXISTS abonnement_niva VARCHAR(20)"
            );
            // Eksisterende abonnenter hadde ubegrenset kokebok, så de beholder det
            jdbcTemplate.execute(
                "UPDATE brukere SET abonnement_niva = 'UBEGRENSET' "
              + "WHERE abonnement_niva IS NULL AND har_abonnement = TRUE"
            );
            jdbcTemplate.execute(
                "UPDATE brukere SET abonnement_niva = 'GRATIS' WHERE abonnement_niva IS NULL"
            );

            log.info("Database-migrasjoner fullført");
        } catch (Exception e) {
            log.warn("Migrasjonsadvarsel (kan ignoreres hvis kolonner allerede finnes): {}", e.getMessage());
        }
    }

    /**
     * Fyller status for rader som mangler den. Kjøres i egen try/catch fordi
     * er_publisert-kolonnen ikke finnes i helt nye databaser — da er det
     * ingenting å migrere, og statusen settes av entiteten.
     */
    private void migrerPublisertTilStatus() {
        try {
            jdbcTemplate.execute(
                "UPDATE samlinger SET status = 'GODKJENT' WHERE status IS NULL AND er_publisert = TRUE");
            jdbcTemplate.execute(
                "UPDATE samlinger SET status = 'UTKAST' WHERE status IS NULL");
            log.info("Statusmigrering av samlinger fullført");
        } catch (Exception e) {
            // Ny database uten er_publisert — sett status på alt som mangler den
            try {
                jdbcTemplate.execute(
                    "UPDATE samlinger SET status = 'UTKAST' WHERE status IS NULL");
            } catch (Exception ignorert) {
                log.debug("Ingen samlinger å migrere status for");
            }
        }
    }
}
