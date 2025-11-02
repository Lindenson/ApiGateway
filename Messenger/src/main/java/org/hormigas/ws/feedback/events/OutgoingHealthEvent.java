package org.hormigas.ws.feedback.events;

public record OutgoingHealthEvent(boolean droppedDetected, int droppedCount) {}
