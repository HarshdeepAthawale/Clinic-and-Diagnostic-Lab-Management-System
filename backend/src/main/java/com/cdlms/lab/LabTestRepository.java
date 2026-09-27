package com.cdlms.lab;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LabTestRepository extends JpaRepository<LabTest, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<LabTest> findByIdIn(Collection<UUID> ids);
}
