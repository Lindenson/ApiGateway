package org.hormigas.ws.ports.channel.registry.inmemory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.hormigas.ws.core.presence.local.LocalAdapter;

@Slf4j
@ApplicationScoped
public class PresenceAdapter implements LocalAdapter {

    @Inject
    LocalRegistry localRegistry;

    @Override
    public boolean isLocallyPresent(String userId) {
        boolean clientConnected = localRegistry.isClientConnected(userId);
        log.debug("User {} local presence {}", userId, clientConnected);
        return clientConnected;
    }
}
