package com.example.matminne.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrukerTest {

    @Test
    void konstruktør_setterNavnOgEpost() {
        Bruker bruker = new Bruker("Ola Nordmann", "ola@example.com");
        assertEquals("Ola Nordmann", bruker.getFulltNavn());
        assertEquals("ola@example.com", bruker.getEpost());
    }

    @Test
    void nyBruker_harIkkeAbonnement() {
        Bruker bruker = new Bruker("Kari", "kari@example.com");
        assertFalse(bruker.isHarAbonnement());
    }

    @Test
    void setBio_lagresBio() {
        Bruker bruker = new Bruker();
        bruker.setBio("Glad i mat og friluftsliv.");
        assertEquals("Glad i mat og friluftsliv.", bruker.getBio());
    }

    @Test
    void setHarAbonnement_oppdaterer() {
        Bruker bruker = new Bruker();
        bruker.setHarAbonnement(true);
        assertTrue(bruker.isHarAbonnement());
    }

    @Test
    void setBildeUrl_lagresRiktig() {
        Bruker bruker = new Bruker();
        bruker.setBildeUrl("data:image/jpeg;base64,abc123");
        assertEquals("data:image/jpeg;base64,abc123", bruker.getBildeUrl());
    }
}
