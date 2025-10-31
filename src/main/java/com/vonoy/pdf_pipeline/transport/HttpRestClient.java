package com.vonoy.pdf_pipeline.transport;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.Map;

@Component
public class HttpRestClient implements TransportClient {
    private final RestTemplate rt;
    public HttpRestClient(RestTemplateBuilder builder){ this.rt = builder.build(); }

    @Override public boolean supports(String type){ return "rest".equalsIgnoreCase(type) || "http".equalsIgnoreCase(type); }

    @Override public RawPayload fetch(Map<String,Object> cfg, Map<String,Object> params){
        String url = (String) cfg.get("url");
        String method = (String) cfg.getOrDefault("method", "GET");
        Map<String,String> headersMap = (Map<String,String>) cfg.getOrDefault("headers", Map.of());
        HttpHeaders headers = new HttpHeaders(); headersMap.forEach(headers::add);
        Object body = cfg.get("body");

        ResponseEntity<byte[]> resp = rt.exchange(url, HttpMethod.valueOf(method), new HttpEntity<>(body, headers), byte[].class);
        String ct = resp.getHeaders().getContentType() != null ? resp.getHeaders().getContentType().toString() : "application/octet-stream";
        return new RawPayload(new ByteArrayInputStream(resp.getBody()), ct, Map.of("status", resp.getStatusCode().value()));
    }
}
