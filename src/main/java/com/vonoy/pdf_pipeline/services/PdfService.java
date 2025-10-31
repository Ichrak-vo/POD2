package com.vonoy.pdf_pipeline.services;

import com.vonoy.pdf_pipeline.api.dto.Language;
import com.vonoy.pdf_pipeline.api.dto.PdfJobRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.itextpdf.text.pdf.languages.ArabicLigaturizer;
import com.itextpdf.text.pdf.languages.LanguageProcessor;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final TemplateEngine templateEngine;

    @Value("${pdf.output-dir:results}")
    private String outputDir;

    private static final Map<String, String> TEMPLATE_BY_KEY = Map.of(
        "invoice:v1",  "invoice.v1.html",
        "delivery:v1", "delivery.v1.html"
    );


    public byte[] generate(PdfJobRequest req) {
        final String apiKey = req.getApiKey();
        final String templateId = TEMPLATE_BY_KEY.get(apiKey);
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("Unknown apiKey: " + apiKey);
        }

        final LanguageProcessor processor = new ArabicLigaturizer();
        final Context context = new Context();

        // Logo
        context.setVariable("logoUrl", "/images/logo.png");
        context.setVariable("logoBase64",
            encodeImageToBase64("src/main/resources/static/images/logo.png"));

        // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        //      Extraction des données et gestion d'UNE OU PLUSIEURS images
        // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
        final Map<String, Object> dataMap = req.getData();
        final List<String> podImages = new ArrayList<>();

        if (dataMap != null) {
            // 1) Support historique: "imageBase64" (String)
            Object imgObj = dataMap.get("imageBase64");
            if (imgObj instanceof String s && !s.isBlank()) {
                podImages.add(toDataUri(s.trim()));
            }

            // 2) Nouveau: "imageBase64List" (List<?>), chaque item peut être
            //    - un base64 nu
            //    - un data URI complet
            //    - null / vide (ignoré)
            Object listObj = dataMap.get("imageBase64List");
            if (listObj instanceof Collection<?> col) {
                for (Object o : col) {
                    if (o == null) continue;
                    final String raw = String.valueOf(o).trim();
                    if (!raw.isBlank()) {
                        podImages.add(toDataUri(raw));
                    }
                }
            }

            // Si vous souhaitez exposer l’ancienne variable "proofBase64"
            // pour compat, on mappe la 1ère image si elle existe.
            if (!podImages.isEmpty()) {
                context.setVariable("proofBase64", podImages.get(0));
            }
        }

        // Liste d’images pour la boucle Thymeleaf: th:each="img : "
        context.setVariable("podImages", podImages);

        // Métadonnées & textes
        context.setVariable("compTel1req", processor.process("ﻫﺎﺗﻒ:"));
        context.setVariable("compTel2req", processor.process("ﻓﺎﻛﺲ:"));
        context.setVariable("compTel1",   processor.process("4022251 6 +962 "));
        context.setVariable("compTel2",   processor.process("4022626 6 +962"));
        context.setVariable("emailadd",   processor.process("info@finehh.com"));
        context.setVariable("link",       processor.process("www.finehh.com"));
        context.setVariable("address",    processor.process("ص.ب. 154 عمان 11118 الأردن"));
        context.setVariable("compName",   processor.process("ﺷﺮﻛﺔ ﻓﺎﻳﻦ ﻟﺼﻨﺎﻋﺔ ﺍﻟﻮﺭﻕ ﺍﻟﺼﺤﻲ ﺫ.ﻡ.ﻡ"));
        context.setVariable("footerLine1", processor.process(
            "لأي استفسارات أو لإعادة جدولة التسليم، يُرجى التواصل مع فريق التوزيع أو السائق مباشرةً."));
        context.setVariable("footerLine2", processor.process(
            "يُرجى التأكد من تواجد المستلم أو الممثل المفوَّض في موقع التسليم خلال الوقت المحدد."));

        if (dataMap != null) {
            Function<Object, String> asString = v -> v == null ? "" : String.valueOf(v).trim();

            @SuppressWarnings("unchecked")
            Function<Object, Map<String, Object>> asMap =
                v -> (v instanceof Map) ? (Map<String, Object>) v : null;

            String customerName = asString.apply(dataMap.get("customerName"));
            if (customerName.isBlank()) {
                Map<String, Object> customer = asMap.apply(dataMap.get("customer"));
                if (customer != null) {
                    customerName = asString.apply(
                        customer.containsKey("name") ? customer.get("name") : customer.get("customerName"));
                }
            }

            String driverName = asString.apply(dataMap.get("driverName"));
            if (driverName.isBlank()) {
                Map<String, Object> driver = asMap.apply(dataMap.get("driver"));
                if (driver != null) {
                    driverName = asString.apply(
                        driver.containsKey("name") ? driver.get("name") : driver.get("driverName"));
                }
            }

            String deliveryDate = asString.apply(dataMap.get("deliveryDate"));

            String proofLine = "فيما يلي إثبات التسليم المنفَّذ من قبل السائق "
                + driverName + " إلى العميل " + customerName + " بتاريخ " + deliveryDate;
            context.setVariable("proofLine", processor.process(proofLine));

            try {
                if (!deliveryDate.isBlank()) {
                    java.time.LocalDate d = java.time.LocalDate.parse(deliveryDate);
                    context.setVariable("deliveryDateObj", d);
                }
            } catch (Exception ignore) { }
        }

        final String html = templateEngine.process(templateId, context);
        return convertHtmlToPdf(html);
    }

    // ===== PDF rendering =====
    private byte[] convertHtmlToPdf(String html) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            String baseUri = resolveStaticBaseUri();
            builder.withHtmlContent(html, baseUri);

            // Police arabe : d’abord classpath, puis fallback fichier
            boolean fontLoaded = false;
            try (InputStream is = getClass().getResourceAsStream(
                "/fonts/NotoNaskhArabic-VariableFont_wght.ttf")) {
                if (is != null) {
                    File tmp = File.createTempFile("noto-naskh-arabic", ".ttf");
                    tmp.deleteOnExit();
                    Files.copy(is, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    builder.useFont(tmp, "Noto Naskh Arabic",
                            400, PdfRendererBuilder.FontStyle.NORMAL, true);
                    fontLoaded = true;
                }
            } catch (Exception ignore) { }

            if (!fontLoaded) {
                File fontFile = new File("src/main/resources/fonts/NotoNaskhArabic-VariableFont_wght.ttf");
                if (fontFile.exists()) {
                    builder.useFont(fontFile, "Noto Naskh Arabic",
                            400, PdfRendererBuilder.FontStyle.NORMAL, true);
                } else {
                    System.err.println("Arabic font not found: " + fontFile.getAbsolutePath());
                }
            }

            builder.defaultTextDirection(PdfRendererBuilder.TextDirection.RTL);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("HTML->PDF failed: " + e.getMessage(), e);
        }
    }

    private String resolveStaticBaseUri() {
        URL u = getClass().getResource("/static/");
        return (u != null) ? u.toExternalForm() : new File(".").toURI().toString();
    }

    /** Encode un fichier image local en data URI (optionnel). */
    private String encodeImageToBase64(String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) {
                System.err.println("Image not found: " + imagePath);
                return "";
            }
            byte[] imageBytes = Files.readAllBytes(file.toPath());
            String lower = imagePath.toLowerCase();
            String mime = lower.endsWith(".png") ? "image/png"
                    : (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ? "image/jpeg"
                    : lower.endsWith(".svg") ? "image/svg+xml"
                    : "application/octet-stream";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            System.err.println("Base64 encode error for " + imagePath + ": " + e.getMessage());
            return "";
        }
    }

    // ===== Helpers =====

    /** 
     * Convertit un base64 nu OU un data URI en data URI standard.
     * - Si déjà "data:image/…;base64,xxxx" => inchangé
     * - Sinon on devine le mime (png/jpg/gif) via l’en-tête base64, défaut image/png.
     */
    private String toDataUri(String maybeBase64OrDataUri) {
        if (maybeBase64OrDataUri.startsWith("data:image/")) {
            return maybeBase64OrDataUri;
        }
        String mime = guessImageMimeFromBase64(maybeBase64OrDataUri);
        return "data:" + mime + ";base64," + maybeBase64OrDataUri;
    }

    private String guessImageMimeFromBase64(String b64) {
        if (b64 == null || b64.length() < 16) return "image/png";
        String head = b64.substring(0, Math.min(16, b64.length()));
        if (head.startsWith("iVBOR"))     return "image/png";
        if (head.startsWith("/9j/"))      return "image/jpeg";
        if (head.startsWith("R0lGOD"))    return "image/gif";
        if (head.startsWith("PHN2Zy"))    return "image/svg+xml"; // "<svg" en base64
        return "image/png";
    }


    public byte[] generatePartialDeliveryPdf(PdfJobRequest req) {
        final String templateId = TEMPLATE_BY_KEY.get(req.getApiKey());
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("Unknown apiKey: " + req.getApiKey());
        }

        final Language lang = Optional.ofNullable(req.getLanguage()).orElse(Language.Arabic);
        final boolean isAr = (lang == Language.Arabic);

        final LanguageProcessor arProcessor = new ArabicLigaturizer();
        final Context context = new Context(isAr ? Locale.forLanguageTag("ar") : Locale.ENGLISH);

        // Header / logo
        context.setVariable("logoUrl", "/images/logo.png");
        context.setVariable("logoBase64", encodeImageToBase64("src/main/resources/static/images/logo.png"));

        // Libellés société
        if (isAr) {
            context.setVariable("compTel1req", arProcessor.process("هاتف:"));
            context.setVariable("compTel2req", arProcessor.process("فاكس:"));
            context.setVariable("compTel1",    arProcessor.process("+962 6 4022251"));
            context.setVariable("compTel2",    arProcessor.process("+962 6 4022626"));
            context.setVariable("emailadd",    arProcessor.process("info@finehh.com"));
            context.setVariable("link",        arProcessor.process("www.finehh.com"));
            context.setVariable("title",    arProcessor.process("إثبات الإرجاع الجزئي / التسليم الجزئي"));
            context.setVariable("customerNameLabel", arProcessor.process("اسم العميل :"));
            context.setVariable("customerNumberLabel", arProcessor.process("رقم العميل :"));
            context.setVariable("invNumberLabel", arProcessor.process("رقم الفاتورة"));
            context.setVariable("orderNumberLabel", arProcessor.process("رقم الطلب :"));
            context.setVariable("driverNameLabel", arProcessor.process(" اسم السائق :"));
            context.setVariable("routeNumberLabel", arProcessor.process(" رقم المسار:"));
            context.setVariable("deliveryDateLabel", arProcessor.process(" تاريخ التسليم :"));
            context.setVariable("remarquesLabel", arProcessor.process("ملاحظات"));
            context.setVariable("state", arProcessor.process("الوضع :"));
            context.setVariable("address", arProcessor.process("ص.ب. 154 عمّان 11118 الأردن"));
            context.setVariable("compName", arProcessor.process("شركة فاين لصناعة الورق الصحي ذ.م.م"));
            context.setVariable("footerLine1", arProcessor.process("تم إنشاء هذا المستند تلقائياً بواسطة نظام فونوي لإدارة النقل"));
            context.setVariable("footerLine2", arProcessor.process("© فونوي - جميع الحقوق محفوظة 2025"));
            context.setVariable("SalesNumber", arProcessor.process("الرقم التسلسلي"));
            context.setVariable("itemCode", arProcessor.process(" رمز الصنف"));
            context.setVariable("description", arProcessor.process("الوصف :"));
            context.setVariable("orderedQuantity", arProcessor.process("  المطلوبة الكمية "));
            context.setVariable("returnedquantity", arProcessor.process(" المُرجعة الكمية "));
            context.setVariable("notdeliveredquantity", arProcessor.process(" غير المُسلمة الكمية "));
            context.setVariable("deliveredQuantity", arProcessor.process(" المُسلمة الكمية "));
            context.setVariable("sum", arProcessor.process("المجموع"));

        } else {
            context.setVariable("compTel1req", "Tel:");
            context.setVariable("compTel2req", "Fax:");
            context.setVariable("compTel1",    "+962 6 4022251");
            context.setVariable("compTel2",    "+962 6 4022626");
            context.setVariable("emailadd",    "info@finehh.com");
            context.setVariable("link",        "www.finehh.com");
            context.setVariable("address",     "P.O. Box 154 Amman 11118 Jordan");
            context.setVariable("compName",    "Fine Hygienic Holding");
            context.setVariable("footerLine1",
                "This document was automatically generated by Vonoy TMS.");
            context.setVariable("footerLine2",
                "© Vonoy - All rights reserved 2025");
        }

        // Données dynamiques
        final Map<String, Object> dataMap = req.getData();
        String modeRaw = "DELIVERY";

        if (dataMap != null) {
            Function<Object, String> asString = v -> v == null ? "" : String.valueOf(v).trim();
            @SuppressWarnings("unchecked")
            Function<Object, Map<String, Object>> asMap = v -> (v instanceof Map) ? (Map<String, Object>) v : null;

            String customerName = asString.apply(dataMap.get("customerName"));
            if (customerName.isBlank()) {
                Map<String, Object> customer = asMap.apply(dataMap.get("customer"));
                if (customer != null) {
                    customerName = asString.apply(
                        customer.containsKey("name") ? customer.get("name") : customer.get("customerName"));
                }
            }

            String driverName = asString.apply(dataMap.get("driverName"));
            if (driverName.isBlank()) {
                Map<String, Object> driver = asMap.apply(dataMap.get("driver"));
                if (driver != null) {
                    driverName = asString.apply(
                        driver.containsKey("name") ? driver.get("name") : driver.get("driverName"));
                }
            }

            String deliveryDate  = asString.apply(dataMap.get("deliveryDate"));
            modeRaw              = asString.apply(dataMap.get("mode"));   // brut pour Thymeleaf
            String reason        = asString.apply(dataMap.get("reason"));
            String siteId        = asString.apply(dataMap.get("siteId"));
            String invoiceNumber = asString.apply(dataMap.get("invoiceNumber"));
            String salesOrder    = asString.apply(dataMap.get("salesOrder"));
            String routeId       = asString.apply(dataMap.get("routeId"));
            final String modeLabelAr = "RETURN".equalsIgnoreCase(modeRaw) ? "إرجاع جزئي" : "تسليم جزئي";
            final String proofLineAr = "فيما يلي " +
                    ("RETURN".equalsIgnoreCase(modeRaw) ? "إثبات الإرجاع الجزئي" : "إثبات التسليم الجزئي") +
                    " المنفَّذ من قبل السائق " + safe(driverName) +
                    " إلى العميل " + safe(customerName) +
                    (deliveryDate.isBlank() ? "" : " بتاريخ " + deliveryDate);
            final String modeLabelEn = "RETURN".equalsIgnoreCase(modeRaw) ? "Partial Return" : "Partial Delivery";
            final String proofLineEn = "Below is the "
                    + modeLabelEn.toLowerCase(Locale.ROOT)
                    + " performed by driver " + safe(driverName)
                    + " to customer " + safe(customerName)
                    + (deliveryDate.isBlank() ? "" : " on " + deliveryDate);

            // Injection champs
            if (isAr) {
                context.setVariable("customerName",  processAr(arProcessor, customerName));
                context.setVariable("driverName",    processAr(arProcessor, driverName));
                context.setVariable("deliveryDate",  processAr(arProcessor, deliveryDate));
                context.setVariable("reason",        processAr(arProcessor, reason));
                context.setVariable("siteId",        processAr(arProcessor, siteId));
                context.setVariable("invoiceNumber", processAr(arProcessor, invoiceNumber));
                context.setVariable("salesOrder",    processAr(arProcessor, salesOrder));
                context.setVariable("routeId",       processAr(arProcessor, routeId));
                context.setVariable("proofLine",     processAr(arProcessor, proofLineAr));
                context.setVariable("modeLabelAr",   processAr(arProcessor, modeLabelAr));
            } else {
                context.setVariable("customerName",  customerName);
                context.setVariable("deliveryDate",  deliveryDate);
                context.setVariable("reason",        reason);
                context.setVariable("siteId",        siteId);
                context.setVariable("invoiceNumber", invoiceNumber);
                context.setVariable("salesOrder",    salesOrder);
                context.setVariable("routeId",       routeId);
                context.setVariable("proofLine",     proofLineEn);
                context.setVariable("modeLabelAr",   modeLabelEn); // si ton template affiche ce var
            }   
                
                // IMPORTANT: on met le mode brut pour les conditions Thymeleaf (ex: th:if="${mode == 'RETURN'}")
                context.setVariable("mode", modeRaw);

            // Items
            Object itemsObj = dataMap.get("items");
            if (itemsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
                List<Map<String, Object>> processedItems = new ArrayList<>(items.size());
                for (Map<String, Object> it : items) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("lineId",         it.get("lineId")); // nombre → inchangé
                    row.put("orderedQty",     it.get("orderedQty"));
                    row.put("returnedQty",    it.get("returnedQty"));
                    row.put("undeliveredQty", it.get("undeliveredQty"));
                    row.put("deliveredQty",   it.get("deliveredQty"));
                    // champs texte
                    String code = stringOf(it.get("itemCode"));
                    String desc = stringOf(it.get("description"));
                    row.put("itemCode",    isAr ? processAr(arProcessor, code) : code);
                    row.put("description", isAr ? processAr(arProcessor, desc) : desc);
                    processedItems.add(row);
                }
                context.setVariable("items", processedItems);
            } else {
                context.setVariable("items", itemsObj);
            }

            context.setVariable("totals", dataMap.get("totals"));

            try {
                if (!deliveryDate.isBlank()) {
                    LocalDate d = LocalDate.parse(deliveryDate);
                    context.setVariable("deliveryDateObj", d);
                }
            } catch (Exception ignore) { }
        } else {
            context.setVariable("mode", "DELIVERY");
            if (isAr) {
                context.setVariable("modeLabelAr", processAr(arProcessor, "تسليم جزئي"));
            } else {
                context.setVariable("modeLabelAr", "Partial Delivery");
            }
        }

        final String html = templateEngine.process(templateId, context);
        return convertHtmlToPdf(html);
    }

    // ======= Ton convertisseur HTML -> PDF existant =======
   
  private static String normalizeKey(String raw) {
        if (raw == null) return null;
        String k = raw.trim().toLowerCase(Locale.ROOT);
        k = k.replace(':', '.');               // accepte "Delivery:v1"
        if (k.endsWith(".html")) {
            k = k.substring(0, k.length() - 5); // enlève ".html"
        }
        return k;
    }

/* -------------------- Helpers -------------------- */

// Ligaturise proprement, gère null/blank
private static String processAr(LanguageProcessor p, String s) {
    if (s == null || s.isBlank()) return "";
    return p.process(s);
}

// toString sûr
private static String stringOf(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
}

// Évite NPE dans la construction du proofLine
private static String safe(String s) {
    return s == null ? "" : s;
}


}
