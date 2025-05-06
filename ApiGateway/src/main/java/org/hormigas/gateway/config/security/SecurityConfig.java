package org.hormigas.gateway.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collection;


@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();
        delegate.setAuthorityPrefix("ROLE_");
        delegate.setAuthoritiesClaimName("realm_access.roles");

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = delegate.convert(jwt);
            return Flux.fromStream(authorities.stream());
        });

        return converter;
    }


    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, SecurityHostsProperties securityHostsProperties) {
        http
                .authorizeExchange(exchanges ->
                        exchanges
                                .pathMatchers("/actuator/**")
                                .access(actuatorByHostNameAccessManager(securityHostsProperties))
                                .pathMatchers("/login**", "/logout**").permitAll()
                                .anyExchange().authenticated()
                )
                .oauth2Login(oAuth2LoginSpec -> oAuth2LoginSpec.loginPage("/login"));
        return http.build();
    }


    @Bean
    public RouterFunction<ServerResponse> loginRedirectRouter() {
        return RouterFunctions
                .route(RequestPredicates.GET("/login"),
                        request -> ServerResponse
                                .temporaryRedirect(URI.create("/oauth2/authorization/keycloak")).build()
                );
    }


    private static ReactiveAuthorizationManager<AuthorizationContext> actuatorByHostNameAccessManager(
            SecurityHostsProperties securityHostsProperties
    ) {
        return (authenticationMono, context) -> {
            var request = context.getExchange().getRequest();
            var remoteAddress = request.getRemoteAddress();

            if (remoteAddress != null) {
                String host = remoteAddress.getHostName();
                boolean allowed = securityHostsProperties.getPrometheusHostName().equals(host);
                return Mono.just(new AuthorizationDecision(allowed));
            }
            log.warn("Access to actuator denied for {}", remoteAddress);
            return Mono.just(new AuthorizationDecision(false));
        };
    }
}
