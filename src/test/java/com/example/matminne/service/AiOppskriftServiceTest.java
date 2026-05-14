package com.example.matminne.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AiOppskriftService.
 * Tests behavior when API key is missing (no HTTP calls made).
 */
class AiOppskriftServiceTest {

    private AiOppskriftService service;

    @BeforeEach
    void setup() {
        service = new AiOppskriftService();
        // Tom API-nøkkel — simulerer manglende konfigurasjon
        ReflectionTestUtils.setField(service, "apiKey", "");
        service.init();
    }

    @Test
    void hentOgStrukturer_utenApiNøkkel_returnerOppskriftMedFeil() {
        var resultat = service.hentOgStrukturer("https://example.com");
        assertNotNull(resultat);
        // Enten feil-felt satt, eller tittel inneholder feilmelding
        boolean harFeil = (resultat.getFeil() != null && !resultat.getFeil().isEmpty())
                || (resultat.getTittel() != null && resultat.getTittel().toLowerCase().contains("feil"));
        assertTrue(harFeil, "Forventet feilindikasjon i resultatet");
    }

    @Test
    void genererPrisEstimat_utenApiNøkkel_returnerFeilMap() {
        Map<String, Object> resultat = service.genererPrisEstimat("200g pasta\n3 egg", 4);
        assertNotNull(resultat);
        assertTrue(resultat.containsKey("feil"), "Forventet 'feil'-nøkkel i resultatet");
    }

    @Test
    void genererSubstitutt_utenApiNøkkel_returnerFeilmelding() {
        String resultat = service.genererSubstitutt("smør");
        assertNotNull(resultat);
        assertTrue(resultat.startsWith("FEIL"), "Forventet FEIL-prefix: " + resultat);
    }

    @Test
    void genererUkesmeny_utenApiNøkkel_returnerFeilmelding() {
        String resultat = service.genererUkesmeny(3, "", 4, "", 1500, false, "", 30, "", "", "", false);
        assertNotNull(resultat);
        assertTrue(resultat.startsWith("FEIL"), "Forventet FEIL-prefix: " + resultat);
    }
}
