package com.vonoy.pdf_pipeline.api.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
@Getter @Setter @AllArgsConstructor
public class SourceConfig {

    @NotBlank String type;          // "rest" | "soap" | "file"
    @NotNull Map<String,Object> config;
    List<String> dependsOn ;           // réservé multi-sources
}
