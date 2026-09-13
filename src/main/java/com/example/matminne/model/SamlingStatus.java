package com.example.matminne.model;

/**
 * Godkjenningsløpet for en betalt kokebok.
 *
 * Skaperen bygger i UTKAST, sender inn til TIL_GODKJENNING, og MatMinne
 * godkjenner prisen før den kan selges. Endrer skaperen prisen etter
 * godkjenning, må den godkjennes på nytt — det er prisen som er godkjent,
 * ikke bare kokeboka.
 */
public enum SamlingStatus {

    /** Under arbeid hos skaperen. Ikke synlig for andre. */
    UTKAST("Utkast", "Under arbeid"),

    /** Sendt inn, venter på at MatMinne skal se over pris og innhold. */
    TIL_GODKJENNING("Til godkjenning", "Venter på svar fra MatMinne"),

    /** Godkjent av MatMinne. Kan selges hvis utbetaling er satt opp. */
    GODKJENT("Godkjent", "Til salgs"),

    /** Avslått. Skaperen kan endre og sende inn på nytt. */
    AVSLATT("Avslått", "Ikke godkjent");

    private final String visningsnavn;
    private final String forklaring;

    SamlingStatus(String visningsnavn, String forklaring) {
        this.visningsnavn = visningsnavn;
        this.forklaring = forklaring;
    }

    public String getVisningsnavn() { return visningsnavn; }
    public String getForklaring() { return forklaring; }

    /** Kan skaperen redigere innholdet nå? */
    public boolean kanRedigeres() {
        return this == UTKAST || this == AVSLATT;
    }

    /** Kan skaperen sende inn til godkjenning nå? */
    public boolean kanSendesInn() {
        return this == UTKAST || this == AVSLATT;
    }
}
