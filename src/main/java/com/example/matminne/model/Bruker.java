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
    private String stripeCustomerId;

    @Column(length = 300)
    private String bio;

    public Bruker() {}

    public Bruker(String fulltNavn, String epost) {
        this.fulltNavn = fulltNavn;
        this.epost = epost;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFulltNavn() { return fulltNavn; }
    public void setFulltNavn(String fulltNavn) { this.fulltNavn = fulltNavn; }

    public String getEpost() { return epost; }
    public void setEpost(String epost) { this.epost = epost; }

    public String getPassord() { return passord; }
    public void setPassord(String passord) { this.passord = passord; }

    public String getBildeUrl() { return bildeUrl; }
    public void setBildeUrl(String bildeUrl) { this.bildeUrl = bildeUrl; }

    public boolean isHarAbonnement() { return harAbonnement; }
    public void setHarAbonnement(boolean harAbonnement) { this.harAbonnement = harAbonnement; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
