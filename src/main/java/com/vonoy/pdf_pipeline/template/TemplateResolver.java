package com.vonoy.pdf_pipeline.template;

import com.vonoy.pdf_pipeline.normalize.NormalizedData;

public interface TemplateResolver {
    String resolve(String apiKey, NormalizedData model);
}