package com.falapet.context.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.falapet.context.application.ContextTypeStore;
import com.falapet.context.domain.ContextType;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.idempotency.IdempotencyClaim;
import com.falapet.shared.contract.idempotency.IdempotencyPayloadConflictException;
import com.falapet.shared.contract.idempotency.IdempotencyStore;
import com.falapet.shared.contract.idempotency.StoredHttpResponse;
import com.falapet.shared.contract.http.ApiMediaTypes;
import tools.jackson.databind.ObjectMapper;

@Component final class JdbcContextTypeStore implements ContextTypeStore {
 private static final String SCOPE="context-type.create"; private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final IdempotencyStore ids; private final ObjectMapper json;
 JdbcContextTypeStore(JdbcTemplate jdbc, DataSource ds, IdempotencyStore ids, ObjectMapper json) { this.jdbc=jdbc; this.tx=new TransactionTemplate(new DataSourceTransactionManager(ds)); this.ids=ids; this.json=json; }
 public Optional<ContextType> find(UUID tutor, UUID id) { return jdbc.query("select * from context_type where tutor_id=? and id=?",(rs,n)->map(rs),tutor,id).stream().findFirst(); }
 public List<ContextType> list(UUID tutor,String status,Instant before,UUID beforeId,int limit) { String where="tutor_id=?"+("ALL".equals(status)?"":" and status=?")+(before==null?"":" and (created_at,id)<(?,?)"); var args=new java.util.ArrayList<Object>(); args.add(tutor);if(!"ALL".equals(status))args.add(status);if(before!=null){args.add(Timestamp.from(before));args.add(beforeId);}args.add(limit);return jdbc.query("select * from context_type where "+where+" order by created_at desc,id desc limit ?",(rs,n)->map(rs),args.toArray()); }
 public ContextType create(UUID tutor,String key,String fp,String name) { try{return tx.execute(s->{var c=ids.claim(SCOPE,tutor.toString(),key,fp);if(c.status()==IdempotencyClaim.Status.IN_PROGRESS)throw new ContractException(ErrorCode.SERVICE_UNAVAILABLE);if(c.status()==IdempotencyClaim.Status.REPLAY)return json.readValue(c.storedResponse().body(),ContextType.class);var now=Instant.now();var id=UUID.randomUUID();jdbc.update("insert into context_type(id,tutor_id,name,status,created_at,updated_at,version) values(?,?,?,'ACTIVE',?,?,1)",id,tutor,name,Timestamp.from(now),Timestamp.from(now));var created=find(tutor,id).orElseThrow();ids.complete(SCOPE,tutor.toString(),key,fp,new StoredHttpResponse(201,ApiMediaTypes.APPLICATION_JSON_VALUE,json.writeValueAsBytes(created)));return created;});}catch(IdempotencyPayloadConflictException e){throw new ContractException(ErrorCode.DUPLICATE_RESOURCE);} }
 public Optional<ContextType> update(UUID tutor,UUID id,long v,String name){return jdbc.query("update context_type set name=?,updated_at=current_timestamp,version=version+1 where tutor_id=? and id=? and version=? returning *",(rs,n)->map(rs),name,tutor,id,v).stream().findFirst();}
 public Optional<ContextType> status(UUID tutor,UUID id,String status){return jdbc.query("update context_type set status=?,updated_at=current_timestamp,version=version+1 where tutor_id=? and id=? and status<>? returning *",(rs,n)->map(rs),status,tutor,id,status).stream().findFirst().or(()->find(tutor,id));}
 private ContextType map(java.sql.ResultSet r)throws java.sql.SQLException{return new ContextType(UUID.fromString(r.getString("id")),r.getString("name"),r.getString("status"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getLong("version"));}
}
