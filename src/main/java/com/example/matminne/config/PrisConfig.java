package com.example.matminne.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "matminne")
public class PrisConfig {

    private List<Pris> priser = new ArrayList<>();

    public List<Pris> getPriser() { return priser; }
    public void setPriser(List<Pris> priser) { this.priser = priser; }

    public static class Pris {
        private String ingrediens;
        private String produktNavn;
        private double pris;
        private String butikk;

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
