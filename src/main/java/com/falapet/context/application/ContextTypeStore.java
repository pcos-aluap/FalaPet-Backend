package com.falapet.context.application;
import java.time.Instant; import java.util.List; import java.util.Optional; import java.util.UUID;
import com.falapet.context.domain.ContextType;
public interface ContextTypeStore {
 Optional<ContextType> find(UUID tutorId, UUID id); List<ContextType> list(UUID tutorId, String status, Instant before, UUID beforeId, int limit);
 ContextType create(UUID tutorId, String key, String fingerprint, String name); Optional<ContextType> update(UUID tutorId, UUID id, long version, String name); Optional<ContextType> status(UUID tutorId, UUID id, String status);
}
