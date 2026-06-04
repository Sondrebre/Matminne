package com.example.matminne.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "handleliste_gruppe_medlem")
public class HandlelisteGruppeMedlem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gruppeId;
    private Long brukerId;

    @Column(updatable = false)
    private LocalDateTime blittMedlem;

    @PrePersist
    protected void onCreate() { this.blittMedlem = LocalDateTime.now(); }

    public HandlelisteGruppeMedlem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGruppeId() { return gruppeId; }
    public void setGruppeId(Long gruppeId) { this.gruppeId = gruppeId; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public LocalDateTime getBlittMedlem() { return blittMedlem; }
    public void setBlittMedlem(LocalDateTime t) { this.blittMedlem = t; }
}
