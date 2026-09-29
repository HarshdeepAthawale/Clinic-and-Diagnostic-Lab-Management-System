package com.cdlms.result;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.PageResponse;
import com.cdlms.result.ResultDtos.EnterResultsRequest;
import com.cdlms.result.ResultDtos.RejectRequest;
import com.cdlms.result.ResultDtos.ReturnRequest;
import com.cdlms.result.ResultDtos.SampleResults;
import com.cdlms.result.ResultDtos.TestingRow;
import com.cdlms.result.ResultDtos.VerificationRow;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Testing, result entry, verification and retests (Docs/API.md "Samples"). */
@RestController
public class ResultController {

    private final ResultService service;

    public ResultController(ResultService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- lists

    /** Samples at the lab waiting to be tested; returned ones first with the pathologist's reason. */
    @GetMapping("/api/samples/to-test")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public PageResponse<TestingRow> toTest(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.toTest(Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    /** The verification queue: critical first, then out-of-range, then the longest waiting. */
    @GetMapping("/api/samples/pending-verification")
    @PreAuthorize("hasRole('PATHOLOGIST')")
    public PageResponse<VerificationRow> pendingVerification(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "50") int size) {
        return service.pendingVerification(Math.max(page, 0), Math.clamp(size, 1, 100));
    }

    @GetMapping("/api/samples/verified-by-me")
    @PreAuthorize("hasRole('PATHOLOGIST')")
    public PageResponse<VerificationRow> verifiedByMe(@AuthenticationPrincipal AuthUser pathologist,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return service.verifiedByMe(pathologist, Math.max(page, 0), Math.clamp(size, 1, 50));
    }

    // ---------------------------------------------------------------- lab technician

    @PostMapping("/api/samples/{id}/start-testing")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public SampleResults startTesting(@AuthenticationPrincipal AuthUser tech, @PathVariable UUID id) {
        return service.startTesting(tech, id);
    }

    /** One attempt: a value for every parameter of every test on the sample. */
    @PostMapping("/api/samples/{id}/results")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public SampleResults enterResults(@AuthenticationPrincipal AuthUser tech, @PathVariable UUID id,
                                      @Valid @RequestBody EnterResultsRequest request) {
        return service.enterResults(tech, id, request);
    }

    /** Rejection during testing: sample used up, degraded, or another reason with a note. */
    @PostMapping("/api/samples/{id}/reject")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public SampleResults reject(@AuthenticationPrincipal AuthUser tech, @PathVariable UUID id,
                                @Valid @RequestBody RejectRequest request) {
        return service.rejectInTesting(tech, id, request);
    }

    @GetMapping("/api/samples/{id}/results")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN', 'PATHOLOGIST')")
    public SampleResults results(@AuthenticationPrincipal AuthUser caller, @PathVariable UUID id) {
        return service.get(caller, id);
    }

    // ---------------------------------------------------------------- pathologist

    /** Signs the result off: creates the report. */
    @PostMapping("/api/samples/{id}/verify")
    @PreAuthorize("hasRole('PATHOLOGIST')")
    public SampleResults verify(@AuthenticationPrincipal AuthUser pathologist, @PathVariable UUID id) {
        return service.verify(pathologist, id);
    }

    @PostMapping("/api/samples/{id}/return-for-retest")
    @PreAuthorize("hasRole('PATHOLOGIST')")
    public SampleResults returnForRetest(@AuthenticationPrincipal AuthUser pathologist, @PathVariable UUID id,
                                         @Valid @RequestBody ReturnRequest request) {
        return service.returnForRetest(pathologist, id, request);
    }
}
