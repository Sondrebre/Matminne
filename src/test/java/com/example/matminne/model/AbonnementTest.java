package com.example.matminne.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nivåene og grensene de gir.
 */
class AbonnementTest {

    @Test
    void prisenFolgerOppskriftsgrensen() {
        assertEquals(29,  Abonnement.NIVA_30.getPrisKroner());
        assertEquals(39,  Abonnement.NIVA_50.getPrisKroner());
        assertEquals(49,  Abonnement.NIVA_80.getPrisKroner());
        assertEquals(59,  Abonnement.NIVA_100.getPrisKroner());
        assertEquals(79,  Abonnement.NIVA_150.getPrisKroner());
        assertEquals(99,  Abonnement.NIVA_200.getPrisKroner());
        assertEquals(199, Abonnement.UBEGRENSET.getPrisKroner());
    }

    @Test
    void grensenePerNiva() {
        assertEquals(10,  Abonnement.GRATIS.getOppskriftGrense());
        assertEquals(30,  Abonnement.NIVA_30.getOppskriftGrense());
        assertEquals(200, Abonnement.NIVA_200.getOppskriftGrense());
        assertTrue(Abonnement.UBEGRENSET.erUbegrenset());
    }

    @Test
    void harPlass_stopperPaGrensen() {
        assertTrue(Abonnement.NIVA_30.harPlass(29));
        assertFalse(Abonnement.NIVA_30.harPlass(30));
        assertFalse(Abonnement.NIVA_30.harPlass(31));
    }

    @Test
    void ubegrenset_harAlltidPlass() {
        assertTrue(Abonnement.UBEGRENSET.harPlass(0));
        assertTrue(Abonnement.UBEGRENSET.harPlass(5000));
    }

    @Test
    void minsteSomRommer_velgerRiktigPlan() {
        assertEquals(Abonnement.NIVA_30,  Abonnement.minsteSomRommer(10));
        assertEquals(Abonnement.NIVA_50,  Abonnement.minsteSomRommer(30));
        assertEquals(Abonnement.NIVA_80,  Abonnement.minsteSomRommer(60));
        assertEquals(Abonnement.NIVA_150, Abonnement.minsteSomRommer(120));
        assertEquals(Abonnement.UBEGRENSET, Abonnement.minsteSomRommer(500));
    }

    @Test
    void betalte_erAlleUtenomGratis_billigstForst() {
        var betalte = Abonnement.betalte();
        assertEquals(7, betalte.size());
        assertFalse(betalte.contains(Abonnement.GRATIS));
        assertEquals(Abonnement.NIVA_30, betalte.get(0));
        assertEquals(Abonnement.UBEGRENSET, betalte.get(betalte.size() - 1));
    }

    @Test
    void prisNokkel_matcherKonfigurasjonen() {
        assertEquals("niva-30", Abonnement.NIVA_30.prisNokkel());
        assertEquals("niva-ubegrenset", Abonnement.UBEGRENSET.prisNokkel());
        assertNull(Abonnement.GRATIS.prisNokkel());
    }

    @Test
    void fraNavn_taalerTullVerdier() {
        assertEquals(Abonnement.NIVA_150, Abonnement.fraNavn("NIVA_150"));
        assertEquals(Abonnement.NIVA_150, Abonnement.fraNavn("niva_150"));
        assertEquals(Abonnement.GRATIS, Abonnement.fraNavn("finnes-ikke"));
        assertEquals(Abonnement.GRATIS, Abonnement.fraNavn(null));
        assertEquals(Abonnement.GRATIS, Abonnement.fraNavn(""));
    }

    @Test
    void grenseTekst_ogPrisTekst() {
        assertEquals("150 oppskrifter", Abonnement.NIVA_150.grenseTekst());
        assertEquals("Ubegrenset", Abonnement.UBEGRENSET.grenseTekst());
        assertEquals("79 kr/mnd", Abonnement.NIVA_150.prisTekst());
        assertEquals("Gratis", Abonnement.GRATIS.prisTekst());
    }

    // ── GRENSEN SLIK BRUKEREN OPPLEVER DEN ────────────────────────

    @Test
    void oppsagtAbonnement_fallerTilbakeTilGratis() {
        Bruker b = new Bruker("Test", "test@test.no");
        b.setAbonnementNiva(Abonnement.NIVA_200);
        b.setHarAbonnement(false);          // oppsagt

        assertEquals(Abonnement.GRATIS, b.gjeldendePlan());
        assertEquals(10, b.oppskriftGrense());
        assertFalse(b.harPlassTilFlere(10));
    }

    @Test
    void aktivtAbonnement_girNivaetsGrense() {
        Bruker b = new Bruker("Test", "test@test.no");
        b.setAbonnementNiva(Abonnement.NIVA_150);
        b.setHarAbonnement(true);

        assertEquals(150, b.oppskriftGrense());
        assertTrue(b.harPlassTilFlere(149));
        assertFalse(b.harPlassTilFlere(150));
    }

    @Test
    void nedgradering_beholderOppskrifterMenSperrerNye() {
        // Hadde 150 lagret, går ned til 30-planen
        Bruker b = new Bruker("Test", "test@test.no");
        b.setAbonnementNiva(Abonnement.NIVA_30);
        b.setHarAbonnement(true);

        // Ingenting slettes, men det er ikke plass til flere
        assertFalse(b.harPlassTilFlere(150));
        assertEquals(30, b.oppskriftGrense());
    }

    @Test
    void nyBruker_erPaGratisplanen() {
        Bruker b = new Bruker("Test", "test@test.no");
        assertEquals(Abonnement.GRATIS, b.gjeldendePlan());
        assertEquals(10, b.oppskriftGrense());
        assertTrue(b.harPlassTilFlere(9));
        assertFalse(b.harPlassTilFlere(10));
    }
}
