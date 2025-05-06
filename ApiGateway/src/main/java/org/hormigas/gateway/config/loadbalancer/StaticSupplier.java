package org.hormigas.gateway.config.loadbalancer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
public class StaticSupplier implements ServiceInstanceListSupplier {
    private final String serviceId;
    private final List<ServiceInstance> instances;

    public StaticSupplier(String serviceId, List<String> hostPorts) {
        this.serviceId = serviceId;

        try {
            if (hostPorts == null || hostPorts.isEmpty())
                throw new IllegalArgumentException("hostPorts cannot be empty for " + serviceId);
            instances = IntStream.range(0, hostPorts.size())
                    .mapToObj(i -> {
                        String[] parts = hostPorts.get(i).split(":");
                        return new DefaultServiceInstance(
                                serviceId + "-instance" + i,
                                serviceId,
                                parts[0],
                                Integer.parseInt(parts[1]),
                                false
                        );
                    })
                    .map(ServiceInstance.class::cast)
                    .toList();
        } catch (Exception e) {
            log.error("Error building instances for {}", serviceId, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return Flux.just(instances);
    }
}