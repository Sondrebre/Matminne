package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UkesmenyRepository extends JpaRepository<UkesmenyItem, Long> {
    List<UkesmenyItem> findByBrukerId(Long brukerId);
    void deleteByBrukerIdAndDagAndMaltid(Long brukerId, String dag, String maltid);
}
