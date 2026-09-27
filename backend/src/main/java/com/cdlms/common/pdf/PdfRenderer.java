package com.cdlms.common.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Renders a PDF from an XHTML template in {@code resources/templates/pdf/} (ADR-021).
 *
 * <p>Templates are processed by Thymeleaf in XML mode (so the output is well-formed XHTML, as the
 * PDF engine requires; values are always escaped) and laid out by OpenHTMLtoPDF on PDFBox. The
 * clinic letterhead is added to every model as {@code clinic}.
 */
@Component
public class PdfRenderer {

    public record Letterhead(String name, String address, String phone) {
    }

    private final TemplateEngine engine = new TemplateEngine();
    private final Letterhead letterhead;

    public PdfRenderer(@Value("${app.clinic.name:CDLMS Clinic & Diagnostic Lab}") String name,
                       @Value("${app.clinic.address:}") String address,
                       @Value("${app.clinic.phone:}") String phone) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        engine.setTemplateResolver(resolver);
        this.letterhead = new Letterhead(name, blankToNull(address), blankToNull(phone));
    }

    public byte[] render(String template, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        context.setVariable("clinic", letterhead);
        String xhtml = engine.process(template, context);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(xhtml, null)
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render " + template + " PDF", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
