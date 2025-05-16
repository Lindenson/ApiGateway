package org.hormigas.gateway.filters;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtRoleGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtRoleGatewayFilterFactory.Config> {

    public static final String TOKEN_ATTRIBUTE_NAME = "status";

    public JwtRoleGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) ->
                exchange.getPrincipal()
                        .flatMap(auth -> {
                            if (auth instanceof JwtAuthenticationToken authentication) {
                                var principal = (Jwt) authentication.getPrincipal();
                                String status = principal.getClaim(TOKEN_ATTRIBUTE_NAME);
                                if (status.equals(config.getRole())) {
                                    return chain.filter(exchange);
                                } else {
                                    log.warn("Access denied: missing role {}", config.getRole());
                                    return deny(exchange, "Forbidden: role " + config.getRole() + " required");
                                }
                            }
                            return deny(exchange, "Forbidden: no authentication");
                        });
    }

    private Mono<Void> deny(ServerWebExchange exchange, String reason) {
        log.debug("Request denied: {}", reason);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Getter
    @Setter
    public static class Config {
        private String role;
    }
}
