package com.vonoy.pdf_pipeline.controller;

import com.vonoy.pdf_pipeline.api.dto.PdfJobRequest;
import com.vonoy.pdf_pipeline.services.PdfService;
import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pdf")
public class PdfController {

    private final PdfService pdfService;

    public PdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @io.swagger.v3.oas.annotations.Operation(
        summary = "Generates a PDF and returns it as binary data",
        description = "Receives a PdfJobRequest (JSON), generates a PDF in memory, and returns the PDF file directly in the HTTP response."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "PDF generated successfully",
            content = @io.swagger.v3.oas.annotations.media.Content(
                mediaType = "application/pdf"
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request"
        )
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> generate(@Valid @RequestBody PdfJobRequest req) {
        try {
            byte[] pdfBytes = pdfService.generate(req);

            String fileName = (req.getOutputFileName() == null || req.getOutputFileName().isBlank())
                    ? "result.pdf"
                    : (req.getOutputFileName().toLowerCase().endsWith(".pdf")
                        ? req.getOutputFileName()
                        : req.getOutputFileName() + ".pdf");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                ContentDisposition.inline().filename(fileName).build()
            );
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Server error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }



    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/partial")
    public ResponseEntity<byte[]> generatePartialDeliveryPdf(@Valid @RequestBody PdfJobRequest req) {
        try {
            byte[] pdfBytes = pdfService.generatePartialDeliveryPdf(req);

            String fileName = (req.getOutputFileName() == null || req.getOutputFileName().isBlank())
                    ? "result.pdf"
                    : (req.getOutputFileName().toLowerCase().endsWith(".pdf")
                        ? req.getOutputFileName()
                        : req.getOutputFileName() + ".pdf");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                ContentDisposition.inline().filename(fileName).build()
            );
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Server error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

}
