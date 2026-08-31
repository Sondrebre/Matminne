package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OppskriftRepository extends JpaRepository<Oppskrift, Long> {
    List<Oppskrift> findByBrukerIdInAndErOffentligTrueOrderByIdDesc(List<Long> brukerIds);
    List<Oppskrift> findByBrukerId(Long brukerId);
    long countByBrukerId(Long brukerId);
    List<Oppskrift> findByErOffentligTrueOrderByIdDesc();
    List<Oppskrift> findByErOffentligTrueOrderByIdDesc(Pageable pageable);
    List<Oppskrift> findByErOffentligTrueAndKategoriOrderByIdDesc(String kategori);
    List<Oppskrift> findByErOffentligTrueAndKategoriOrderByIdDesc(String kategori, Pageable pageable);
    List<Oppskrift> findByErOffentligTrueAndTittelContainingIgnoreCaseOrderByIdDesc(String tittel);
    @Query("SELECT o FROM Oppskrift o WHERE o.id IN :ids")
    List<Oppskrift> findByIdIn(List<Long> ids);
}
