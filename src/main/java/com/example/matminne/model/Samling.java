package com.example.matminne.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "samlinger")
public class Samling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long brukerId;
    private String navn;
    private String beskrivelse;

    /** Pris i øre. null eller 0 = vanlig privat samling (gratis). > 0 = betalt kokebok. */
    private Integer pris;

    /** Først når denne er true er en betalt samling synlig og kjøpbar for andre. */
    private boolean erPublisert = false;

    /** Forsidebilde for betalte samlinger. */
    @Column(columnDefinition = "TEXT")
    private String bildeUrl;

    @Column(updatable = false)
    private LocalDateTime opprettet;

    @PrePersist
    protected void onCreate() { this.opprettet = LocalDateTime.now(); }

    public Samling() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public String getNavn() { return navn; }
    public void setNavn(String navn) { this.navn = navn; }
    public String getBeskrivelse() { return beskrivelse; }
    public void setBeskrivelse(String beskrivelse) { this.beskrivelse = beskrivelse; }
    public LocalDateTime getOpprettet() { return opprettet; }
    public void setOpprettet(LocalDateTime opprettet) { this.opprettet = opprettet; }

    public Integer getPris() { return pris; }
    public void setPris(Integer pris) { this.pris = pris; }

    public boolean isErPublisert() { return erPublisert; }
    public void setErPublisert(boolean erPublisert) { this.erPublisert = erPublisert; }

    public String getBildeUrl() { return bildeUrl; }
    public void setBildeUrl(String bildeUrl) { this.bildeUrl = bildeUrl; }

    /** True hvis dette er en betalt kokebok (har pris satt). */
    public boolean erBetalt() { return pris != null && pris > 0; }

    /** True hvis samlingen er kjøpbar for andre akkurat nå. */
    public boolean erTilSalgs() { return erBetalt() && erPublisert; }

    /** Pris formatert for visning, f.eks. "149 kr". */
    public String prisFormatert() {
        if (!erBetalt()) return "Gratis";
        int kroner = pris / 100;
        int ore = pris % 100;
        return ore == 0 ? kroner + " kr" : String.format("%d,%02d kr", kroner, ore);
    }
}
