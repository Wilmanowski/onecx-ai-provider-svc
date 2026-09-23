package org.tkit.onecx.ai.provider.common.services.provider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.tkit.onecx.ai.provider.domain.models.Provider;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.RuntimeSnapshotMapper;

import gen.org.tkit.onecx.ai.provider.runtime.client.api.RuntimeInternalApi;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ProviderHealthRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ProviderHealthService {

    private static final String UNHEALTHY = "UNHEALTHY";
    private static final String HEALTHY = "HEALTHY";

    @Inject
    @RestClient
    RuntimeInternalApi runtimeProviderHealthClient;

    @Inject
    RuntimeSnapshotMapper runtimeSnapshotMapper;

    public String getProviderHealthStatus(Provider provider) {
        if (provider == null || provider.getType() == null) {
            return UNHEALTHY;
        }
        Response response = null;
        try {
            response = runtimeProviderHealthClient
                    .getProviderHealthStatus(new ProviderHealthRequest().provider(runtimeSnapshotMapper.mapProvider(provider)));
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                log.warn("Runtime provider health check failed with HTTP status {}", response.getStatus());
                return UNHEALTHY;
            } else {
                return HEALTHY;
            }
        } catch (Exception ex) {
            log.warn("Error invoking runtime provider health API: {}", ex.getMessage());
            log.debug("Runtime provider health API failure details", ex);
            return UNHEALTHY;
        } finally {
            closeQuietly(response);
        }
    }

    private static void closeQuietly(Response response) {
        if (response != null) {
            try {
                response.close();
            } catch (Exception closeEx) {
                log.warn("Error closing runtime provider health response: {}", closeEx.getMessage());
                log.debug("Runtime provider health response close failure details", closeEx);
            }
        }
    }
}
