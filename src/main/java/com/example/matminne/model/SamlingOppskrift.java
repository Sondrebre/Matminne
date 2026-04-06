package com.example.matminne.model;

import jakarta.persistence.*;

@Entity
@Table(name = "samling_oppskrift")
public class SamlingOppskrift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long samlingId;
    private Long oppskriftId;

    public SamlingOppskrift() {}
    public SamlingOppskrift(Long samlingId, Long oppskriftId) {
        this.samlingId = samlingId;
        this.oppskriftId = oppskriftId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSamlingId() { return samlingId; }
    public void setSamlingId(Long samlingId) { this.samlingId = samlingId; }
    public Long getOppskriftId() { return oppskriftId; }
    public void setOppskriftId(Long oppskriftId) { this.oppskriftId = oppskriftId; }
}
