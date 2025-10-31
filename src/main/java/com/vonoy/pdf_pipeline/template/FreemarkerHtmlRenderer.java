package com.vonoy.pdf_pipeline.template;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.Map;

@Component
public class FreemarkerHtmlRenderer implements HtmlRenderer {
    private final Configuration cfg;
    public FreemarkerHtmlRenderer(Configuration cfg){ this.cfg = cfg; }

    @Override public String render(String templateId, Map<String,Object> model){
        try (var sw = new StringWriter()){
            Template t = cfg.getTemplate(templateId);
            t.process(model, sw);
            return sw.toString();
        } catch (Exception e){ throw new RuntimeException(e); }
    }
}
