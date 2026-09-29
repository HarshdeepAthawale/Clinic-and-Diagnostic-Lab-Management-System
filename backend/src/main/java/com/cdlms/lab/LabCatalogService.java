package com.cdlms.lab;

import com.cdlms.common.ApiException;
import com.cdlms.lab.LabDtos.LabTestRequest;
import com.cdlms.lab.LabDtos.LabTestSummary;
import com.cdlms.lab.LabDtos.LabTestView;
import com.cdlms.lab.LabDtos.ParameterRange;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The lab test catalog (Docs/Rules.md §2a). Everyone signed in can browse active tests (doctors to
 * order, patients to see prep); admins add tests, edit prices, prep and ranges, and retire tests.
 * Retiring never touches existing orders — their lines keep the name and price they were ordered at.
 */
@Service
public class LabCatalogService {

    private final LabTestRepository tests;
    private final LabQueries queries;

    public LabCatalogService(LabTestRepository tests, LabQueries queries) {
        this.tests = tests;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public List<LabTestSummary> list(String query, boolean includeInactive) {
        return queries.tests(query, includeInactive);
    }

    @Transactional(readOnly = true)
    public LabTestView get(UUID id, boolean includeInactive) {
        return tests.findById(id)
                .filter(t -> includeInactive || t.isActive())
                .map(LabCatalogService::view)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Test not found"));
    }

    @Transactional
    public LabTestView create(LabTestRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Give the test a short code, e.g. CBC");
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (tests.existsByCodeIgnoreCase(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_TAKEN", "Another test already uses the code " + code);
        }
        LabTest test = new LabTest(code, details(request));
        setParameters(test, request.parameters());
        return view(tests.saveAndFlush(test));
    }

    @Transactional
    public LabTestView update(UUID id, LabTestRequest request) {
        LabTest test = tests.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Test not found"));
        test.apply(details(request));
        // Remove old parameters first so re-numbered positions never clash with the (test, position) key.
        test.clearParameters();
        tests.saveAndFlush(test);
        setParameters(test, request.parameters());
        return view(tests.saveAndFlush(test));
    }

    // ---------------------------------------------------------------- helpers

    private static LabTest.Details details(LabTestRequest r) {
        return new LabTest.Details(r.name().trim(), r.category().trim(), r.sampleType(), r.requiredTubeType(),
                r.price(), r.turnaroundHours().shortValue(), trim(r.prepInstructions()),
                r.active() == null || r.active());
    }

    private static void setParameters(LabTest test, List<ParameterRange> parameters) {
        if (parameters == null) {
            return;
        }
        for (ParameterRange p : parameters) {
            requireOrdered(p.name(), p.refLow(), p.refHigh(), "normal range");
            requireOrdered(p.name(), p.criticalLow(), p.criticalHigh(), "critical limits");
            ValueType type = p.valueType() != null ? p.valueType()
                    : ValueType.inferred(p.refLow(), p.refHigh(), p.criticalLow(), p.criticalHigh());
            if (type == ValueType.TEXT && (p.refLow() != null || p.refHigh() != null
                    || p.criticalLow() != null || p.criticalHigh() != null)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        p.name() + ": a text result can't have a normal range — make it numeric or clear the range");
            }
            test.addParameter(new LabTestParameter.Range(p.name().trim(), trim(p.unit()), p.refLow(), p.refHigh(),
                    p.criticalLow(), p.criticalHigh(), type));
        }
    }

    private static void requireOrdered(String name, BigDecimal low, BigDecimal high, String what) {
        if (low != null && high != null && low.compareTo(high) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    name + ": the low end of the " + what + " is above the high end");
        }
    }

    static LabTestView view(LabTest t) {
        return new LabTestView(t.getId(), t.getCode(), t.getName(), t.getCategory(), t.getSampleType(),
                t.getRequiredTubeType(), t.getPrice(), t.getTurnaroundHours(), t.getPrepInstructions(), t.isActive(),
                t.getParameters().stream().map(p -> {
                    LabTestParameter.Range r = p.range();
                    return new ParameterRange(r.name(), r.unit(), r.refLow(), r.refHigh(), r.criticalLow(), r.criticalHigh(), r.valueType());
                }).toList(),
                t.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
