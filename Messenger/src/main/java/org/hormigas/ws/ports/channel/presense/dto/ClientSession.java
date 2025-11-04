package org.hormigas.ws.ports.channel.presense.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.hormigas.ws.credits.Credits;


@Builder
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ClientSession<T> {
    private final String clientId;
    private final Credits credits;
    private final long sessionCreatedTimestamp = System.currentTimeMillis();
    @EqualsAndHashCode.Include
    private final T session;

    public boolean tryConsumeCredits() {
        return credits.tryConsume();
    }
    public double getAvailableCredits() {
        return credits.getCurrentCredits();
    }
}
