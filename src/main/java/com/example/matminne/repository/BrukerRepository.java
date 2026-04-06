package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BrukerRepository extends JpaRepository<Bruker, Long> {
    Bruker findByEpost(String epost);
    List<Bruker> findByFulltNavnContainingIgnoreCase(String navn);
    java.util.Optional<Bruker> findByStripeCustomerId(String stripeCustomerId);
}
