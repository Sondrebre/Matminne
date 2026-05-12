package com.example.matminne.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.matminne.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiOppskriftService {

    private static final Logger log = LoggerFactory.getLogger(AiOppskriftService.class);

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private AnthropicClient client;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();
            log.info("Claude AI-klient initialisert (claude-opus-4-6)");
        } else {
            log.warn("Anthropic API-nøkkel mangler — AI-funksjoner er deaktivert");
        }
    }

    /**
     * Henter og strukturerer en oppskrift fra en URL via Claude.
     */
    public Oppskrift hentOgStrukturer(String url) {
        Oppskrift ny = new Oppskrift();
        ny.setUrl(url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();
            String raaTekst = doc.body().text();
            if (raaTekst.length() > 8000) {
                raaTekst = raaTekst.substring(0, 8000);
            }
            String prompt = "Ekstraher oppskriften fra denne teksten. Svar nøyaktig i dette formatet:\n" +
                            "TITTEL: [navn]\n" +
                            "INGREDIENSER:\n- [ingrediens]\n" +
                            "FREMGANGSMÅTE:\n1. [steg]\n\n" +
                            "Tekst: " + raaTekst;
            String aiSvar = kallClaude(prompt);
            String bilde = doc.select("meta[property=og:image]").attr("content");
            ny.setBildeUrl(bilde);
            parseSvar(aiSvar, ny);
        } catch (Exception e) {
            ny.setTittel("Feil ved prosessering: " + e.getMessage());
        }
        return ny;
    }

    /**
     * Sender en fritekst-prompt til Claude og returnerer råsvaret.
     */
    public String genererFritekst(String prompt) {
        return kallClaude(prompt);
    }

    /**
     * Genererer en personalisert ukesmeny for 7 dager.
     * Returnerer strukturert tekst med dag, frokost, lunsj og middag.
     */
    public String genererUkesmeny(int kjottMiddager, String allergier, int porsjoner, String ekstraOnsker) {
        String allergiTekst = (allergier != null && !allergier.isBlank())
                ? "Allergier/intoleranser: " + allergier + "."
                : "Ingen kjente allergier.";
        String ekstraTekst = (ekstraOnsker != null && !ekstraOnsker.isBlank())
                ? "Andre ønsker: " + ekstraOnsker + "."
                : "";
        String prompt = "Du er en erfaren norsk kostholdsekspert. Lag en variert og sunn ukesmeny for 7 dager.\n\n" +
                "Brukerprofil:\n" +
                "- Antall kjøttbaserte middager: " + kjottMiddager + " av 7\n" +
                "- Porsjoner: " + porsjoner + "\n" +
                "- " + allergiTekst + "\n" +
                (ekstraTekst.isBlank() ? "" : "- " + ekstraTekst + "\n") +
                "\nSvar KUN med gyldig JSON, ingen annen tekst:\n" +
                "{\"mandag\":{\"frokost\":\"...\",\"lunsj\":\"...\",\"middag\":\"...\"}," +
                "\"tirsdag\":{\"frokost\":\"...\",\"lunsj\":\"...\",\"middag\":\"...\"}," +
                "\"onsdag\":{\"frokost\":\"...\",\"lunsj\":\"...\",\"middag\":\"...\"}," +
                "\"torsdag\":{\"frokost\":\"...\",\"lunsj\":\"...\",\"middag\":\"...\"}," +
                "\"fredag\":{\"frokost\":\"...\",\"lunsj\":\"...\",\"middag\":\"...\"}," +
                "\"lordag\":{\"frokost\":\"...\",\"lunsj\":\"...\",\"middag\":\"...\"}," +
                "\"sondag\":{\"frokost\":\"...\",\"lunsj\":\"...\",\"middag\":\"...\"}}\n\n" +
                "Fyll inn realistiske norske hverdagsretter. Varier frokost og lunsj. " +
                "Overhold kjøttmiddager-kravet nøyaktig. Ingen markdown, ingen forklaring — kun JSON.";
        return kallClaude(prompt);
    }

    /**
     * Estimerer matvarekostnad for en oppskrift via Claude.
     * Returnerer strukturert Map med detaljer per ingrediens og totalsum.
     */
    public Map<String, Object> genererPrisEstimat(String ingrediensTekst, Integer porsjoner) {
        Map<String, Object> feilSvar = new HashMap<>();
        String prompt = "Du er ekspert på norske dagligvarepriser i 2025 (Rema 1000, Kiwi, Meny, Spar).\n" +
                "Estimer hva det koster å kjøpe ingrediensene til denne oppskriften i Norge i dag.\n" +
                "Bruk alltid billigste butikk og billigste EMV/private-label produkt.\n" +
                (porsjoner != null ? "Oppskriften er til " + porsjoner + " porsjoner.\n" : "") +
                "\nPrisveiledning (2025, typiske Rema/Kiwi-priser):\n" +
                "- Egg 12 stk: 38 kr | Mel 1 kg: 18 kr | Sukker 1 kg: 22 kr\n" +
                "- Smør 500g: 72 kr | Margarin 400g: 30 kr\n" +
                "- Lettmelk 1 L: 23 kr | Fløte 3 dl: 30 kr | Rømme 350g: 35 kr\n" +
                "- Kjøttdeig 400g: 58 kr | Kyllingfilet 700g: 98 kr | Laks 400g: 75 kr\n" +
                "- Pasta 500g: 16 kr | Ris 1 kg: 28 kr | Potet 1 kg: 22 kr\n" +
                "- Løk 1 kg: 22 kr | Gulrot 1 kg: 18 kr | Hvitløk 1 hode: 12 kr\n" +
                "- Hermetiske tomater 400g: 18 kr | Tomatpuré 140g: 14 kr\n" +
                "- Kokosmelk 400 ml: 22 kr | Buljong terning 6 stk: 16 kr\n" +
                "- Olivenolje 500 ml: 55 kr | Solsikkeolje 1 L: 30 kr\n" +
                "- Fersk pasta 250g: 35 kr | Parmesan 150g: 55 kr | Mozzarella 125g: 30 kr\n" +
                "\nRegler:\n" +
                "- Oppgi prisen på minste tilgjengelig pakke som dekker behovet\n" +
                "- Hvis ingrediensen er en liten mengde av noe (f.eks. 1 ts salt), bruk lav pris (2-5 kr)\n" +
                "- totalMin = sum av alle minPris i listen\n" +
                "\nIngredienser:\n" + ingrediensTekst + "\n\n" +
                "Svar KUN med gyldig JSON, ingen annen tekst, ingen markdown:\n" +
                "{\"detaljer\":[{\"ingrediens\":\"egg\",\"produktNavn\":\"Prior egg 12 stk\",\"minPris\":38.0,\"billigstButikk\":\"Rema 1000\"}]," +
                "\"totalMin\":38.0}";
        String svar = kallClaude(prompt);
        if (svar == null || svar.startsWith("FEIL") || svar.startsWith("RATE_LIMIT")) {
            feilSvar.put("feil", svar != null ? svar : "Ukjent feil");
            return feilSvar;
        }
        try {
            String json = svar.replaceAll("(?s)```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) json = json.substring(start, end + 1);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            List<Map<String, Object>> detaljer = new ArrayList<>();
            JsonNode detNode = root.path("detaljer");
            if (detNode.isArray()) {
                for (JsonNode item : detNode) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("ingrediens", item.path("ingrediens").asText(""));
                    d.put("produktNavn", item.path("produktNavn").asText(""));
                    d.put("minPris", item.path("minPris").asDouble(0));
                    d.put("billigstButikk", item.path("billigstButikk").asText(""));
                    detaljer.add(d);
                }
            }
            long total = root.path("totalMin").asLong(0);
            Map<String, Object> res = new HashMap<>();
            res.put("detaljer", detaljer);
            res.put("totalMin", total);
            res.put("antallFunnet", detaljer.size());
            res.put("antallTotal", detaljer.size());
            return res;
        } catch (Exception e) {
            log.error("Feil ved parsing av prisestimat: {}", e.getMessage());
            feilSvar.put("feil", "Kunne ikke tolke AI-svaret");
            return feilSvar;
        }
    }

    /**
     * Genererer en full dagsmeny basert på brukerens fritekst-mål og valgfri kroppsprofil.
     */
    public Map<String, Object> genererDagsmenyFraMal(String brukerMal,
                                                      String kjønn, Integer vekt,
                                                      Integer høyde, Integer alder,
                                                      String aktivitet) {
        Map<String, Object> feilSvar = new HashMap<>();

        // Bygg kroppsprofil-del av prompten hvis noen felter er oppgitt
        StringBuilder profil = new StringBuilder();
        if (kjønn != null && !kjønn.isBlank())       profil.append("Kjønn: ").append(kjønn).append("\n");
        if (vekt != null && vekt > 0)                 profil.append("Vekt: ").append(vekt).append(" kg\n");
        if (høyde != null && høyde > 0)               profil.append("Høyde: ").append(høyde).append(" cm\n");
        if (alder != null && alder > 0)               profil.append("Alder: ").append(alder).append(" år\n");
        if (aktivitet != null && !aktivitet.isBlank()) profil.append("Aktivitetsnivå: ").append(aktivitet).append("\n");

        String profilTekst = profil.length() > 0
                ? "\nBrukerens kroppsprofil:\n" + profil + "\nBruk denne profilen til å beregne realistisk TDEE og tilpasse kalori- og proteinmengder nøyaktig til denne personen.\n"
                : "";

        String prompt = "Du er en norsk ernæringsfysiolog og kokk. En bruker ønsker følgende i dag:\n" +
                "\"" + brukerMal + "\"\n" +
                profilTekst + "\n" +
                "Tolke målet og lag en komplett dagsmeny med 3-4 måltider (frokost, evt. mellommåltid, lunsj, middag).\n" +
                "Bruk norsk mat og realistiske næringsestimater.\n\n" +
                "For hvert måltid: lag en KOMPLETT oppskrift med alle ingredienser (eksakte mengder) og detaljert fremgangsmåte.\n\n" +
                "Svar KUN med gyldig JSON, ingen annen tekst:\n" +
                "{\n" +
                "  \"tolkaMaal\": \"Kort beskrivelse av tolket mål\",\n" +
                "  \"totalKcal\": 2100,\n" +
                "  \"totalProtein\": 105,\n" +
                "  \"totalKarbo\": 220,\n" +
                "  \"totalFett\": 72,\n" +
                "  \"maltider\": [\n" +
                "    {\n" +
                "      \"type\": \"Frokost\",\n" +
                "      \"ikon\": \"🌅\",\n" +
                "      \"navn\": \"Gresk yoghurt med granola og bær\",\n" +
                "      \"kcal\": 380,\n" +
                "      \"protein\": 22,\n" +
                "      \"karbo\": 45,\n" +
                "      \"fett\": 8,\n" +
                "      \"ingredienser\": \"- 200g gresk yoghurt\\n- 40g granola\\n- 100g blandede bær\\n- 1 ts honning\",\n" +
                "      \"fremgangsmate\": \"1. Hell yoghurt i en bolle.\\n2. Topp med granola og bær.\\n3. Drypp honning over.\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "Ikoner for type: Frokost=🌅, Mellommåltid=🍎, Lunsj=☀️, Middag=🌙, Dessert=🍫\n" +
                "Ingredienser og fremgangsmate: bruk \\n for linjeskift (ikke faktisk linjeskift i JSON).\n" +
                "totalProtein/karbo/fett = sum av alle måltider. Ingen markdown, kun JSON.";
        String svar = kallClaudeMedModell(prompt, Model.CLAUDE_SONNET_4_5, 5000L);
        if (svar == null || svar.startsWith("FEIL") || svar.startsWith("RATE_LIMIT")) {
            feilSvar.put("feil", svar != null ? svar : "Ukjent feil");
            return feilSvar;
        }
        try {
            String json = svar.replaceAll("(?s)```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) json = json.substring(start, end + 1);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            List<Map<String, Object>> maltider = new ArrayList<>();
            for (JsonNode m : root.path("maltider")) {
                Map<String, Object> maltid = new HashMap<>();
                maltid.put("type",         m.path("type").asText(""));
                maltid.put("ikon",         m.path("ikon").asText("🍽"));
                maltid.put("navn",         m.path("navn").asText(""));
                maltid.put("kcal",         m.path("kcal").asInt(0));
                maltid.put("protein",      m.path("protein").asInt(0));
                maltid.put("karbo",        m.path("karbo").asInt(0));
                maltid.put("fett",         m.path("fett").asInt(0));
                maltid.put("ingredienser", m.path("ingredienser").asText(""));
                maltid.put("fremgangsmate",m.path("fremgangsmate").asText(""));
                maltider.add(maltid);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("tolkaMaal",    root.path("tolkaMaal").asText(""));
            res.put("totalKcal",    root.path("totalKcal").asInt(0));
            res.put("totalProtein", root.path("totalProtein").asInt(0));
            res.put("totalKarbo",   root.path("totalKarbo").asInt(0));
            res.put("totalFett",    root.path("totalFett").asInt(0));
            res.put("maltider",     maltider);
            return res;
        } catch (Exception e) {
            log.error("Feil ved parsing av dagsmeny: {}", e.getMessage());
            feilSvar.put("feil", "Kunne ikke tolke AI-svaret. Prøv igjen.");
            return feilSvar;
        }
    }

    /**
     * Genererer ingredienser og fremgangsmåte for et måltid fra ukesmenyen.
     */
    public Map<String, String> genererOppskriftFraTittel(String tittel, int porsjoner) {
        String prompt = "Du er en norsk kokk. Lag en komplett og detaljert oppskrift på \"" + tittel + "\" til " + porsjoner + " porsjoner.\n\n" +
                "Svar KUN i dette formatet, ingen annen tekst:\n\n" +
                "INGREDIENSER:\n" +
                "- [eksakt mengde og ingrediens]\n\n" +
                "FREMGANGSMÅTE:\n" +
                "1. [konkret steg med temperatur/tid der relevant]\n\n" +
                "Krav: Minst 6 ingredienser. Minst 4 fremgangssteg. Norske mål (dl, ss, ts, g, stk).";
        String svar = kallClaudeMedModell(prompt, Model.CLAUDE_SONNET_4_5, 2048L);
        Map<String, String> res = new HashMap<>();
        if (svar == null || svar.startsWith("FEIL") || svar.startsWith("RATE_LIMIT")) {
            res.put("feil", svar != null ? svar : "Ukjent feil");
            return res;
        }
        // Robust split — handles **FREMGANGSMÅTE:** or ## FREMGANGSMÅTE: etc.
        String[] deler = svar.split("(?i)\\*{0,2}FREMGANGSM[Å|A]TE:\\*{0,2}");
        String ingredienser = deler[0].replaceAll("(?i)\\*{0,2}INGREDIENSER:\\*{0,2}", "").trim();
        res.put("ingredienser", ingredienser);
        res.put("fremgangsmate", deler.length > 1 ? deler[1].trim() : "");
        return res;
    }


    /**
     * Sorterer og merger en handleliste med AI.
     * Returnerer JSON med varer gruppert etter butikkavdeling.
     */
    public Map<String, Object> smartSorterHandleliste(List<String> varer) {
        Map<String, Object> feilSvar = new HashMap<>();
        if (varer == null || varer.isEmpty()) {
            feilSvar.put("feil", "Handlelisten er tom.");
            return feilSvar;
        }
        String listeStr = String.join("\n", varer);
        String prompt = "Du er en smart handleliste-assistent. Her er en handleliste:\n\n" +
                listeStr + "\n\n" +
                "Gjør følgende:\n" +
                "1. Slå sammen duplikater (f.eks. '2 løk' og '1 løk' → '3 løk totalt')\n" +
                "2. Grupper varene i butikkavdelinger\n\n" +
                "Bruk KUN disse avdelingene (bare ta med avdelinger som har varer):\n" +
                "- Frukt & Grønt\n- Meieri & Egg\n- Kjøtt & Fisk\n- Bakeri & Brød\n" +
                "- Tørrvarer & Hermetikk\n- Frysedisk\n- Drikke\n- Annet\n\n" +
                "Svar KUN med gyldig JSON i dette formatet, ingen forklaring rundt:\n" +
                "{\n" +
                "  \"grupper\": [\n" +
                "    { \"navn\": \"Frukt & Grønt\", \"ikon\": \"🥦\", \"varer\": [\"2 epler\", \"1 løk\"] },\n" +
                "    { \"navn\": \"Meieri & Egg\", \"ikon\": \"🥛\", \"varer\": [\"1L melk\"] }\n" +
                "  ]\n" +
                "}";
        String svar = kallClaude(prompt);
        if (svar == null || svar.startsWith("FEIL") || svar.startsWith("RATE_LIMIT")) {
            feilSvar.put("feil", svar != null ? svar : "Ukjent feil");
            return feilSvar;
        }
        try {
            // Strip markdown code fences if present
            String json = svar.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("```", "").trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) json = json.substring(start, end + 1);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            List<Map<String, Object>> grupper = new ArrayList<>();
            JsonNode grupperNode = root.path("grupper");
            if (grupperNode.isArray()) {
                for (JsonNode g : grupperNode) {
                    Map<String, Object> gruppe = new HashMap<>();
                    gruppe.put("navn", g.path("navn").asText(""));
                    gruppe.put("ikon", g.path("ikon").asText("🛒"));
                    List<String> gVarer = new ArrayList<>();
                    g.path("varer").forEach(v -> gVarer.add(v.asText("")));
                    gruppe.put("varer", gVarer);
                    if (!gVarer.isEmpty()) grupper.add(gruppe);
                }
            }
            Map<String, Object> res = new HashMap<>();
            res.put("grupper", grupper);
            return res;
        } catch (Exception e) {
            log.error("Feil ved parsing av smart handleliste: {}", e.getMessage());
            feilSvar.put("feil", "Kunne ikke tolke AI-svaret. Prøv igjen.");
            return feilSvar;
        }
    }

    /**
     * Genererer 3 substitutter for en ingrediens.
     * Returnerer en enkel tekst med bullet-punkter.
     */
    public String genererSubstitutt(String ingrediens) {
        String prompt = "Du er en hjelpsomhetlig matekspert. Brukeren mangler ingrediensen: \"" + ingrediens + "\".\n" +
                "List opp nøyaktig 3 gode substitutter. For hver: navn på substitutt, kort forklaring (1 setning), og evt. mengdejustering.\n" +
                "Svar på norsk. Format:\n" +
                "1. [Substitutt] — [Forklaring]\n" +
                "2. [Substitutt] — [Forklaring]\n" +
                "3. [Substitutt] — [Forklaring]";
        return kallClaude(prompt);
    }

    /**
     * Kaller Claude API og returnerer tekst-svaret.
     */
    private String kallClaude(String prompt) {
        return kallClaudeMedModell(prompt, Model.CLAUDE_HAIKU_4_5_20251001, 2048L);
    }

    private String kallClaudeSonnet(String prompt) {
        return kallClaudeMedModell(prompt, Model.CLAUDE_SONNET_4_5, 4096L);
    }

    private String kallClaudeMedModell(String prompt, Model modell, long maxTokens) {
        if (client == null) {
            return "FEIL: Anthropic API-nøkkel mangler.";
        }
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(modell)
                    .maxTokens(maxTokens)
                    .addUserMessage(prompt)
                    .build();
            Message response = client.messages().create(params);
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(tb -> tb.text())
                    .findFirst()
                    .orElse("Tomt svar fra Claude.");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            log.error("Claude-feil [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            if (msg.contains("429") || msg.contains("rate_limit") || msg.contains("overload")) {
                return "RATE_LIMIT:60";
            }
            return "FEIL: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /**
     * Tolker råteksten fra Claude og fordeler den i Oppskrift-objektet.
     */
    private void parseSvar(String aiSvar, Oppskrift ny) {
        if (aiSvar == null || aiSvar.startsWith("FEIL")) {
            ny.setFeil("Noe gikk galt med AI-tjenesten. Prøv igjen.");
            return;
        }
        if (aiSvar.startsWith("RATE_LIMIT:")) {
            String sekStr = aiSvar.replace("RATE_LIMIT:", "").trim();
            int sek = 60;
            try { sek = Integer.parseInt(sekStr); } catch (Exception ignored) {}
            ny.setFeil("RATE_LIMIT:" + sek);
            return;
        }
        try {
            // Robust splitting — handles markdown (**INGREDIENSER:**) and both Å/A variants
            String[] deler = aiSvar.split("(?i)\\*{0,2}INGREDIENSER:\\*{0,2}|(?i)\\*{0,2}FREMGANGSM[ÅA]TE:\\*{0,2}");
            if (deler.length >= 1) {
                String tittel = deler[0].replaceAll("(?i)\\*{0,2}TITTEL:\\*{0,2}", "").trim();
                ny.setTittel(tittel.isBlank() ? null : tittel);
            }
            if (deler.length >= 2) ny.setIngredienser(deler[1].trim());
            if (deler.length >= 3) ny.setFremgangsmate(deler[2].trim());

            // If nothing was parsed, treat as error
            if (ny.getTittel() == null && ny.getIngredienser() == null) {
                ny.setFeil("Klarte ikke hente oppskriften fra denne siden. Prøv en annen lenke.");
            }
        } catch (Exception e) {
            ny.setFeil("Klarte ikke tolke AI-svaret. Prøv igjen.");
        }
    }
}
