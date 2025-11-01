package org.hormigas.ws.feedback.events;

public record OutboxHealthEvent(boolean droppedDetected, int droppedCount) {}
