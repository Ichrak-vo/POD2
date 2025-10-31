package com.vonoy.pdf_pipeline.normalize;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import java.util.*;

@Component
public class DeclarativeNormalizer implements Normalizer {

    // Chargé depuis application.yml : pdf.normalization.rules
    private final Map<String, Map<String, List<String>>> rulesByApiKey;

    public DeclarativeNormalizer(@Value("#{${pdf.normalization.rules:{}}}") Map<String, Map<String, List<String>>> rulesByApiKey) {
        this.rulesByApiKey = rulesByApiKey != null ? rulesByApiKey : Map.of();
    }

    @Override public boolean supports(String apiKey){ return rulesByApiKey.containsKey(apiKey); }

    @Override public NormalizedData normalize(JsonNode root, Map<String,Object> params){
        Map<String,Object> out = new LinkedHashMap<>();
        Map<String, List<String>> rules = rulesByApiKey.getOrDefault(params.getOrDefault("apiKey", "").toString(), null);
        if (rules == null) rules = rulesByApiKey.getOrDefault(params.getOrDefault("api", "").toString(), Map.of());
        if (rules.isEmpty()) rules = rulesByApiKey.values().stream().findFirst().orElse(Map.of()); // fallback

        Object jsonDoc = Configuration.defaultConfiguration().jsonProvider().parse(root.toString());

        for (var e : rules.entrySet()){
            String field = e.getKey();
            Object value = null;
            for (String expr : e.getValue()){
                try {
                    value = JsonPath.read(jsonDoc, expr);
                } catch (Exception ignored) {}
                if (value != null) break;
            }
            out.put(field, value);
        }
        if (params != null) out.putAll(params);
        return new NormalizedData(out);
    }
}