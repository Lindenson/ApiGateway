package org.hormigas.ws.feedback.events;

public record IncomingHealthEvent(boolean droppedDetected, int droppedCount) {}
