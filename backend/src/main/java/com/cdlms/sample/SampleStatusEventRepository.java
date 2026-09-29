package com.cdlms.sample;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SampleStatusEventRepository extends JpaRepository<SampleStatusEvent, UUID> {
}
