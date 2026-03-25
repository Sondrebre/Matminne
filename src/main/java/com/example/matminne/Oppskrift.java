package com.example.matminne;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "oppskrifter")
public class Oppskrift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tittel;

    @Column(columnDefinition = "TEXT")
    private String ingredienser;

    @Column(columnDefinition = "TEXT")
    private String fremgangsmate;

    private String bildeUrl;
    private String url;

    private Long brukerId;
    private String brukerEpost;
    private String brukerNavn;

    private boolean erOffentlig;

    // --- Nye felt ---
    private String kategori;          // Middag, Dessert, Frokost, osv.
    private Integer tilberedningstid; // i minutter
    private Integer porsjoner;
    private String vanskelighet;      // Enkel, Medium, Avansert

    @Column(updatable = false)
    private LocalDateTime datoOpprettet;

    @Transient
    private String feil;

    @PrePersist
    protected void onCreate() {
        this.datoOpprettet = LocalDateTime.now();
    }

    public Oppskrift() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTittel() { return tittel; }
    public void setTittel(String tittel) { this.tittel = tittel; }
    public String getIngredienser() { return ingredienser; }
    public void setIngredienser(String ingredienser) { this.ingredienser = ingredienser; }
    public String getFremgangsmate() { return fremgangsmate; }
    public void setFremgangsmate(String fremgangsmate) { this.fremgangsmate = fremgangsmate; }
    public String getBildeUrl() { return bildeUrl; }
    public void setBildeUrl(String bildeUrl) { this.bildeUrl = bildeUrl; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public String getBrukerEpost() { return brukerEpost; }
    public void setBrukerEpost(String brukerEpost) { this.brukerEpost = brukerEpost; }
    public String getBrukerNavn() { return brukerNavn; }
    public void setBrukerNavn(String brukerNavn) { this.brukerNavn = brukerNavn; }
    public boolean isErOffentlig() { return erOffentlig; }
    public void setErOffentlig(boolean erOffentlig) { this.erOffentlig = erOffentlig; }
    public String getKategori() { return kategori; }
    public void setKategori(String kategori) { this.kategori = kategori; }
    public Integer getTilberedningstid() { return tilberedningstid; }
    public void setTilberedningstid(Integer tilberedningstid) { this.tilberedningstid = tilberedningstid; }
    public Integer getPorsjoner() { return porsjoner; }
    public void setPorsjoner(Integer porsjoner) { this.porsjoner = porsjoner; }
    public String getVanskelighet() { return vanskelighet; }
    public void setVanskelighet(String vanskelighet) { this.vanskelighet = vanskelighet; }
    public LocalDateTime getDatoOpprettet() { return datoOpprettet != null ? datoOpprettet : LocalDateTime.now(); }
    public void setDatoOpprettet(LocalDateTime datoOpprettet) { this.datoOpprettet = datoOpprettet; }
    public String getFeil() { return feil; }
    public void setFeil(String feil) { this.feil = feil; }
}
