package com.vonoy.pdf_pipeline.template;
import com.vonoy.pdf_pipeline.normalize.NormalizedData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class YamlTemplateResolver implements TemplateResolver {
    // application.yml: pdf.templates.mapping: { "invoice:v1": "templates/invoice.v1.html" }
    private final Map<String,String> mapping;

    public YamlTemplateResolver(@Value("#{${pdf.templates.mapping:{}}}") Map<String,String> mapping){
        this.mapping = mapping != null ? mapping : Map.of();
    }

    @Override public String resolve(String apiKey, NormalizedData model){
        String base = mapping.getOrDefault(apiKey, "templates/invoice.v1.html");
        Object locale = model.get("locale");
        if ("ar".equals(locale)) {
            if (base.endsWith(".ftl")) return base.replace(".ftl","-ar.ftl");
        }
        return base;
    }
}