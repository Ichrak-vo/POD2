package com.vonoy.pdf_pipeline.normalize;
import java.util.Map;

public record NormalizedData(Map<String,Object> fields) {
    public Object get(String key){ return fields.get(key); }
}