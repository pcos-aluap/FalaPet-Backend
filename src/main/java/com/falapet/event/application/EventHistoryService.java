package com.falapet.event.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.falapet.event.domain.EventHistoryItem;
import com.falapet.event.domain.EventMetadata;
import com.falapet.pet.application.ActivePetFinder;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.idempotency.RequestFingerprint;
import com.falapet.shared.contract.pagination.CursorPageRequest;
import com.falapet.shared.contract.pagination.CursorState;
import com.falapet.shared.contract.pagination.PageData;
import com.falapet.shared.contract.pagination.PageMetadata;
import com.falapet.shared.contract.pagination.SignedCursorCodec;

@Service
public final class EventHistoryService {
    private static final Set<String> PURPOSES=Set.of("BEHAVIORAL","TEST");
    private static final Set<String> CLASSIFICATIONS=Set.of("APPARENTLY_INTENTIONAL","APPARENTLY_ACCIDENTAL","UNCERTAIN");
    private final EventHistoryStore store;
    private final ActivePetFinder pets;
    private final SignedCursorCodec cursors;
    private final RequestFingerprint fingerprints;

    EventHistoryService(EventHistoryStore store,ActivePetFinder pets,SignedCursorCodec cursors,RequestFingerprint fingerprints){
        this.store=store;this.pets=pets;this.cursors=cursors;this.fingerprints=fingerprints;
    }

    public EventHistoryItem find(UUID tutor,UUID event){
        return store.find(tutor,event).orElseThrow(()->notFound());
    }

    public PageData<EventHistoryItem> list(UUID tutor, Map<String,String> q, String cursor, Integer limit) {
        EventHistoryStore.Filters f=filters(q);
        String filter=fingerprints.forText(f.toString());
        String scope="button-event.history."+fingerprints.forText(tutor.toString());
        Instant before=null;
        UUID beforeId=null;
        var page=CursorPageRequest.of(cursor,limit);

        if(cursor!=null&&!cursor.isBlank()) {
            try {
                var s = cursors.decode(cursor);
                if (!scope.equals(s.scope()) || !filter.equals(s.filterFingerprint()) || s.position().size() != 2)
                    throw new IllegalArgumentException();
                before = Instant.parse(s.position().get(0));
                beforeId = UUID.fromString(s.position().get(1));
            } catch (RuntimeException ex) {
                throw new ContractException(ErrorCode.INVALID_CURSOR);
            }
        }

        List<EventHistoryItem> rows=store.list(tutor,f,before,beforeId,page.limit()+1);
        boolean more=rows.size()>page.limit();
        var items=more?rows.subList(0,page.limit()):rows;
        String next=null;

        if(more){
            var last=items.get(items.size()-1);
            next=cursors.encode(
                    new CursorState(scope,filter,List.of(last.occurredAt()
                            .toString(),
                            last.id().toString())));
        }

        return new PageData<>(items,new PageMetadata(next,more));
    }

    public EventMetadata.PetAttribution putPet(UUID tutor,UUID event,String ifMatch,Map<String,Object> body){
        if(body==null||!body.keySet().equals(Set.of("petId"))||!(body.get("petId") instanceof String raw))
            throw invalid();UUID pet=id(raw);
            if(!store.petOwnedBy(tutor,pet))
                throw notFound();
            var current=find(tutor,event).metadata().petAttribution();
            long v=expected(ifMatch,current==null?0:current.version());
            return store.replacePetAttribution(tutor,event,pet,v)
                    .orElseThrow(()->conflictOrNotFound(tutor,event));
    }

    public PetDeleteResult deletePet(UUID tutor,UUID event,String ifMatch){
        var item=find(tutor,event);
        var current=item.metadata().petAttribution();

        if(current==null)
            return new PetDeleteResult(null,false);

        if(!"TUTOR".equals(current.source()))
            return new PetDeleteResult(current,false);

        long v=expected(ifMatch,current.version());

        if(store.deleteTutorPetAttribution(tutor,event,v).isEmpty())
            throw conflictOrNotFound(tutor,event);

        var active=pets.activePetIds(tutor);

        if(active.size()==1){
            var restored=store.replacePetAttribution(tutor,event,active.getFirst(),0)
                    .orElseThrow(()->conflictOrNotFound(tutor,event));
            return new PetDeleteResult(restored,true);
        }

        return new PetDeleteResult(null,false);
    }

    public EventMetadata.Classification putClassification(UUID tutor,UUID event,String ifMatch,Map<String,Object> body){
        if(body==null||!body.keySet().equals(Set.of("value"))||!(body.get("value") instanceof String value)||!CLASSIFICATIONS.contains(value))
            throw invalid();
        var current=find(tutor,event).metadata().classification();

        return store
                .replaceClassification(tutor,event,value,expected(ifMatch,current==null?0:current.version()))
                .orElseThrow(()->conflictOrNotFound(tutor,event));
    }

    public void deleteClassification(UUID tutor,UUID event,String ifMatch){
        var c=find(tutor,event).metadata().classification();
        if(c==null)
            throw notFound();
        if(!store.deleteClassification(tutor,event,expected(ifMatch,c.version())))
            throw conflictOrNotFound(tutor,event);
    }

    public EventMetadata.Context putContext(UUID tutor,UUID event,String ifMatch,Map<String,Object> body){
        if(body==null||!Set.of("contextTypeId","note")
                .containsAll(body.keySet()))throw invalid();
        UUID type=null;String note=null;
        if(body.containsKey("contextTypeId")){
            if(!(body.get("contextTypeId") instanceof String v))
                throw invalid();type=id(v);
                if(!store.activeContextTypeOwnedBy(tutor,type))
                    throw notFound();
        }
        if(body.containsKey("note")){
            if(!(body.get("note") instanceof String v)||v.length()>1000||v.codePoints().anyMatch(Character::isISOControl))
                throw invalid();
            note=v.trim();
            if(note.isEmpty())note=null;
        }
        if(type==null&&note==null)
            throw invalid();
        var c=find(tutor,event).metadata().context();
        return store.replaceContext(tutor,event,type,note,expected(ifMatch,c==null?0:c.version()))
                .orElseThrow(()->conflictOrNotFound(tutor,event));
    }

    public void deleteContext(UUID tutor,UUID event,String ifMatch){
        var c=find(tutor,event).metadata().context();
        if(c==null)
            throw notFound();

        if(!store.deleteContext(tutor,event,expected(ifMatch,c.version())))
            throw conflictOrNotFound(tutor,event);
    }

    private EventHistoryStore.Filters filters(Map<String,String> q){
        try{
            Instant from=time(q.get("from")),to=time(q.get("to"));
            if(from!=null&&to!=null&&from.isAfter(to))
                throw invalid();String p=blank(q.get("purpose"));
                if(p!=null&&!PURPOSES.contains(p))
                    throw invalid();
                String c=blank(q.get("classification"));
                if(c!=null&&!CLASSIFICATIONS.contains(c))
                    throw invalid();
                Boolean training=bool(q.get("training"));
                return new EventHistoryStore
                        .Filters(from,to,uuid(q.get("petId")),uuid(q.get("buttonId")),p,training,c);
        }catch(ContractException e){
            throw e;
        }catch(RuntimeException e){
            throw invalid();
        }
    }

    private long expected(String h,long existing){
        if(existing==0){
            if(h==null||h.isBlank())
                return 0;
            throw invalid();
        }
        if(h==null||!h.matches("\\\"[1-9][0-9]*\\\""))
            throw invalid();
        try{
            return Long.parseLong(h.substring(1,h.length()-1));
        }catch(NumberFormatException e){
            throw invalid();
        }
    }

    private UUID id(String value){
        try{
            return UUID.fromString(value);
        }catch(IllegalArgumentException e){
            throw invalid();
        }
    }

    private UUID uuid(String v){
        return blank(v)==null?null:id(v);
    }

    private Instant time(String v){
        return blank(v)==null?null:Instant.parse(v);
    }

    private String blank(String v){
        return v==null||v.isBlank()?null:v;
    }

    private Boolean bool(String v){
        if(blank(v)==null) return null;
        if("true".equals(v))return true;
        if("false".equals(v))return false;
        throw invalid();
    }

    private ContractException invalid(){
        return new ContractException(ErrorCode.VALIDATION_ERROR);
    }

    private ContractException notFound(){
        return new ContractException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private ContractException conflictOrNotFound(UUID tutor,UUID event){
        if(store.find(tutor,event).isEmpty())return notFound();
        return new ContractException(ErrorCode.VERSION_CONFLICT);
    }

    public record PetDeleteResult(EventMetadata.PetAttribution petAttribution,boolean automaticAttributionRestored) {}
}
