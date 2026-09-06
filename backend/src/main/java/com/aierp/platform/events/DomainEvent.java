package com.aierp.platform.events;
import java.util.*;
public record DomainEvent(UUID publicationId,String type,UUID aggregateId,UUID projectId,UUID actorId,List<UUID> recipients,long businessRevision) {}
