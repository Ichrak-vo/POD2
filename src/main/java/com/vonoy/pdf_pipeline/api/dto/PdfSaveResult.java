package com.vonoy.pdf_pipeline.api.dto;

public record PdfSaveResult(
    String fileName,    // nom du fichier écrit (ex: facture-123.pdf)
    String absolutePath,
    long sizeBytes
) {}