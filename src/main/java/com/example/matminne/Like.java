package com.example.matminne;

import jakarta.persistence.*;

@Entity
@Table(name = "likes")
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long brukerId;
    private Long oppskriftId;

    public Like() {}
    public Like(Long brukerId, Long oppskriftId) {
        this.brukerId = brukerId;
        this.oppskriftId = oppskriftId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public Long getOppskriftId() { return oppskriftId; }
    public void setOppskriftId(Long oppskriftId) { this.oppskriftId = oppskriftId; }
}
