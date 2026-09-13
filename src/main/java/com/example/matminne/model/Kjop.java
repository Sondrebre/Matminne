package com.example.matminne.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Et fullført engangskjøp av en betalt samling.
 * Raden er selve tilgangsbeviset: finnes den, har brukeren varig tilgang
 * til oppskriftene i samlingen.
 */
@Entity
@Table(name = "kjop", indexes = {
    @Index(name = "idx_kjop_bruker", columnList = "brukerId"),
    @Index(name = "idx_kjop_samling", columnList = "samlingId"),
    @Index(name = "idx_kjop_sesjon", columnList = "stripeSesjonId", unique = true)
})
public class Kjop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long brukerId;
    private String brukerEpost;

    private Long samlingId;

    /** Snapshot av samlingsnavnet ved kjøp, slik at kvitteringen holder seg selv om navnet endres. */
    private String samlingNavn;

    /** Skaperen som fikk utbetalingen. */
    private Long skaperId;

    /** Totalbeløp betalt, i øre. */
    private Integer belop;

    /** Plattformens andel av beløpet, i øre. */
    private Integer plattformAndel;

    /** Stripe Checkout-sesjonen. Unik, så samme webhook kan ikke gi dobbelt kjøp. */
    private String stripeSesjonId;

    @Column(updatable = false)
    private LocalDateTime datoKjopt;

    @PrePersist
    protected void onCreate() { this.datoKjopt = LocalDateTime.now(); }

    public Kjop() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }

    public String getBrukerEpost() { return brukerEpost; }
    public void setBrukerEpost(String brukerEpost) { this.brukerEpost = brukerEpost; }

    public Long getSamlingId() { return samlingId; }
    public void setSamlingId(Long samlingId) { this.samlingId = samlingId; }

    public String getSamlingNavn() { return samlingNavn; }
    public void setSamlingNavn(String samlingNavn) { this.samlingNavn = samlingNavn; }

    public Long getSkaperId() { return skaperId; }
    public void setSkaperId(Long skaperId) { this.skaperId = skaperId; }

    public Integer getBelop() { return belop; }
    public void setBelop(Integer belop) { this.belop = belop; }

    public Integer getPlattformAndel() { return plattformAndel; }
    public void setPlattformAndel(Integer plattformAndel) { this.plattformAndel = plattformAndel; }

    public String getStripeSesjonId() { return stripeSesjonId; }
    public void setStripeSesjonId(String stripeSesjonId) { this.stripeSesjonId = stripeSesjonId; }

    public LocalDateTime getDatoKjopt() { return datoKjopt; }
    public void setDatoKjopt(LocalDateTime datoKjopt) { this.datoKjopt = datoKjopt; }

    /** Skaperens andel, i øre. */
    public int skaperAndel() {
        if (belop == null) return 0;
        return belop - (plattformAndel == null ? 0 : plattformAndel);
    }
}
