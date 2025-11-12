package org.hormigas.ws.infrastructure.websocket.outbound.transformers;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import org.hormigas.ws.domain.message.Message;
import org.hormigas.ws.domain.session.ClientSession;

@ApplicationScoped
public class CreditsTransformer implements Transformer<Message, WebSocketConnection> {
    @Override
    public Message apply(@Nullable Message message, @Nonnull ClientSession<WebSocketConnection> clientSession) {
        if (message == null) return null;
        double creditsDynamics = clientSession.getAvailableCredits();
        int grantedCredits = (int) Math.floor(creditsDynamics);
        return message.toBuilder().creditsAvailable(grantedCredits).build();
    }
}
