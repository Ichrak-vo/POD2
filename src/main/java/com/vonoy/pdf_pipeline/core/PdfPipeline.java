// package com.vonoy.pdf_pipeline.core;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.vonoy.pdf_pipeline.api.dto.PdfJobRequest;
// import com.vonoy.pdf_pipeline.api.dto.PdfSaveResult;
// import com.vonoy.pdf_pipeline.normalize.NormalizedData;
// import com.vonoy.pdf_pipeline.normalize.NormalizedValidator;
// import com.vonoy.pdf_pipeline.normalize.Normalizer;
// import com.vonoy.pdf_pipeline.parse.AnyDataParser;
// import com.vonoy.pdf_pipeline.render.PdfRenderer;
// import com.vonoy.pdf_pipeline.template.HtmlRenderer;
// import com.vonoy.pdf_pipeline.template.TemplateResolver;
// import com.vonoy.pdf_pipeline.transport.RawPayload;
// import com.vonoy.pdf_pipeline.transport.TransportClient;
// import com.vonoy.pdf_pipeline.transport.TransportRegistry;

// import org.slf4j.MDC;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;

// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.List;
// import java.util.Map;
// import java.util.UUID;

// @Service
// public class PdfPipeline {

//     private final TransportRegistry transports;
//     private final AnyDataParser parser;

//     // On garde ces champs pour ne pas casser l’injection Spring si déjà configurée,
//     // mais on ne les utilise plus.
//     @SuppressWarnings("unused")
//     private final List<Normalizer> normalizers;

//     @SuppressWarnings("unused")
//     private final NormalizedValidator validator;

//     private final TemplateResolver templates;
//     private final HtmlRenderer html;
//     private final PdfRenderer pdf;

//     @Value("${pdf.output.dir:./results}")
//     private String outputDir;

//     public PdfPipeline(TransportRegistry transports,
//                        AnyDataParser parser,
//                        List<Normalizer> normalizers,          // injecté mais ignoré
//                        NormalizedValidator validator,         // injecté mais ignoré
//                        TemplateResolver templates,
//                        HtmlRenderer html,
//                        PdfRenderer pdf) {
//         this.transports = transports;
//         this.parser = parser;
//         this.normalizers = normalizers;
//         this.validator = validator;
//         this.templates = templates;
//         this.html = html;
//         this.pdf = pdf;
//     }

//     public byte[] execute(PdfJobRequest req) {
//     String cid = UUID.randomUUID().toString();
//     MDC.put("cid", cid);
//     try {
//         // 1) Transport
//         TransportClient client = transports.get(req.getSource().getType());
//         RawPayload payload = client.fetch(req.getSource().getConfig(), req.getParams());
//         System.out.println("terminate transport");

//         // 2) Parse (ok même si non utilisé)
//         JsonNode root = parser.parse(payload);
//         System.out.println("terminate parse");

//         // 3) Pas de normalisation : modèle = params (ou vide)
//         Map<String, Object> params = (req.getParams() == null) ? Map.of() : req.getParams();
//         NormalizedData model = new NormalizedData(params);
//         System.out.println("skip normalize");

//         // 4) Pas de validation
//         System.out.println("skip validate");

//         // 5) Choix du HTML ou template
//         String htmlStr = null;

//         // 5.a) Si on passe du HTML brut dans la requête -> utiliser tel quel
//         Object rawHtml = model.fields().get("html");
//         if (rawHtml instanceof String rh && !rh.isBlank()) {
//             System.out.println("skip template (using raw HTML from params.html)");
//             htmlStr = rh;
//         } else {
//             // 5.b) Sinon on regarde si un templateId est donné dans params
//             String templateId = null;
//             Object tid = model.fields().get("templateId");
//             if (tid instanceof String s && !s.isBlank()) {
//                 templateId = s;
//                 System.out.println("using templateId from params: " + templateId);
//             } else {
//                 // 5.c) Fallback: on utilise le resolver à partir de l'apiKey
//                 templateId = templates.resolve(req.getApiKey(), model);
//                 System.out.println("using templateId from resolver: " + templateId);
//             }

//             // 6) Rendu HTML via le moteur (Thymeleaf ou FreeMarker selon ton HtmlRenderer)
//             htmlStr = html.render(templateId, model.fields());
//             System.out.println("terminate html");
//         }

//         // 7) PDF
//         return pdf.render(htmlStr);

//     } catch (RuntimeException ex) {
//         throw new RuntimeException("PDF generation failed: " + ex.getMessage(), ex);
//     } finally {
//         MDC.remove("cid");
//     }
// }


//     public PdfSaveResult executeToFile(PdfJobRequest req) {
//         byte[] pdfBytes = execute(req);

//         try {
//             // 1) s’assurer que le dossier existe
//             Path outDir = Path.of(outputDir).toAbsolutePath().normalize();
//             Files.createDirectories(outDir);

//             // 2) nom de fichier (force .pdf si absent)
//             String rawName = (req.getOutput() != null && req.getOutput().getFileName() != null)
//                     ? req.getOutput().getFileName()
//                     : ("document-" + UUID.randomUUID());
//             String fileName = rawName.toLowerCase().endsWith(".pdf") ? rawName : rawName + ".pdf";

//             // 3) chemin final
//             Path outFile = outDir.resolve(fileName);

//             // 4) écrire
//             Files.write(outFile, pdfBytes);

//             return new PdfSaveResult(fileName, outFile.toString(), pdfBytes.length);
//         } catch (Exception e) {
//             throw new RuntimeException("Failed to write PDF file: " + e.getMessage(), e);
//         }
//     }
// }
