package org.hormigas.gateway.config.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@LoadBalancerClient(name = "masters", configuration = LoadBalancerMasterConfiguration.class)
public class LoadBalancerMasterConfiguration {

    @Bean(name = "mastersInstanceListSupplier")
    public ServiceInstanceListSupplier masters(HostsListProperties properties,
                                               ConfigurableApplicationContext context) {

        ServiceInstanceListSupplier supplier = ServiceInstanceListSupplier.builder()
                .withBase(new StaticSupplier("masters", properties.getMasterHosts()))
                .withCaching()
                .withHealthChecks()
                .build(context);
        log.info("Load balancer list supplier created for MASTERS");
        return supplier;
    }
}
