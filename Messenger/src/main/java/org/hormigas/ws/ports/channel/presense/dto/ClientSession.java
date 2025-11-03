package org.hormigas.ws.ports.channel.presense.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.hormigas.ws.credits.Credits;
import org.hormigas.ws.credits.lazy.LazyCreditsBuket;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ClientSession<T> {
    @Getter
    private final String clientId;
    private final Credits credits;
    @Getter
    private final long sessionCreatedTimestamp;
    @Getter
    @EqualsAndHashCode.Include
    private final T session;

    public ClientSession(String clientId, T session, int maxCredits, double refillRate) {
        this.clientId = clientId;
        this.session = session;
        this.credits = new LazyCreditsBuket(maxCredits, refillRate);
        this.sessionCreatedTimestamp = System.currentTimeMillis();
    }

    public boolean tryConsume() {
        return credits.tryConsume();
    }
    public double getAvailableCredits() {
        return credits.getCurrentCredits();
    }
}
