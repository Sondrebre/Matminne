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

    /** Hvor i godkjenningsløpet kokeboka er. Kun GODKJENT kan selges. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SamlingStatus status = SamlingStatus.UTKAST;

    /** Begrunnelse fra MatMinne når en søknad avslås. */
    @Column(length = 500)
    private String avslagsgrunn;

    private LocalDateTime datoSendtInn;
    private LocalDateTime datoBehandlet;

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

    public SamlingStatus getStatus() { return status != null ? status : SamlingStatus.UTKAST; }
    public void setStatus(SamlingStatus status) { this.status = status; }

    public String getAvslagsgrunn() { return avslagsgrunn; }
    public void setAvslagsgrunn(String avslagsgrunn) { this.avslagsgrunn = avslagsgrunn; }

    public LocalDateTime getDatoSendtInn() { return datoSendtInn; }
    public void setDatoSendtInn(LocalDateTime datoSendtInn) { this.datoSendtInn = datoSendtInn; }

    public LocalDateTime getDatoBehandlet() { return datoBehandlet; }
    public void setDatoBehandlet(LocalDateTime datoBehandlet) { this.datoBehandlet = datoBehandlet; }

    public String getBildeUrl() { return bildeUrl; }
    public void setBildeUrl(String bildeUrl) { this.bildeUrl = bildeUrl; }

    /** True hvis dette er en betalt kokebok (har pris satt). */
    public boolean erBetalt() { return pris != null && pris > 0; }

    /** True hvis samlingen er kjøpbar for andre akkurat nå. */
    public boolean erTilSalgs() { return erBetalt() && getStatus() == SamlingStatus.GODKJENT; }

    /** True hvis kokeboka venter på behandling hos MatMinne. */
    public boolean venterPaSvar() { return getStatus() == SamlingStatus.TIL_GODKJENNING; }

    /** Prisen i hele kroner, til bruk i skjemafelt. */
    public int prisKroner() { return pris != null ? pris / 100 : 0; }

    /** Hva skaperen sitter igjen med per salg, i kroner, før Stripe-gebyr. */
    public int skaperAndelKroner(int plattformProsent) {
        if (!erBetalt()) return 0;
        return Math.round((pris / 100f) * (100 - plattformProsent) / 100f);
    }

    /** Pris formatert for visning, f.eks. "149 kr". */
    public String prisFormatert() {
        if (!erBetalt()) return "Gratis";
        int kroner = pris / 100;
        int ore = pris % 100;
        return ore == 0 ? kroner + " kr" : String.format("%d,%02d kr", kroner, ore);
    }
}
