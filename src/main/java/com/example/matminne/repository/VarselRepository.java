package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VarselRepository extends JpaRepository<Varsel, Long> {
    List<Varsel> findByMottakerBrukerIdOrderByOpprettetDesc(Long mottakerBrukerId);
    long countByMottakerBrukerIdAndLestFalse(Long mottakerBrukerId);
}
