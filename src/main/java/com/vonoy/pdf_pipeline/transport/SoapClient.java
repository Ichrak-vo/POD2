package com.vonoy.pdf_pipeline.transport;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringSource;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class SoapClient implements TransportClient {
    private final WebServiceTemplate ws;
    public SoapClient() {
        // Simple WS template; si tu utilises JAXB, configure le marshaller
        this.ws = new WebServiceTemplate(new Jaxb2Marshaller());
    }

    @Override public boolean supports(String type){ return "soap".equalsIgnoreCase(type); }

    @Override public RawPayload fetch(Map<String,Object> cfg, Map<String,Object> params){
        String endpoint = (String) cfg.get("endpoint");
        String envelope = (String) cfg.get("envelope"); // tu peux générer via FreeMarker côté appelant
        Source request = new StringSource(envelope);
        DOMResult result = new DOMResult();
        ws.sendSourceAndReceiveToResult(endpoint, request, result);
        String xml = XmlUtil.nodeToString(result.getNode()); // utilitaire ci-dessous
        return new RawPayload(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "application/xml", Map.of());
    }

    // utilitaire trivial
    static final class XmlUtil {
        static String nodeToString(org.w3c.dom.Node node){
            try {
                javax.xml.transform.Transformer t = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
                java.io.StringWriter sw = new java.io.StringWriter();
                t.transform(new javax.xml.transform.dom.DOMSource(node), new javax.xml.transform.stream.StreamResult(sw));
                return sw.toString();
            } catch (Exception e){ throw new RuntimeException(e); }
        }
    }
}