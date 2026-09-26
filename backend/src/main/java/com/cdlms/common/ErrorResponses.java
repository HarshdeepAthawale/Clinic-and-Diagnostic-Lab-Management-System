package com.cdlms.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Writes an {@link ApiError}-shaped body from servlet filters and security handlers,
 * which run outside Spring MVC and so can't use {@link GlobalExceptionHandler}.
 */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    public static void write(HttpServletResponse response, int status, String error, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + escape(error) + "\",\"code\":\"" + escape(code) + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
