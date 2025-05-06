package org.hormigas.gateway.controllers;

import org.hormigas.gateway.config.security.SecurityHostsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SupportingController {

    private final SecurityHostsProperties cloudConfiguration;
    private String redirectUrl_prefix;
    private String redirectUrl_suffix;

    @PostConstruct
    public void init() {
        redirectUrl_prefix = cloudConfiguration.getKeycloakURL()
                + "/realms/"+ cloudConfiguration.getKeycloakRealm()
                +"/protocol/openid-connect/logout"
                + "?id_token_hint=";
        redirectUrl_suffix = "&post_logout_redirect_uri="+cloudConfiguration.getGatewayRedirectURL();
    }

    @GetMapping("/fallback")
    public Mono<String> fallback() {
        return Mono.just("Service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/logout")
    public Mono<Void> logout(@AuthenticationPrincipal OidcUser oidcUser, ServerWebExchange exchange) {
        String idToken = Optional.ofNullable(oidcUser).map(OidcUser::getIdToken).map(OidcIdToken::getTokenValue).orElse(null);
        if (!StringUtils.hasText(idToken)) return Mono.empty();

        String redirectUrl = redirectUrl_prefix + idToken + redirectUrl_suffix;
        log.debug("logout to url {}", redirectUrl);
        return exchange.getSession()
                .flatMap(WebSession::invalidate)
                .then(Mono.fromRunnable(SecurityContextHolder::clearContext))
                .then(Mono.fromRunnable(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    exchange.getResponse().getHeaders().setLocation(URI.create(redirectUrl));
                }))
                .then();
    }
}
