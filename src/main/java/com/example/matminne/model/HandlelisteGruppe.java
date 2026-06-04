package com.example.matminne.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "handleliste_gruppe")
public class HandlelisteGruppe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String navn;

    @Column(unique = true, length = 10)
    private String inviteringskode;

    private Long eierBrukerId;

    @Column(updatable = false)
    private LocalDateTime opprettet;

    @PrePersist
    protected void onCreate() { this.opprettet = LocalDateTime.now(); }

    public HandlelisteGruppe() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNavn() { return navn; }
    public void setNavn(String navn) { this.navn = navn; }
    public String getInviteringskode() { return inviteringskode; }
    public void setInviteringskode(String kode) { this.inviteringskode = kode; }
    public Long getEierBrukerId() { return eierBrukerId; }
    public void setEierBrukerId(Long id) { this.eierBrukerId = id; }
    public LocalDateTime getOpprettet() { return opprettet; }
    public void setOpprettet(LocalDateTime t) { this.opprettet = t; }
}
