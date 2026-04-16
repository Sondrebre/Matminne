package com.example.matminne.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OppskriftTest {

    @Test
    void nyOppskrift_erIkkeOffentligSomStandard() {
        Oppskrift o = new Oppskrift();
        assertFalse(o.isErOffentlig());
    }

    @Test
    void setTittel_lagresRiktig() {
        Oppskrift o = new Oppskrift();
        o.setTittel("Pasta Carbonara");
        assertEquals("Pasta Carbonara", o.getTittel());
    }

    @Test
    void setKategori_ogPorsjoner() {
        Oppskrift o = new Oppskrift();
        o.setKategori("Middag");
        o.setPorsjoner(4);
        assertEquals("Middag", o.getKategori());
        assertEquals(4, o.getPorsjoner());
    }

    @Test
    void setErOffentlig_oppdaterer() {
        Oppskrift o = new Oppskrift();
        o.setErOffentlig(true);
        assertTrue(o.isErOffentlig());
    }

    @Test
    void getDatoOpprettet_returnerIkkeNull() {
        Oppskrift o = new Oppskrift();
        // Uten @PrePersist (ikke persistert) skal getDatoOpprettet() fallback til nå
        assertNotNull(o.getDatoOpprettet());
    }

    @Test
    void setIngredienser_lagresRiktig() {
        Oppskrift o = new Oppskrift();
        o.setIngredienser("200g spaghetti\n3 egg\n100g bacon");
        assertTrue(o.getIngredienser().contains("spaghetti"));
    }
}
