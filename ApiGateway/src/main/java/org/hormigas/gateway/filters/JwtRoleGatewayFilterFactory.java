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
                        .switchIfEmpty(Mono.defer(() -> deny(exchange, "Forbidden: no principal")).then(Mono.empty()))
                        .flatMap(auth -> {
                            if (auth instanceof JwtAuthenticationToken authentication) {
                                var principal = (Jwt) authentication.getPrincipal();
                                String status = principal.getClaim(TOKEN_ATTRIBUTE_NAME);
                                if (status.equals(config.getRole())) {
                                    return chain.filter(exchange);
                                } else {
                                    String reason = String.format("Forbidden: missing role %s for user %s", config.getRole(), principal.getSubject());
                                    return deny(exchange, reason);
                                }
                            }
                            return deny(exchange, "Forbidden: no authentication");
                        })
                        .onErrorResume(ex -> {
                            log.error("Error in JwtRole filter", ex);
                            return deny(exchange, "Forbidden: internal error");
                        });
    }

    private Mono<Void> deny(ServerWebExchange exchange, String reason) {
        log.warn("Request denied: {}", reason);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Getter
    @Setter
    public static class Config {
        private String role;
    }
}
