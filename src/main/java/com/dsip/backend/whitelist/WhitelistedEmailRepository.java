package com.dsip.backend.whitelist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhitelistedEmailRepository extends JpaRepository<WhitelistedEmail, Long> {

    Optional<WhitelistedEmail> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteByEmail(String email);
}
