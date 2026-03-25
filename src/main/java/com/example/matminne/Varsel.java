package com.example.matminne;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "varsler")
public class Varsel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long mottakerBrukerId;
    private String tekst;
    private String lenke;
    private boolean lest = false;

    @Column(updatable = false)
    private LocalDateTime opprettet;

    @PrePersist
    protected void onCreate() { this.opprettet = LocalDateTime.now(); }

    public Varsel() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMottakerBrukerId() { return mottakerBrukerId; }
    public void setMottakerBrukerId(Long mottakerBrukerId) { this.mottakerBrukerId = mottakerBrukerId; }
    public String getTekst() { return tekst; }
    public void setTekst(String tekst) { this.tekst = tekst; }
    public String getLenke() { return lenke; }
    public void setLenke(String lenke) { this.lenke = lenke; }
    public boolean isLest() { return lest; }
    public void setLest(boolean lest) { this.lest = lest; }
    public LocalDateTime getOpprettet() { return opprettet; }
    public void setOpprettet(LocalDateTime opprettet) { this.opprettet = opprettet; }
}
