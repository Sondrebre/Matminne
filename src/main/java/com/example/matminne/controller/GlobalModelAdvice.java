package com.example.matminne.controller;

import com.example.matminne.model.Bruker;
import com.example.matminne.repository.VarselRepository;
import com.example.matminne.service.BrukerService;
import com.example.matminne.service.SkaperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Felles modellverdier som navbaren og bunnmenyen trenger på hver side.
 *
 * Ligger som @ControllerAdvice slik at alle kontrollere får dem — tidligere
 * lå denne logikken i WebController og gjaldt bare dens egne ruter.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired private BrukerService brukerService;
    @Autowired private VarselRepository varselRepository;
    @Autowired private SkaperService skaperService;

    @ModelAttribute
    public void leggTilGlobalInfo(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            model.addAttribute("innlogget", false);
            model.addAttribute("ulesVarsler", 0L);
            model.addAttribute("harAbonnement", false);
            model.addAttribute("erAdmin", false);
            model.addAttribute("antallTilGodkjenning", 0L);
            return;
        }

        String epost = principal.getAttribute("email");
        Bruker meg = brukerService.finnVedEpost(epost);

        model.addAttribute("brukernavn",
                meg != null ? meg.getVisningsnavn() : principal.getAttribute("name"));
        model.addAttribute("brukerEpost", epost);
        model.addAttribute("brukerId", meg != null ? meg.getId() : null);
        model.addAttribute("innlogget", true);

        boolean erAdmin = skaperService.erAdmin(epost);
        model.addAttribute("erAdmin", erAdmin);
        model.addAttribute("antallTilGodkjenning",
                erAdmin ? skaperService.antallTilGodkjenning() : 0L);

        if (meg != null) {
            String profilBilde = (meg.getBildeUrl() != null && !meg.getBildeUrl().isBlank())
                    ? meg.getBildeUrl()
                    : principal.getAttribute("picture");
            model.addAttribute("profilBilde", profilBilde);
            model.addAttribute("ulesVarsler",
                    varselRepository.countByMottakerBrukerIdAndLestFalse(meg.getId()));
            model.addAttribute("harAbonnement", meg.isHarAbonnement());
        } else {
            model.addAttribute("profilBilde", principal.getAttribute("picture"));
            model.addAttribute("ulesVarsler", 0L);
            model.addAttribute("harAbonnement", false);
        }
    }
}
