package org.hormigas.ws.ports.channel.presense.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.hormigas.ws.credits.Credits;


@Builder
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ClientSession<T> {
    private final String id;
    private final String name;
    private final Credits credits;
    private final long connectedAt = System.currentTimeMillis();
    private long lastActiveAt = System.currentTimeMillis();
    @EqualsAndHashCode.Include
    private final T session;

    public boolean tryConsumeCredits() {
        return credits.tryConsume();
    }
    public double getAvailableCredits() {
        return credits.getCurrentCredits();
    }
    public void updateActivity() {
        lastActiveAt = System.currentTimeMillis();
    }
}
