package com.vonoy.pdf_pipeline.normalize;
import org.springframework.stereotype.Component;

@Component
public class NormalizedValidator {
    public void validate(NormalizedData data, String apiKey){
        // Exemple minimal : vérifie quelques champs standards si présents dans tes règles
        // TODO: rends ça configurable (required fields par apiKey)
        // if (data.get("title") == null) throw new IllegalArgumentException("Missing title");
    }
}
