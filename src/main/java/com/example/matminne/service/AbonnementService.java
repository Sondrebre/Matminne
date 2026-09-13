package com.example.matminne.service;

import com.example.matminne.model.Abonnement;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Kobler abonnementsnivåene til Stripe-priser.
 *
 * Hvert betalte nivå trenger sitt eget Price-objekt hos Stripe, satt som
 * stripe.price.niva-30, stripe.price.niva-50 osv. Tjenesten holder
 * oversettelsen begge veier: nivå → price-ID når noen kjøper, og
 * price-ID → nivå når Stripe forteller oss hva som ble betalt.
 */
@Service
public class AbonnementService {

    private static final Logger log = LoggerFactory.getLogger(AbonnementService.class);

    private final Environment env;

    /** Gammel enkeltpris, brukt som reserve for 30-nivået hvis den nye ikke er satt. */
    @Value("${stripe.price.id:}")
    private String gammelPrisId;

    private final Map<Abonnement, String> prisIdPerNiva = new EnumMap<>(Abonnement.class);
    private final Map<String, Abonnement> nivaPerPrisId = new HashMap<>();

    public AbonnementService(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void lastPriser() {
        for (Abonnement niva : Abonnement.betalte()) {
            String prisId = env.getProperty("stripe.price." + niva.prisNokkel(), "");

            // Bakoverkompatibilitet: den opprinnelige prisen var ett nivå
            if (prisId.isBlank() && niva == Abonnement.NIVA_30 && !gammelPrisId.isBlank())
                prisId = gammelPrisId;

            if (!prisId.isBlank()) {
                prisIdPerNiva.put(niva, prisId);
                nivaPerPrisId.put(prisId, niva);
            }
        }

        if (prisIdPerNiva.isEmpty()) {
            log.warn("Ingen Stripe-priser konfigurert — abonnement kan ikke kjøpes");
        } else {
            log.info("Abonnementspriser lastet for {} av {} nivåer",
                    prisIdPerNiva.size(), Abonnement.betalte().size());
        }
    }

    /** Stripe price-ID for et nivå, hvis det er konfigurert. */
    public Optional<String> prisIdFor(Abonnement niva) {
        return Optional.ofNullable(prisIdPerNiva.get(niva));
    }

    /** Hvilket nivå en Stripe-pris tilhører. */
    public Optional<Abonnement> nivaFor(String prisId) {
        if (prisId == null || prisId.isBlank()) return Optional.empty();
        return Optional.ofNullable(nivaPerPrisId.get(prisId));
    }

    public boolean erKjopbart(Abonnement niva) {
        return prisIdPerNiva.containsKey(niva);
    }

    /**
     * Nivåene som faktisk kan kjøpes nå — resten vises som utilgjengelige.
     * Nøklene er enum-navn, ikke enum-verdier: SpEL klarer ikke å slå opp
     * i et EnumMap fra en mal.
     */
    public Map<String, Boolean> tilgjengelighet() {
        Map<String, Boolean> kart = new HashMap<>();
        for (Abonnement niva : Abonnement.betalte())
            kart.put(niva.name(), prisIdPerNiva.containsKey(niva));
        return kart;
    }
}
