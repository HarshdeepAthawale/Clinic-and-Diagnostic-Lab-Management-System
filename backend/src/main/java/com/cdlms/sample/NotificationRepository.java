package com.cdlms.sample;

import com.cdlms.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop30ByAudienceRoleAndHandledAtIsNullOrderByCreatedAtDesc(Role audienceRole);

    long countByAudienceRoleAndHandledAtIsNull(Role audienceRole);
}
