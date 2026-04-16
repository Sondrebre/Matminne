package com.example.matminne.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Oppskrift model — validering og grensetilfeller.
 */
class OppskriftValidationTest {

    @Test
    void nyOppskrift_alleStringFelterErNull() {
        Oppskrift o = new Oppskrift();
        assertNull(o.getTittel());
        assertNull(o.getIngredienser());
        assertNull(o.getFremgangsmate());
        assertNull(o.getBildeUrl());
        assertNull(o.getKategori());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Middag", "Frokost", "Lunsj", "Dessert", "Snacks"})
    void setKategori_gyldigeKategorier_lagresRiktig(String kategori) {
        Oppskrift o = new Oppskrift();
        o.setKategori(kategori);
        assertEquals(kategori, o.getKategori());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 6, 10, 20})
    void setPorsjoner_positiveTall_lagresRiktig(int porsjoner) {
        Oppskrift o = new Oppskrift();
        o.setPorsjoner(porsjoner);
        assertEquals(porsjoner, o.getPorsjoner());
    }

    @Test
    void setVanskelighet_alleNivåer_lagresRiktig() {
        Oppskrift o = new Oppskrift();
        for (String nivå : new String[]{"Enkel", "Medium", "Avansert"}) {
            o.setVanskelighet(nivå);
            assertEquals(nivå, o.getVanskelighet());
        }
    }

    @Test
    void setFeil_transientFelt_lagresIkkeIPersistens() {
        Oppskrift o = new Oppskrift();
        o.setFeil("Testfeil");
        assertEquals("Testfeil", o.getFeil());
        // @Transient bekrefter at feltet ikke persisteres i DB
    }

    @Test
    void setErOffentlig_toggles_riktig() {
        Oppskrift o = new Oppskrift();
        assertFalse(o.isErOffentlig());
        o.setErOffentlig(true);
        assertTrue(o.isErOffentlig());
        o.setErOffentlig(false);
        assertFalse(o.isErOffentlig());
    }

    @Test
    void setIngredienser_flerlinjesTekst_bevaresRiktig() {
        Oppskrift o = new Oppskrift();
        String tekst = "200g pasta\n3 egg\n100g bacon\nSalt og pepper";
        o.setIngredienser(tekst);
        assertEquals(tekst, o.getIngredienser());
        assertEquals(4, o.getIngredienser().split("\n").length);
    }
}
