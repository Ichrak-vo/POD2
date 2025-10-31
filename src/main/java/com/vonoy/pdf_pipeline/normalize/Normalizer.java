package com.vonoy.pdf_pipeline.normalize;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface Normalizer {
    boolean supports(String apiKey);
    NormalizedData normalize(JsonNode root, Map<String,Object> params);
}