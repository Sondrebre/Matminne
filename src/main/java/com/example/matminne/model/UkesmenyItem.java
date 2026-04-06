package com.example.matminne.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ukesmeny")
public class UkesmenyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long brukerId;
    private String dag; // Mandag, Tirsdag, ...
    private String maltid; // Frokost, Lunsj, Middag
    private Long oppskriftId;
    private String oppskriftTittel;
    private String oppskriftBilde;

    public UkesmenyItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public String getDag() { return dag; }
    public void setDag(String dag) { this.dag = dag; }
    public String getMaltid() { return maltid; }
    public void setMaltid(String maltid) { this.maltid = maltid; }
    public Long getOppskriftId() { return oppskriftId; }
    public void setOppskriftId(Long oppskriftId) { this.oppskriftId = oppskriftId; }
    public String getOppskriftTittel() { return oppskriftTittel; }
    public void setOppskriftTittel(String oppskriftTittel) { this.oppskriftTittel = oppskriftTittel; }
    public String getOppskriftBilde() { return oppskriftBilde; }
    public void setOppskriftBilde(String oppskriftBilde) { this.oppskriftBilde = oppskriftBilde; }
}
