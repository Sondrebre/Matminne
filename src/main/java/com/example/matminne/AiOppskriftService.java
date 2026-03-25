package com.example.matminne;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
        if (client == null) {
            return "FEIL: Anthropic API-nøkkel mangler. Sett ANTHROPIC_API_KEY i application.properties.";
        }

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_OPUS_4_6)
                    .maxTokens(2048L)
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
            if (msg.contains("429") || msg.contains("rate_limit") || msg.contains("overload")) {
                log.warn("Claude rate limit: {}", e.getMessage());
                return "RATE_LIMIT:60";
            }
            log.error("Feil ved kall til Claude: {}", e.getMessage());
            return "FEIL: " + e.getMessage();
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
            String[] deler = aiSvar.split("INGREDIENSER:|FREMGANGSMÅTE:");
            if (deler.length >= 1) ny.setTittel(deler[0].replace("TITTEL:", "").trim());
            if (deler.length >= 2) ny.setIngredienser(deler[1].trim());
            if (deler.length >= 3) ny.setFremgangsmate(deler[2].trim());
        } catch (Exception e) {
            ny.setTittel("Klarte ikke tolke AI-svaret.");
            ny.setIngredienser(aiSvar);
        }
    }
}
