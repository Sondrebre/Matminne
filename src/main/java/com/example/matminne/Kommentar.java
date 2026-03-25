package com.example.matminne;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "kommentarer")
public class Kommentar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long oppskriftId;
    private Long brukerId;
    private String brukerNavn;

    @Column(columnDefinition = "TEXT")
    private String tekst;

    private Long foreldreId; // null = toppnivå, ellers svar på annen kommentar

    @Column(updatable = false)
    private LocalDateTime opprettet;

    @PrePersist
    protected void onCreate() { this.opprettet = LocalDateTime.now(); }

    public Kommentar() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOppskriftId() { return oppskriftId; }
    public void setOppskriftId(Long oppskriftId) { this.oppskriftId = oppskriftId; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public String getBrukerNavn() { return brukerNavn; }
    public void setBrukerNavn(String brukerNavn) { this.brukerNavn = brukerNavn; }
    public String getTekst() { return tekst; }
    public void setTekst(String tekst) { this.tekst = tekst; }
    public Long getForeldreId() { return foreldreId; }
    public void setForeldreId(Long foreldreId) { this.foreldreId = foreldreId; }
    public LocalDateTime getOpprettet() { return opprettet; }
    public void setOpprettet(LocalDateTime opprettet) { this.opprettet = opprettet; }
}
