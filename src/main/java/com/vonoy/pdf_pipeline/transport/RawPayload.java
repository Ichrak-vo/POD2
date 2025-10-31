package com.vonoy.pdf_pipeline.transport;

import java.io.InputStream;
import java.util.Map;


    public record RawPayload(InputStream stream, String contentType, Map<String,Object> meta) {}
