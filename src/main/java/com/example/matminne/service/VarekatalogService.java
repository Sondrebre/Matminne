package com.example.matminne.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Statisk varekatalog for handlelisten.
 * Laster varekatalog.csv (format: vare;kategori) ved oppstart og gir raske
 * oppslag uten å måtte kalle AI for kjente varer.
 *
 * Kategorier:
 * 1=Frukt & Grønt, 2=Meieri & Egg, 3=Kjøtt & Fisk, 4=Bakeri & Brød,
 * 5=Tørrvarer & Hermetikk, 6=Frysedisk, 7=Brus/Øl, 8=Annet, 9=Kjølevarer
 */
@Service
public class VarekatalogService {

    private static final Logger log = LoggerFactory.getLogger(VarekatalogService.class);

    private static final Map<Integer, String> KATEGORI_NAVN = Map.of(
            1, "Frukt & Grønt",
            2, "Meieri & Egg",
            3, "Kjøtt & Fisk",
            4, "Bakeri & Brød",
            5, "Tørrvarer & Hermetikk",
            6, "Frysedisk",
            7, "Brus/Øl",
            8, "Annet",
            9, "Kjølevarer");

    /** Regex for ledende mengde/enhet, f.eks. "2 ", "500g ", "1,5 l ", "3 stk ", "1 boks " */
    private static final String MENGDE_REGEX =
            "^[\\d.,/½¼¾]+\\s*(kg|hg|g|gram|l|liter|dl|cl|ml|stk|pk|pakker?|bokser?|poser?|glass|flasker?|bunter?|never?|ss|ts|dråper?)?\\.?\\s+";

    /** Regex for ledende mengdeord/adjektiver, f.eks. "to store løk" → "løk" */
    private static final String ORD_REGEX =
            "^(en|et|ei|to|tre|fire|fem|seks|sju|syv|åtte|ni|ti|noen|litt|store?|små|liten|lite|stort?|ferske?|fersk|grove?|fine?|hele?|halve?|røde?|grønne?|gule?)\\s+";

    private final Map<String, Integer> katalog = new HashMap<>();

    @PostConstruct
    public void lastKatalog() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("varekatalog.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String linje;
            while ((linje = reader.readLine()) != null) {
                linje = linje.trim();
                if (linje.isEmpty() || linje.startsWith("#")) continue;
                String[] deler = linje.split(";");
                if (deler.length < 2) continue;
                try {
                    katalog.put(deler[0].trim().toLowerCase(Locale.ROOT), Integer.parseInt(deler[deler.length - 1].trim()));
                } catch (NumberFormatException ignored) {
                    // hopp over ugyldige linjer
                }
            }
            log.info("Varekatalog lastet: {} varer", katalog.size());
        } catch (Exception e) {
            log.error("Kunne ikke laste varekatalog.csv: {}", e.getMessage());
        }
    }

    /**
     * Slår opp kategori (1-9) for en vare.
     * Prøver flere strategier: direkte oppslag, komma-deling, ord-for-ord, delstreng-match.
     */
    public Optional<Integer> finnKategori(String vare) {
        if (vare == null || vare.isBlank()) return Optional.empty();
        String norm = normaliser(vare);
        if (norm.isEmpty()) return Optional.empty();

        // 1. Direkte oppslag (inkl. uten parentes)
        Optional<Integer> funnet = slaaOpp(norm);
        if (funnet.isPresent()) return funnet;

        // 2. Hvert komma-ledd (f.eks. "lønnesirup, blåbær eller andre bær" → prøv "lønnesirup", "blåbær")
        if (norm.contains(",")) {
            for (String del : norm.split(",")) {
                funnet = slaaOpp(normaliser(del.trim()));
                if (funnet.isPresent()) return funnet;
            }
        }

        // 3. Hvert enkelt ord ≥ 4 bokstaver (f.eks. "fint havsalt" → "havsalt")
        for (String ord : norm.split("[\\s,]+")) {
            if (ord.length() >= 4) {
                funnet = slaaOpp(ord);
                if (funnet.isPresent()) return funnet;
            }
        }

        // 4. Lengste katalognøkkel som finnes som delstreng i teksten
        final String normFinal = norm;
        return katalog.entrySet().stream()
                .filter(e -> e.getKey().length() >= 4 && normFinal.contains(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue);
    }

    private Optional<Integer> slaaOpp(String tekst) {
        if (tekst == null || tekst.isBlank()) return Optional.empty();
        String t = tekst.trim();
        Integer k = katalog.get(t);
        if (k != null) return Optional.of(k);
        // Uten parentes, f.eks. "løk (gul)" → "løk"
        String utenP = t.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
        if (!utenP.equals(t)) {
            k = katalog.get(utenP);
            if (k != null) return Optional.of(k);
        }
        return Optional.empty();
    }

    /** Visningsnavn for et kategori-nummer. */
    public String kategoriNavn(int kategori) {
        return KATEGORI_NAVN.getOrDefault(kategori, "Annet");
    }

    /**
     * Sorteringsrekkefølge for et kategori-navn (slik det lagres på HandelListeItem).
     * 1=Frukt&Grønt ... 9=Kjølevarer, null/ukjent = sist.
     */
    public int kategoriRekkefolge(String kategoriNavn) {
        if (kategoriNavn == null || kategoriNavn.isBlank()) return 10;
        String n = kategoriNavn.toLowerCase(Locale.ROOT).replaceAll("[\\s&/\\-]", "");
        return switch (n) {
            case "fruktgrønt", "fruktoggrønt" -> 1;
            case "meieriegg", "meieriogegg" -> 2;
            case "kjøttfisk", "kjøttogfisk" -> 3;
            case "bakeri", "bakeribrød", "bakeriogbrød" -> 4;
            case "tørrvarer", "tørrvarerhermetikk", "tørrvareroghermetikk" -> 5;
            case "frysedisk" -> 6;
            case "brus", "øl", "brusøl", "drikke" -> 7;
            case "annet" -> 8;
            case "kjølevarer" -> 9;
            default -> 8;
        };
    }

    /** Lowercase + trim + fjerner ledende bindestrek, brøktegn, suffikser, mengde/enhet og mengdeord. */
    private String normaliser(String vare) {
        String s = vare.toLowerCase(Locale.ROOT).trim();
        // Strip ledende "- ", "* ", "• " (fra oppskriftsformat)
        s = s.replaceFirst("^[-*•]\\s+", "");
        // Strip Unicode brøktegn (¼ ½ ¾ ⅓ ⅔ ⅛ ⅜ ⅝ ⅞)
        s = s.replaceAll("[¼½¾⅓⅔⅛⅜⅝⅞]", "");
        // Strip vanlige suffikser fra oppskrifter etter komma
        s = s.replaceAll(",?\\s*(til servering|til pynt|etter smak|pluss ekstra[^,]*|om nødvendig|delt i .*)$", "").trim();
        String forrige;
        int vakt = 0;
        do {
            forrige = s;
            s = s.replaceFirst(MENGDE_REGEX, "").replaceFirst(ORD_REGEX, "").trim();
        } while (!s.equals(forrige) && ++vakt < 5);
        return s;
    }
}
