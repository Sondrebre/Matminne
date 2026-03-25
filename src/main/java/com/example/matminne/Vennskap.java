package com.example.matminne;

import jakarta.persistence.*;

@Entity
@Table(name = "vennskap")
public class Vennskap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Vi bruker Long id for å koble til Bruker-entiteten
    private Long brukerId; // ID-en til den som følger
    private Long vennId;   // ID-en til den som blir fulgt

    public Vennskap() {}

    public Vennskap(Long brukerId, Long vennId) {
        this.brukerId = brukerId;
        this.vennId = vennId;
    }

    // --- Gettere og Settere ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBrukerId() {
        return brukerId;
    }

    public void setBrukerId(Long brukerId) {
        this.brukerId = brukerId;
    }

    public Long getVennId() {
        return vennId;
    }

    public void setVennId(Long vennId) {
        this.vennId = vennId;
    }
}