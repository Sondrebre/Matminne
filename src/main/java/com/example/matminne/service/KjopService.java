package com.example.matminne.service;

import com.example.matminne.model.Bruker;
import com.example.matminne.model.Kjop;
import com.example.matminne.model.Samling;
import com.example.matminne.repository.KjopRepository;
import com.example.matminne.repository.SamlingRepository;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrerer fullførte kjøp av betalte samlinger.
 *
 * Både suksess-redirecten og Stripe-webhooken kaller hit, og de kan komme
 * i vilkårlig rekkefølge eller begge to. Derfor er registreringen idempotent:
 * Stripe-sesjons-ID er unik i databasen, og vi sjekker eksisterende kjøp først.
 */
@Service
public class KjopService {

    private static final Logger log = LoggerFactory.getLogger(KjopService.class);

    @Autowired private KjopRepository kjopRepository;
    @Autowired private SamlingRepository samlingRepository;
    @Autowired private BrukerService brukerService;

    /**
     * Oppretter kjøpsraden hvis betalingen er fullført og kjøpet ikke finnes fra før.
     * @return samlingId ved suksess, ellers null.
     */
    @Transactional
    public Long registrer(Session sesjon) {
        if (sesjon == null) return null;

        boolean betalt = "paid".equals(sesjon.getPaymentStatus()) || "complete".equals(sesjon.getStatus());
        if (!betalt) return null;

        Long samlingId = lesLong(sesjon, "samlingId");
        if (samlingId == null) return null;   // ikke et samlingskjøp (f.eks. abonnement)

        // Samme sesjon skal aldri gi to kjøp
        if (kjopRepository.findByStripeSesjonId(sesjon.getId()).isPresent()) return samlingId;

        String kjoperEpost = metadata(sesjon, "kjoperEpost");
        if (kjoperEpost == null) {
            log.warn("Checkout-sesjon {} mangler kjoperEpost", sesjon.getId());
            return null;
        }

        Bruker kjoper = brukerService.finnVedEpost(kjoperEpost);
        Samling samling = samlingRepository.findById(samlingId).orElse(null);
        if (kjoper == null || samling == null) {
            log.warn("Kjøp {} refererer til ukjent bruker eller samling", sesjon.getId());
            return null;
        }

        // Samme bruker skal ikke kunne ende opp med to kjøp av samme samling
        if (kjopRepository.existsByBrukerIdAndSamlingId(kjoper.getId(), samlingId)) return samlingId;

        Kjop k = new Kjop();
        k.setBrukerId(kjoper.getId());
        k.setBrukerEpost(kjoper.getEpost());
        k.setSamlingId(samlingId);
        k.setSamlingNavn(samling.getNavn());
        k.setSkaperId(samling.getBrukerId());
        k.setBelop(sesjon.getAmountTotal() != null
                ? sesjon.getAmountTotal().intValue() : samling.getPris());
        k.setPlattformAndel(lesInt(sesjon, "plattformAndel"));
        k.setStripeSesjonId(sesjon.getId());
        kjopRepository.save(k);

        log.info("Kjøp registrert: {} kjøpte «{}» for {} øre",
                kjoper.getEpost(), samling.getNavn(), k.getBelop());
        return samlingId;
    }

    private String metadata(Session s, String nokkel) {
        return s.getMetadata() != null ? s.getMetadata().get(nokkel) : null;
    }

    private Long lesLong(Session s, String nokkel) {
        try {
            String v = metadata(s, nokkel);
            return v != null ? Long.parseLong(v) : null;
        } catch (NumberFormatException e) { return null; }
    }

    private Integer lesInt(Session s, String nokkel) {
        try {
            String v = metadata(s, nokkel);
            return v != null ? Integer.parseInt(v) : null;
        } catch (NumberFormatException e) { return null; }
    }
}
