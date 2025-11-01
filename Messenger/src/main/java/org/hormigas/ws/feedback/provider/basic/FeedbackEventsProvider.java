package org.hormigas.ws.feedback.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class FeedbackEventsProvider implements InEventProvider<IncomingHealthEvent>, OutEventProvider<OutgoingHealthEvent> {

    @Inject
    Event<OutgoingHealthEvent> eventBusOutgoing;

    @Inject
    Event<IncomingHealthEvent> eventBusIncoming;

    public void fireOut(OutgoingHealthEvent event) {
        eventBusOutgoing.fire(event);
    }

    public void fireIn(IncomingHealthEvent event) {
        eventBusIncoming.fire(event);
    }
}
