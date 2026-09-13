package com.example.matminne.model;

import jakarta.persistence.*;

@Entity
@Table(name = "brukere", indexes = {
    @Index(name = "idx_bruker_epost", columnList = "brukernavn", unique = true)
})
public class Bruker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fulltNavn;

    // Stored in the 'brukernavn' column for backwards compatibility
    @Column(name = "brukernavn")
    private String epost;

    private String passord;
    @Column(columnDefinition = "TEXT")
    private String bildeUrl;
    private boolean harAbonnement = false;

    /** Hvilket nivå brukeren betaler for. Styrer hvor mange oppskrifter som kan lagres. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Abonnement abonnementNiva = Abonnement.GRATIS;

    private String stripeCustomerId;
    private boolean harGodtattVilkar = false;

    /** Stripe Connect-konto for skapere som selger betalte samlinger. */
    private String stripeConnectId;

    /** True når Connect-onboarding er fullført og kontoen kan motta utbetalinger. */
    private boolean connectKlar = false;

    @Column(length = 300)
    private String bio;

    @Column(length = 50)
    private String kallenavn;

    @Column(length = 200)
    private String kontaktEpost;

    public Bruker() {}

    public Bruker(String fulltNavn, String epost) {
        this.fulltNavn = fulltNavn;
        this.epost = epost;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFulltNavn() { return fulltNavn; }
    public void setFulltNavn(String fulltNavn) { this.fulltNavn = fulltNavn; }

    public String getKallenavn() { return kallenavn; }
    public void setKallenavn(String kallenavn) { this.kallenavn = kallenavn; }

    public String getKontaktEpost() { return kontaktEpost; }
    public void setKontaktEpost(String kontaktEpost) { this.kontaktEpost = kontaktEpost; }

    public String getVisningsnavn() {
        return (kallenavn != null && !kallenavn.isBlank()) ? kallenavn : fulltNavn;
    }

    public String getEpost() { return epost; }
    public void setEpost(String epost) { this.epost = epost; }

    public String getPassord() { return passord; }
    public void setPassord(String passord) { this.passord = passord; }

    public String getBildeUrl() { return bildeUrl; }
    public void setBildeUrl(String bildeUrl) { this.bildeUrl = bildeUrl; }

    public boolean isHarAbonnement() { return harAbonnement; }
    public void setHarAbonnement(boolean harAbonnement) { this.harAbonnement = harAbonnement; }

    public Abonnement getAbonnementNiva() {
        return abonnementNiva != null ? abonnementNiva : Abonnement.GRATIS;
    }
    public void setAbonnementNiva(Abonnement abonnementNiva) { this.abonnementNiva = abonnementNiva; }

    /**
     * Planen som gjelder nå. Er abonnementet oppsagt eller utløpt faller
     * brukeren tilbake til gratisplanen, uansett hva nivået sier.
     */
    public Abonnement gjeldendePlan() {
        return harAbonnement ? getAbonnementNiva() : Abonnement.GRATIS;
    }

    /** Hvor mange oppskrifter brukeren kan lagre nå. -1 = ubegrenset. */
    public int oppskriftGrense() {
        return gjeldendePlan().getOppskriftGrense();
    }

    /** Har brukeren plass til én oppskrift mer? */
    public boolean harPlassTilFlere(long antallNa) {
        return gjeldendePlan().harPlass(antallNa);
    }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getStripeConnectId() { return stripeConnectId; }
    public void setStripeConnectId(String stripeConnectId) { this.stripeConnectId = stripeConnectId; }

    public boolean isConnectKlar() { return connectKlar; }
    public void setConnectKlar(boolean connectKlar) { this.connectKlar = connectKlar; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public boolean isHarGodtattVilkar() { return harGodtattVilkar; }
    public void setHarGodtattVilkar(boolean harGodtattVilkar) { this.harGodtattVilkar = harGodtattVilkar; }
}
