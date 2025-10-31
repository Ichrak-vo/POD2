package com.vonoy.pdf_pipeline.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.vonoy.pdf_pipeline.transport.RawPayload;

public interface AnyDataParser {
    JsonNode parse(RawPayload payload);
}