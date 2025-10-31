package com.vonoy.pdf_pipeline.transport;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Map;

@Component
public class FileClient implements TransportClient {
    private final Tika tika = new Tika();

    @Override public boolean supports(String type){ return "file".equalsIgnoreCase(type); }

    @Override public RawPayload fetch(Map<String,Object> cfg, Map<String,Object> params){
        String path = (String) cfg.get("path");
        try {
            var is = new FileInputStream(Path.of(path).toFile());
            String ct = tika.detect(path);
            return new RawPayload(is, ct, Map.of());
        } catch (Exception e){ throw new RuntimeException(e); }
    }
}