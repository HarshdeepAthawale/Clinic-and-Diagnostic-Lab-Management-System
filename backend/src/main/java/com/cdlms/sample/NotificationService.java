package com.cdlms.sample;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.common.ClinicTime;
import com.cdlms.sample.SampleDtos.NotificationList;
import com.cdlms.sample.SampleDtos.NotificationView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The front desk's inbox (Docs/Design.md §6): things to act on, like calling a patient back after a
 * sample was rejected. Items stay until someone marks them handled; handled items drop off the list.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final ClinicTime time;

    public NotificationService(NotificationRepository notifications, ClinicTime time) {
        this.notifications = notifications;
        this.time = time;
    }

    /** Open items for the caller's role, newest first. */
    @Transactional(readOnly = true)
    public NotificationList open(AuthUser caller) {
        return new NotificationList(
                notifications.findTop30ByAudienceRoleAndHandledAtIsNullOrderByCreatedAtDesc(caller.role()).stream()
                        .map(NotificationService::view).toList(),
                notifications.countByAudienceRoleAndHandledAtIsNull(caller.role()));
    }

    /** Marks an item as dealt with. Only someone in the item's audience can do it. */
    @Transactional
    public NotificationList handle(AuthUser caller, UUID id) {
        Notification notification = notifications.findById(id)
                .filter(n -> n.getAudienceRole() == caller.role())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notification not found"));
        if (!notification.isHandled()) {
            notification.handle(caller.id(), time.now());
            notifications.saveAndFlush(notification);
        }
        return open(caller);
    }

    private static NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getPatientId(),
                n.getSampleId(), n.getCreatedAt());
    }
}
