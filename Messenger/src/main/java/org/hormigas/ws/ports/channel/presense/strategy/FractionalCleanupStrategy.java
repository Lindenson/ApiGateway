package org.hormigas.ws.ports.channel.presense.strategy;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Slf4j
@ApplicationScoped
public class FractionalCleanupStrategy implements CleanupStrategy<WebSocketConnection> {


    private static final double CLEAN_FRACTION = 0.33;
    private static final int CLEAN_THRESHOLD = 20;

    @Override
    public void clean(WebSocketConnection tested, Set<WebSocketConnection> active, Consumer<WebSocketConnection> deregister) {
        int openSize = tested.getOpenConnections().size();
        if (Math.abs(openSize - active.size()) < CLEAN_THRESHOLD) return;

        List<WebSocketConnection> list = active.stream().toList();
        int n = (int) (list.size() * CLEAN_FRACTION);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int i = 0; i < n; i++) {
            WebSocketConnection conn = list.get(rnd.nextInt(list.size()));
            if (!conn.isOpen()) {
                deregister.accept(conn);
                log.debug("Pruned inactive connection {}", conn.id());
            }
        }
    }
}
