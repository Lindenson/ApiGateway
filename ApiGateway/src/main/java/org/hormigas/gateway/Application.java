package org.hormigas.gateway;

import org.hormigas.gateway.config.security.SecurityHostsProperties;
import org.hormigas.gateway.config.loadbalancer.HostsListProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

@SpringBootApplication
@EnableWebFluxSecurity
@EnableConfigurationProperties({SecurityHostsProperties.class, HostsListProperties.class})
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
