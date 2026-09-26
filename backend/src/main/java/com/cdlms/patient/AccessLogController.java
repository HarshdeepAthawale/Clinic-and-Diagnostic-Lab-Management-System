package com.cdlms.patient;

import com.cdlms.common.PageResponse;
import com.cdlms.patient.PatientDtos.AccessLogEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Record access log for Admin: who opened which patient record, and when (Security.md §6). */
@RestController
@RequestMapping("/api/admin/access-log")
public class AccessLogController {

    private final PatientAccessLogRepository accessLog;

    public AccessLogController(PatientAccessLogRepository accessLog) {
        this.accessLog = accessLog;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AccessLogEntry> search(@RequestParam(required = false) UUID patientId,
                                               @RequestParam(required = false) UUID userId,
                                               @RequestParam(required = false) Instant from,
                                               @RequestParam(required = false) Instant to,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        var rows = accessLog.search(patientId, userId, from, to,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
        return PageResponse.of(rows, r -> new AccessLogEntry(r.getId(), r.getAccessedAt(), r.getResource(),
                r.getPatientId(), r.getPatientCode(), r.getPatientName(), r.getUserId(), r.getUserRole(),
                r.getUserName()));
    }
}
