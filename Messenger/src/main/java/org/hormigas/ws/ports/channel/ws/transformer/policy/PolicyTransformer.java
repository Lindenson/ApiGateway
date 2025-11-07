package org.hormigas.ws.ports.channel.ws.transformer.policy;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.domain.Message;
import org.hormigas.ws.ports.channel.presense.dto.ClientSession;
import org.hormigas.ws.ports.channel.ws.transformer.Transformer;

@ApplicationScoped
public class PolicyTransformer implements Transformer<Message, WebSocketConnection> {
    @Override
    public Message apply(@Nullable Message message, @Nonnull ClientSession<WebSocketConnection> clientSession) {
        if (message == null) return null;
        double creditsDynamics = clientSession.getAvailableCredits();
        int grantedCredits = (int) Math.floor(creditsDynamics);
        return message.toBuilder().creditsAvailable(grantedCredits).build();
    }
}
