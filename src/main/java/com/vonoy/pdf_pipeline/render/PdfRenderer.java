package com.vonoy.pdf_pipeline.render;

public interface PdfRenderer {
    byte[] render(String html);
}