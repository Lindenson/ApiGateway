package org.hormigas.gateway.config.loadbalancer;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "hosts-lists")
public class HostsListProperties {

    @NotEmpty
    private List<String> masterHosts;

    @NotEmpty
    private List<String> clientHosts;
}
