package com.vonoy.pdf_pipeline.render;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URL;

@Component
public class OpenHtmlToPdfRenderer implements PdfRenderer {

    @Override
    public byte[] render(String html) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();

            // Base URI pour permettre à <img th:src="@{/images/...}"> de se résoudre
            String baseUri = resolveStaticBaseUri(); // ex: file:/.../classes/static/
            b.withHtmlContent(html, baseUri);

            // (Optionnel) Support SVG si tu en utilises
            try { b.useSVGDrawer(new BatikSVGDrawer()); } catch (Throwable ignore) {}

            // (Optionnel) RTL par défaut si tu as du contenu arabe
            b.defaultTextDirection(PdfRendererBuilder.TextDirection.RTL);

            b.toStream(baos);
            b.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF render failed: " + e.getMessage(), e);
        }
    }

    private String resolveStaticBaseUri() {
        URL u = getClass().getResource("/static/");
        return (u != null) ? u.toExternalForm() : null;
    }
}
