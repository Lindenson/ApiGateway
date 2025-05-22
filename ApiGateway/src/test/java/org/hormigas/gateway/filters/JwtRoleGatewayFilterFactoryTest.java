package org.hormigas.gateway.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class JwtRoleGatewayFilterFactoryTest {

    private JwtRoleGatewayFilterFactory filterFactory;

    @BeforeEach
    void setUp() {
        filterFactory = new JwtRoleGatewayFilterFactory();
    }

    @Test
    void shouldAllowRequestWhenRoleMatches() {
        // given
        var config = new JwtRoleGatewayFilterFactory.Config();
        config.setRole("client");

        var jwt = mock(Jwt.class);
        when(jwt.getClaim("status")).thenReturn("client");

        var token = new JwtAuthenticationToken(jwt);


        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/somepath")
                .build());

        exchange = exchange.mutate().principal(Mono.just(token)).build();

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        var filter = filterFactory.apply(config);

        // when
        Mono<Void> result = filter.filter(exchange, chain);

        // then
        StepVerifier.create(result).verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void shouldRejectRequestWhenRoleDoesNotMatch() {
        var config = new JwtRoleGatewayFilterFactory.Config();
        config.setRole("client");

        var jwt = mock(Jwt.class);
        when(jwt.getClaim("status")).thenReturn("admin");

        var token = new JwtAuthenticationToken(jwt);

        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/somepath")
                .build());

        exchange = exchange.mutate().principal(Mono.just(token)).build();

        var chain = mock(GatewayFilterChain.class);
        var filter = filterFactory.apply(config);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void shouldRejectRequestWhenPrincipalIsNotJwt() {
        var config = new JwtRoleGatewayFilterFactory.Config();
        config.setRole("client");


        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/somepath")
                .build());

        exchange = exchange.mutate().principal(Mono.just(() -> "user")).build();

        var chain = mock(GatewayFilterChain.class);
        var filter = filterFactory.apply(config);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void shouldRejectRequestWhenNoPrincipal() {
        var config = new JwtRoleGatewayFilterFactory.Config();
        config.setRole("client");


        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/somepath")
                .build());

        exchange = exchange.mutate().principal(Mono.empty()).build();

        var chain = mock(GatewayFilterChain.class);
        var filter = filterFactory.apply(config);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }
}
