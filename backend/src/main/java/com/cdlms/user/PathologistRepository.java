package com.cdlms.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PathologistRepository extends JpaRepository<Pathologist, UUID> {

    Optional<Pathologist> findByUserId(UUID userId);
}
