package com.falapet.event.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.falapet.event.application.EventHistoryStore;
import com.falapet.event.domain.EventHistoryItem;
import com.falapet.event.domain.EventMetadata;

/** Read/write projection for human metadata; it deliberately never updates button_event. */
@Component
final class JdbcEventHistoryStore implements EventHistoryStore {
    private static final String SELECT = """
        SELECT e.id, e.button_id, s.button_name, e.esp32_device_id, e.physical_button_id,
          e.esp_session_id, e.sequence, e.esp_uptime_ms, e.occurred_at, e.received_at,
          e.time_quality, e.transport, e.purpose, e.training_session_id, e.created_at,
          a.pet_id attribution_pet_id, a.origin attribution_origin, a.created_at attribution_updated_at,
          a.version attribution_version, c.value classification_value, c.updated_at classification_updated_at,
          c.version classification_version, x.context_type_id, x.context_type_name_snapshot, x.note,
          x.updated_at context_updated_at, x.version context_version
        FROM button_event e
        JOIN event_button_snapshot s ON s.event_id = e.id
        LEFT JOIN event_pet_attribution a ON a.event_id = e.id
        LEFT JOIN event_user_classification c ON c.event_id = e.id
        LEFT JOIN event_context x ON x.event_id = e.id
        """;
    private final JdbcTemplate jdbc;
    JdbcEventHistoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<EventHistoryItem> find(UUID tutorId, UUID eventId) {
        return jdbc.query(SELECT + " WHERE e.tutor_id = ? AND e.id = ?", (rs, n) -> map(rs), tutorId, eventId)
                .stream().findFirst();
    }
    @Override public List<EventHistoryItem> list(UUID tutorId, Filters f, Instant before, UUID beforeId, int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE e.tutor_id = ?");
        List<Object> args = new ArrayList<>(); args.add(tutorId);
        if (f.from() != null) { sql.append(" AND e.occurred_at >= ?"); args.add(Timestamp.from(f.from())); }
        if (f.to() != null) { sql.append(" AND e.occurred_at <= ?"); args.add(Timestamp.from(f.to())); }
        if (f.petId() != null) { sql.append(" AND a.pet_id = ?"); args.add(f.petId()); }
        if (f.buttonId() != null) { sql.append(" AND e.button_id = ?"); args.add(f.buttonId()); }
        if (f.purpose() != null) { sql.append(" AND e.purpose = ?"); args.add(f.purpose()); }
        if (f.training() != null) sql.append(f.training() ? " AND e.training_session_id IS NOT NULL" : " AND e.training_session_id IS NULL");
        if (f.classification() != null) { sql.append(" AND c.value = ?"); args.add(f.classification()); }
        if (before != null) { sql.append(" AND (e.occurred_at, e.id) < (?, ?)"); args.add(Timestamp.from(before)); args.add(beforeId); }
        sql.append(" ORDER BY e.occurred_at DESC, e.id DESC LIMIT ?"); args.add(limit);
        return jdbc.query(sql.toString(), (rs, n) -> map(rs), args.toArray());
    }
    @Override public Optional<EventMetadata.PetAttribution> replacePetAttribution(UUID tutor, UUID event, UUID pet, long v) {
        if (v == 0) {
            int inserted = jdbc.update("""
                INSERT INTO event_pet_attribution(event_id,pet_id,origin,created_at,version)
                SELECT ?, ?, 'TUTOR', current_timestamp, 1 WHERE EXISTS
                (SELECT 1 FROM button_event WHERE id=? AND tutor_id=?) ON CONFLICT DO NOTHING""", event, pet, event, tutor);
            return inserted == 1 ? attribution(tutor, event) : Optional.empty();
        }
        return jdbc.query("""
            UPDATE event_pet_attribution a SET pet_id=?,origin='TUTOR',created_at=current_timestamp,version=version+1
            FROM button_event e WHERE a.event_id=e.id AND e.tutor_id=? AND a.event_id=? AND a.version=? RETURNING a.*""",
            (rs,n)->attribution(rs), pet,tutor,event,v).stream().findFirst();
    }
    @Override public Optional<EventMetadata.PetAttribution> deleteTutorPetAttribution(UUID tutor, UUID event, long v) {
        return jdbc.query("""
            DELETE FROM event_pet_attribution a USING button_event e WHERE a.event_id=e.id
            AND e.tutor_id=? AND a.event_id=? AND a.origin='TUTOR' AND a.version=? RETURNING a.*""",
            (rs,n)->attribution(rs), tutor,event,v).stream().findFirst();
    }
    @Override public Optional<EventMetadata.Classification> replaceClassification(UUID tutor, UUID event, String value, long v) {
        if (v == 0) {
            int inserted=jdbc.update("""
                INSERT INTO event_user_classification(event_id,value,updated_at,version)
                SELECT ?,?,current_timestamp,1 WHERE EXISTS(SELECT 1 FROM button_event WHERE id=? AND tutor_id=?) ON CONFLICT DO NOTHING""",event,value,event,tutor);
            return inserted==1?classification(tutor,event):Optional.empty();
        }
        return jdbc.query("""
            UPDATE event_user_classification c SET value=?,updated_at=current_timestamp,version=version+1
            FROM button_event e WHERE c.event_id=e.id AND e.tutor_id=? AND c.event_id=? AND c.version=? RETURNING c.*""",
            (rs,n)->classification(rs),value,tutor,event,v).stream().findFirst();
    }
    @Override public boolean deleteClassification(UUID tutor, UUID event, long v) { return jdbc.update("""
        DELETE FROM event_user_classification c USING button_event e WHERE c.event_id=e.id
        AND e.tutor_id=? AND c.event_id=? AND c.version=?""",tutor,event,v)>0; }
    @Override public Optional<EventMetadata.Context> replaceContext(UUID tutor, UUID event, UUID type, String note, long v) {
        if(v==0) { int n=jdbc.update("""
          INSERT INTO event_context(event_id,context_type_id,context_type_name_snapshot,note,updated_at,version)
          SELECT ?,ct.id,ct.name,?,current_timestamp,1 FROM context_type ct WHERE ct.id=? AND ct.tutor_id=? AND ct.status='ACTIVE'
          AND EXISTS(SELECT 1 FROM button_event e WHERE e.id=? AND e.tutor_id=?) ON CONFLICT DO NOTHING""",event,note,type,tutor,event,tutor);
          if(type==null) n=jdbc.update("""
          INSERT INTO event_context(event_id,note,updated_at,version)
          SELECT ?,?,current_timestamp,1 WHERE EXISTS(SELECT 1 FROM button_event e WHERE e.id=? AND e.tutor_id=?) ON CONFLICT DO NOTHING""",event,note,event,tutor);
          return n==1?context(tutor,event):Optional.empty(); }
        String snapshot = type == null ? null : jdbc.query("select name from context_type where id=? and tutor_id=? and status='ACTIVE'",(rs,n)->rs.getString(1),type,tutor).stream().findFirst().orElse(null);
        if(type != null && snapshot == null) return Optional.empty();
        return jdbc.query("""
          UPDATE event_context x SET context_type_id=?,context_type_name_snapshot=?,note=?,updated_at=current_timestamp,version=version+1
          FROM button_event e WHERE x.event_id=e.id AND e.tutor_id=? AND x.event_id=? AND x.version=? RETURNING x.*""",
          (rs,n)->context(rs),type,snapshot,note,tutor,event,v).stream().findFirst();
    }
    @Override public boolean deleteContext(UUID tutor,UUID event,long v) {return jdbc.update("""
        DELETE FROM event_context x USING button_event e WHERE x.event_id=e.id
        AND e.tutor_id=? AND x.event_id=? AND x.version=?""",tutor,event,v)>0;}
    @Override public boolean petOwnedBy(UUID tutor, UUID pet) { return !jdbc.query("select 1 from pet where id=? and tutor_id=?",(rs,n)->1,pet,tutor).isEmpty(); }
    @Override public boolean activeContextTypeOwnedBy(UUID tutor, UUID id) { return !jdbc.query("select 1 from context_type where id=? and tutor_id=? and status='ACTIVE'",(rs,n)->1,id,tutor).isEmpty(); }
    private Optional<EventMetadata.PetAttribution> attribution(UUID tutor,UUID event){return jdbc.query("select * from event_pet_attribution a join button_event e on e.id=a.event_id where e.tutor_id=? and a.event_id=?",(rs,n)->attribution(rs),tutor,event).stream().findFirst();}
    private Optional<EventMetadata.Classification> classification(UUID tutor,UUID event){return jdbc.query("select * from event_user_classification c join button_event e on e.id=c.event_id where e.tutor_id=? and c.event_id=?",(rs,n)->classification(rs),tutor,event).stream().findFirst();}
    private Optional<EventMetadata.Context> context(UUID tutor,UUID event){return jdbc.query("select * from event_context x join button_event e on e.id=x.event_id where e.tutor_id=? and x.event_id=?",(rs,n)->context(rs),tutor,event).stream().findFirst();}
    private EventMetadata.PetAttribution attribution(ResultSet r)throws SQLException{return new EventMetadata.PetAttribution(r.getObject("pet_id",UUID.class),r.getString("origin"),r.getTimestamp("created_at").toInstant(),r.getLong("version"));}
    private EventMetadata.Classification classification(ResultSet r)throws SQLException{return new EventMetadata.Classification(r.getString("value"),r.getTimestamp("updated_at").toInstant(),r.getLong("version"));}
    private EventMetadata.Context context(ResultSet r)throws SQLException{return new EventMetadata.Context(r.getObject("context_type_id",UUID.class),r.getString("context_type_name_snapshot"),r.getString("note"),r.getTimestamp("updated_at").toInstant(),r.getLong("version"));}
    private EventHistoryItem map(ResultSet r)throws SQLException { EventMetadata.PetAttribution a=r.getObject("attribution_pet_id")==null?null:new EventMetadata.PetAttribution(r.getObject("attribution_pet_id",UUID.class),r.getString("attribution_origin"),r.getTimestamp("attribution_updated_at").toInstant(),r.getLong("attribution_version")); EventMetadata.Classification c=r.getString("classification_value")==null?null:new EventMetadata.Classification(r.getString("classification_value"),r.getTimestamp("classification_updated_at").toInstant(),r.getLong("classification_version")); EventMetadata.Context x=r.getObject("context_type_id")==null&&r.getString("note")==null?null:new EventMetadata.Context(r.getObject("context_type_id",UUID.class),r.getString("context_type_name_snapshot"),r.getString("note"),r.getTimestamp("context_updated_at").toInstant(),r.getLong("context_version")); return new EventHistoryItem(r.getObject("id",UUID.class),r.getObject("button_id",UUID.class),r.getString("button_name"),r.getObject("esp32_device_id",UUID.class),r.getString("physical_button_id"),r.getObject("esp_session_id",UUID.class),r.getLong("sequence"),r.getLong("esp_uptime_ms"),r.getTimestamp("occurred_at").toInstant(),r.getTimestamp("received_at").toInstant(),r.getString("time_quality"),r.getString("transport"),r.getString("purpose"),r.getObject("training_session_id",UUID.class),r.getTimestamp("created_at").toInstant(),new EventMetadata(a,c,x)); }
}
