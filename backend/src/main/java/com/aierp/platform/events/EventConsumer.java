package com.aierp.platform.events;
/** Post-commit consumers execute together in the outbox relay's independent transaction. */
public interface EventConsumer { void consume(DomainEvent event); }
