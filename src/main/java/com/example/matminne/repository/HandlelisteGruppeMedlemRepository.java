package com.example.matminne.repository;

import com.example.matminne.model.HandlelisteGruppeMedlem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface HandlelisteGruppeMedlemRepository extends JpaRepository<HandlelisteGruppeMedlem, Long> {
    List<HandlelisteGruppeMedlem> findByBrukerId(Long brukerId);
    List<HandlelisteGruppeMedlem> findByGruppeId(Long gruppeId);
    boolean existsByGruppeIdAndBrukerId(Long gruppeId, Long brukerId);
    long countByGruppeId(Long gruppeId);
    @Transactional
    void deleteByGruppeIdAndBrukerId(Long gruppeId, Long brukerId);
    @Transactional
    void deleteByGruppeId(Long gruppeId);
}
