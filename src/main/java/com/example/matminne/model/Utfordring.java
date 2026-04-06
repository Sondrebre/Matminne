package com.example.matminne.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "utfordringer")
public class Utfordring {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tittel;

    @Column(columnDefinition = "TEXT")
    private String beskrivelse;

    private boolean aktiv = true;

    @Column(updatable = false)
    private LocalDateTime opprettet;

    @PrePersist
    protected void onCreate() { this.opprettet = LocalDateTime.now(); }

    public Utfordring() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTittel() { return tittel; }
    public void setTittel(String tittel) { this.tittel = tittel; }
    public String getBeskrivelse() { return beskrivelse; }
    public void setBeskrivelse(String beskrivelse) { this.beskrivelse = beskrivelse; }
    public boolean isAktiv() { return aktiv; }
    public void setAktiv(boolean aktiv) { this.aktiv = aktiv; }
    public LocalDateTime getOpprettet() { return opprettet; }
    public void setOpprettet(LocalDateTime opprettet) { this.opprettet = opprettet; }
}
