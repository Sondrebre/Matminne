package com.example.matminne;

import jakarta.persistence.*;

@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long brukerId;
    private Long oppskriftId;
    private int verdi; // 1-5

    public Rating() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public Long getOppskriftId() { return oppskriftId; }
    public void setOppskriftId(Long oppskriftId) { this.oppskriftId = oppskriftId; }
    public int getVerdi() { return verdi; }
    public void setVerdi(int verdi) { this.verdi = verdi; }
}
