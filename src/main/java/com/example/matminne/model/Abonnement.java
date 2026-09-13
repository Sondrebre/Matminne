package com.example.matminne.model;

import java.util.Arrays;
import java.util.List;

/**
 * Abonnementsnivåene. Prisen følger hvor mange oppskrifter du kan lagre;
 * alle betalte nivåer gir de samme AI-funksjonene.
 *
 * UBEGRENSET har grense -1, som betyr ingen grense.
 */
public enum Abonnement {

    GRATIS    ("Gratis",      10,    0),
    NIVA_30   ("30",          30,   29),
    NIVA_50   ("50",          50,   39),
    NIVA_80   ("80",          80,   49),
    NIVA_100  ("100",        100,   59),
    NIVA_150  ("150",        150,   79),
    NIVA_200  ("200",        200,   99),
    UBEGRENSET("Ubegrenset",  -1,  199);

    /** Ingen grense. */
    public static final int UTEN_GRENSE = -1;

    private final String navn;
    private final int oppskriftGrense;
    private final int prisKroner;

    Abonnement(String navn, int oppskriftGrense, int prisKroner) {
        this.navn = navn;
        this.oppskriftGrense = oppskriftGrense;
        this.prisKroner = prisKroner;
    }

    public String getNavn() { return navn; }
    public int getOppskriftGrense() { return oppskriftGrense; }
    public int getPrisKroner() { return prisKroner; }

    public boolean erGratis() { return this == GRATIS; }

    public boolean erUbegrenset() { return oppskriftGrense == UTEN_GRENSE; }

    /** Har brukeren plass til én oppskrift mer? */
    public boolean harPlass(long antallNa) {
        return erUbegrenset() || antallNa < oppskriftGrense;
    }

    /** "Ubegrenset" eller "150 oppskrifter" — til visning. */
    public String grenseTekst() {
        return erUbegrenset() ? "Ubegrenset" : oppskriftGrense + " oppskrifter";
    }

    /** "199 kr/mnd", eller "Gratis" for gratisplanen. */
    public String prisTekst() {
        return erGratis() ? "Gratis" : prisKroner + " kr/mnd";
    }

    /** Nøkkelen i application.properties: stripe.price.niva-30 osv. */
    public String prisNokkel() {
        return erGratis() ? null : "niva-" + name().replace("NIVA_", "").toLowerCase();
    }

    /** Alle betalte nivåer, billigst først — rekkefølgen på abonnementssiden. */
    public static List<Abonnement> betalte() {
        return Arrays.stream(values()).filter(a -> !a.erGratis()).toList();
    }

    /**
     * Minste nivå som rommer et gitt antall oppskrifter.
     * Brukes til å foreslå riktig plan når noen treffer grensen sin.
     */
    public static Abonnement minsteSomRommer(long antall) {
        return Arrays.stream(values())
                .filter(a -> !a.erGratis())
                .filter(a -> a.harPlass(antall))
                .findFirst()
                .orElse(UBEGRENSET);
    }

    /** Tolker et nivånavn fra databasen eller et skjema, med gratis som fallback. */
    public static Abonnement fraNavn(String verdi) {
        if (verdi == null || verdi.isBlank()) return GRATIS;
        try {
            return valueOf(verdi.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GRATIS;
        }
    }
}
