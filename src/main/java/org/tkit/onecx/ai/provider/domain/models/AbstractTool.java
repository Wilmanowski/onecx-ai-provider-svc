package org.tkit.onecx.ai.provider.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

import org.tkit.onecx.ai.provider.domain.models.converters.ExecutionPolicyConverter;
import org.tkit.onecx.ai.provider.domain.models.enums.AuthMode;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;
import org.tkit.quarkus.jpa.models.TraceableEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class AbstractTool extends TraceableEntity {

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "TYPE")
    @Enumerated(EnumType.STRING)
    private ToolType type;

    @Column(name = "URL")
    private String url;

    @Column(name = "API_KEY")
    private String apiKey;

    @Column(name = "EXECUTION_POLICY")
    @Convert(converter = ExecutionPolicyConverter.class)
    private ExecutionPolicy executionPolicy;

    @Column(name = "AUTH_MODE")
    @Enumerated(EnumType.STRING)
    private AuthMode authMode;
}
