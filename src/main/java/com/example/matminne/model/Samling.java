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
}
