package com.example.matminne.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.matminne.model.*;
import com.example.matminne.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TilgangsSjekk implements AuthenticationSuccessHandler {

    @Autowired
    private BrukerService brukerService;

    @Value("${app.tillatte.epost:}")
    private String tillatteListe;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String epost = null;
        String navn = null;
        if (authentication.getPrincipal() instanceof OAuth2User user) {
            epost = user.getAttribute("email");
            navn = user.getAttribute("name");
        }

        if (epost == null) {
            response.sendRedirect("/ingen-tilgang");
            return;
        }

        // Opprett bruker i DB hvis de ikke finnes
        Bruker meg = brukerService.finnVedEpost(epost);
        if (meg == null) {
            meg = new Bruker();
            meg.setEpost(epost);
            meg.setFulltNavn(navn);
            brukerService.lagreBruker(meg);
        }

        // Sjekk allowlist (hvis konfigurert)
        if (tillatteListe != null && !tillatteListe.isBlank()) {
            List<String> tillatte = Arrays.stream(tillatteListe.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
            if (!tillatte.contains(epost.toLowerCase())) {
                request.getSession().invalidate();
                response.sendRedirect("/ingen-tilgang");
                return;
            }
        }

        // Krev vilkårsgodkjenning ved første innlogging
        if (!meg.isHarGodtattVilkar()) {
            response.sendRedirect("/godta-vilkar");
            return;
        }

        response.sendRedirect("/kokebok");
    }
}
