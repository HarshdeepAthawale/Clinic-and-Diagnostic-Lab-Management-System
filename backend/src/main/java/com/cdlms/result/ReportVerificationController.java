package com.cdlms.result;

import com.cdlms.common.ApiException;
import com.cdlms.result.ResultDtos.ReportAuthenticity;
import com.cdlms.result.ResultDtos.Verifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checking a printed report (ADR-027). Open to anyone holding the QR code, so it says as little as it can:
 * that the clinic issued the report, which tests it covers, who verified it and when. No results, no
 * patient name (initials only), no dates of birth or IDs.
 *
 * <p>The code is 128 random bits, so it can't be guessed, and every code that isn't a real report gets the
 * same {@code 404}.
 */
@RestController
public class ReportVerificationController {

    private static final java.util.regex.Pattern CODE_SHAPE = java.util.regex.Pattern.compile("[0-9a-f]{32}");

    private final ResultQueries queries;
    private final String clinicName;

    public ReportVerificationController(ResultQueries queries, @Value("${app.clinic-name:CDLMS Clinic}") String clinicName) {
        this.queries = queries;
        this.clinicName = clinicName;
    }

    @GetMapping("/api/public/reports/{code}")
    public ResponseEntity<ReportAuthenticity> verify(@PathVariable String code) {
        // Anything that isn't shaped like a code can't be one: same answer, and no query.
        ResultQueries.Authenticity found = (CODE_SHAPE.matcher(code).matches() ? queries.authenticity(code) : java.util.Optional.<ResultQueries.Authenticity>empty())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "We have no report with that code"));
        ReportAuthenticity body = new ReportAuthenticity(true, clinicName, found.sampleCode(), initials(found.patientName()),
                found.testNames(), found.verifiedAt(), new Verifier(found.verifierName(), found.qualification(), found.registrationNumber()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** "Riya D. Sharma" → "R. D. S." */
    static String initials(String fullName) {
        StringBuilder out = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append('.');
            }
        }
        return out.toString();
    }
}
