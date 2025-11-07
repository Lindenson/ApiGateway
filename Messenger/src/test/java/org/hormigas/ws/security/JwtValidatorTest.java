package org.hormigas.ws.security;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import org.hormigas.ws.ports.channel.ws.security.config.KeycloakConfig;
import org.hormigas.ws.ports.channel.ws.security.JwtValidator;
import org.hormigas.ws.ports.channel.presense.dto.ClientData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.text.ParseException;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JwtValidatorTest {

    private JwtValidator jwtValidator = new JwtValidator();
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor = mock(ConfigurableJWTProcessor.class);
    private final KeycloakConfig keycloakConfig = mock(KeycloakConfig.class);
    private static MockedStatic<SignedJWT> signedJWTMockedStatic;

    @BeforeAll
    static void startUp() {
        signedJWTMockedStatic = mockStatic(SignedJWT.class);
    }

    @BeforeEach
    void setUp() {
        jwtValidator.keycloakConfig = keycloakConfig;
        jwtValidator = spy(jwtValidator);
        jwtValidator.jwtProcessor = jwtProcessor;
    }

    @Test
    void validate_ShouldReturnClientData_WhenTokenIsValid() throws Exception {
        String token = "mock.jwt.token";

        SignedJWT signedJWT = mock(SignedJWT.class);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("client123")
                .claim("preferred_username", "userX")
                .expirationTime(new Date(System.currentTimeMillis() + 10000))
                .build();

        when(SignedJWT.parse(token)).thenReturn(signedJWT);
        when(jwtProcessor.process(signedJWT, null)).thenReturn(claims);
        Optional<ClientData> result = jwtValidator.validate(token);

        assertTrue(result.isPresent());
        assertEquals("client123", result.get().getId());
        assertEquals("userX", result.get().getName());
    }

    @Test
    void validate_ShouldReturnEmpty_WhenTokenIsExpired() throws Exception {
        String token = "expired.token";

        SignedJWT signedJWT = mock(SignedJWT.class);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("client123")
                .claim("preferred_username", "userX")
                .expirationTime(new Date(System.currentTimeMillis() - 10000))  // expired
                .build();

        when(SignedJWT.parse(token)).thenReturn(signedJWT);
        when(jwtProcessor.process(signedJWT, null)).thenReturn(claims);
        Optional<ClientData> result = jwtValidator.validate(token);
        assertTrue(result.isEmpty());
    }

    @Test
    void validate_ShouldReturnEmpty_WhenExceptionThrown() throws Exception {
        String token = "invalid.token";
        when(SignedJWT.parse(token)).thenThrow(new ParseException("bad token", 0));
        Optional<ClientData> result = jwtValidator.validate(token);
        assertTrue(result.isEmpty());
    }

    @AfterAll
    static void tearDown() {
        signedJWTMockedStatic.close();
    }
}
