package com.example.matminne.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class PrisConfig {

    private static final Logger log = LoggerFactory.getLogger(PrisConfig.class);

    private final List<Pris> priser = new ArrayList<>();

    @PostConstruct
    public void lastPriser() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("priser.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String linje;
            boolean forsteLinje = true;
            while ((linje = reader.readLine()) != null) {
                if (forsteLinje) { forsteLinje = false; continue; } // hopp over header
                linje = linje.trim();
                if (linje.isEmpty()) continue;
                String[] deler = linje.split(";", -1);
                if (deler.length < 5) continue;
                try {
                    Pris p = new Pris();
                    p.setKategori(deler[0].trim());
                    p.setIngrediens(deler[1].trim());
                    p.setProduktNavn(deler[2].trim());
                    p.setPris(Double.parseDouble(deler[3].trim().replace(",", ".")));
                    p.setButikk(deler[4].trim());
                    priser.add(p);
                } catch (NumberFormatException e) {
                    log.warn("Ugyldig pris på linje: {}", linje);
                }
            }
            log.info("Lastet {} matvarepriser fra priser.csv", priser.size());
        } catch (Exception e) {
            log.error("Kunne ikke laste priser.csv: {}", e.getMessage());
        }
    }

    public List<Pris> getPriser() {
        return priser;
    }

    public static class Pris {
        private String kategori;
        private String ingrediens;
        private String produktNavn;
        private double pris;
        private String butikk;

        public String getKategori() { return kategori; }
        public void setKategori(String kategori) { this.kategori = kategori; }
        public String getIngrediens() { return ingrediens; }
        public void setIngrediens(String ingrediens) { this.ingrediens = ingrediens; }
        public String getProduktNavn() { return produktNavn; }
        public void setProduktNavn(String produktNavn) { this.produktNavn = produktNavn; }
        public double getPris() { return pris; }
        public void setPris(double pris) { this.pris = pris; }
        public String getButikk() { return butikk; }
        public void setButikk(String butikk) { this.butikk = butikk; }
    }
}
