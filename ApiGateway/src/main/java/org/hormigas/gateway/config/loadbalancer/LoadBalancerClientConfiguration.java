package org.hormigas.gateway.config.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@LoadBalancerClient(name = "clients", configuration = LoadBalancerClientConfiguration.class)
public class LoadBalancerClientConfiguration {

    @Bean(name = "clientsInstanceListSupplier")
    public ServiceInstanceListSupplier clients(HostsListProperties properties,
                                               ConfigurableApplicationContext context) {

        ServiceInstanceListSupplier supplier = ServiceInstanceListSupplier.builder()
                .withBase(new StaticSupplier("clients", properties.getClientHosts()))
                .withCaching()
                .withHealthChecks()
                .build(context);
        log.info("Load balancer list supplier created for CLIENTS");
        return supplier;
    }
}

