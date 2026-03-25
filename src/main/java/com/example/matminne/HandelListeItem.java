package com.example.matminne;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "handel_liste")
public class HandelListeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long brukerId;
    private String tekst;
    private boolean ferdig = false;
    private String kategori; // valgfritt

    @Column(updatable = false)
    private LocalDateTime opprettet;

    @PrePersist
    protected void onCreate() { this.opprettet = LocalDateTime.now(); }

    public HandelListeItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public String getTekst() { return tekst; }
    public void setTekst(String tekst) { this.tekst = tekst; }
    public boolean isFerdig() { return ferdig; }
    public void setFerdig(boolean ferdig) { this.ferdig = ferdig; }
    public String getKategori() { return kategori; }
    public void setKategori(String kategori) { this.kategori = kategori; }
    public LocalDateTime getOpprettet() { return opprettet; }
    public void setOpprettet(LocalDateTime opprettet) { this.opprettet = opprettet; }
}
