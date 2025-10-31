package com.vonoy.pdf_pipeline.transport;

import java.util.Map;

public interface TransportClient {
    boolean supports(String type);
    RawPayload fetch(Map<String,Object> config, Map<String,Object> params);
}