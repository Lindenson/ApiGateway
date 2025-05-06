package org.hormigas.gateway.config.security;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "cloud-security")
public class SecurityHostsProperties {
    @URL
    private String keycloakURL = "https://localhost:8443";
    private String keycloakRealm = "hormigas";

    @URL
    private String gatewayRedirectURL = "https://localhost";

    @NotEmpty
    private String prometheusHostName = "prometheus";
}
