package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OppskriftRepository extends JpaRepository<Oppskrift, Long> {
    List<Oppskrift> findByBrukerIdInAndErOffentligTrueOrderByIdDesc(List<Long> brukerIds);
    List<Oppskrift> findByBrukerId(Long brukerId);
    List<Oppskrift> findByErOffentligTrueOrderByIdDesc();
    List<Oppskrift> findByErOffentligTrueAndKategoriOrderByIdDesc(String kategori);
    List<Oppskrift> findByErOffentligTrueAndTittelContainingIgnoreCaseOrderByIdDesc(String tittel);
}
