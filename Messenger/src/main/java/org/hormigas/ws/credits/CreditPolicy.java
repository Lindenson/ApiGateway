package org.hormigas.ws.credits;

import java.util.function.Predicate;

public interface CreditPolicy<T> extends Predicate<T> {}
