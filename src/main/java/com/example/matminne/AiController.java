package com.example.matminne;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private AiOppskriftService aiService;

    @GetMapping("/extract")
    public Oppskrift hentOppskrift(@RequestParam String url) {
        Oppskrift resultat = aiService.hentOgStrukturer(url);

        if (resultat == null) {
            log.warn("Servicen returnerte NULL for URL: {}", url);
            return new Oppskrift();
        }

        return resultat;
    }
}
