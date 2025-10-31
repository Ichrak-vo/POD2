package com.vonoy.pdf_pipeline.template;
import java.util.Map;

public interface HtmlRenderer {
    String render(String templateId, Map<String,Object> model);
}