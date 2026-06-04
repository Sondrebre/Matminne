package com.example.matminne.repository;

import com.example.matminne.model.HandlelisteGruppe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HandlelisteGruppeRepository extends JpaRepository<HandlelisteGruppe, Long> {
    Optional<HandlelisteGruppe> findByInviteringskode(String kode);
    boolean existsByInviteringskode(String kode);
}
