package com.vonoy.pdf_pipeline.transport;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
public class TransportRegistry {
    private final List<TransportClient> clients;
    public TransportRegistry(List<TransportClient> clients){ this.clients = clients; }
    public TransportClient get(String type){
        return clients.stream().filter(c -> c.supports(type))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No transport for type: " + type));
    }
}