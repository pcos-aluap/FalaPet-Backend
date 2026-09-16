package com.falapet.event.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.auth.TutorIdentity;
import com.falapet.event.application.EventHistoryService;
import com.falapet.event.domain.EventHistoryItem;
import com.falapet.event.domain.EventMetadata;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.pagination.PageData;

@RestController
@RequestMapping(path="/api/v1/button-events",produces=ApiMediaTypes.APPLICATION_JSON_VALUE)
public final class EventHistoryController {
    private final EventHistoryService service;
    EventHistoryController(EventHistoryService service){this.service=service;}

    @GetMapping
    public ApiResponse<PageData<EventHistoryItem>> list(Authentication a,@RequestParam Map<String,String> q){
        var copy=new LinkedHashMap<>(q);
        String cursor=copy.remove("cursor"),limit=copy.remove("limit");
        if(!copy.keySet().stream().allMatch(k->java.util.Set.of("from","to","petId","buttonId","purpose","training","classification").contains(k)))throw new ContractException(ErrorCode.VALIDATION_ERROR);
        Integer size=null;
        try{
            if(limit!=null)size=Integer.valueOf(limit);
        }catch(NumberFormatException e){
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        return ApiResponse.of(service.list(tutor(a),copy,cursor,size));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventHistoryItem> find(Authentication a,@PathVariable UUID eventId){
        return ApiResponse.of(service.find(tutor(a),eventId));
    }

    @PutMapping("/{eventId}/pet-attribution")
    public ApiResponse<Data> putPet(Authentication a,@PathVariable UUID eventId,@RequestHeader(value="If-Match",required=false)String v,@RequestBody Map<String,Object>b){
        return ApiResponse.of(new Data(service.putPet(tutor(a),eventId,v,b)));
    }

    @DeleteMapping("/{eventId}/pet-attribution")
    public ApiResponse<Data> deletePet(Authentication a,@PathVariable UUID eventId,@RequestHeader(value="If-Match",required=false)String v){
        return ApiResponse.of(new Data(service.deletePet(tutor(a),eventId,v)));
    }

    @PutMapping("/{eventId}/classification")
    public ApiResponse<Data> putClassification(Authentication a,@PathVariable UUID eventId,@RequestHeader(value="If-Match",required=false)String v,@RequestBody Map<String,Object>b){
        return ApiResponse.of(new Data(service.putClassification(tutor(a),eventId,v,b)));
    }

    @DeleteMapping("/{eventId}/classification")
    public ResponseEntity<Void> deleteClassification(Authentication a,@PathVariable UUID eventId,@RequestHeader(value="If-Match",required=false)String v){
        service.deleteClassification(tutor(a),eventId,v);return ResponseEntity.noContent().build();
    }

    @PutMapping("/{eventId}/context")
    public ApiResponse<Data> putContext(Authentication a,@PathVariable UUID eventId,@RequestHeader(value="If-Match",required=false)String v,@RequestBody Map<String,Object>b){
        return ApiResponse.of(new Data(service.putContext(tutor(a),eventId,v,b)));
    }

    @DeleteMapping("/{eventId}/context")
    public ResponseEntity<Void> deleteContext(Authentication a,@PathVariable UUID eventId,@RequestHeader(value="If-Match",required=false)String v){
        service.deleteContext(tutor(a),eventId,v);return ResponseEntity.noContent().build();
    }

    private UUID tutor(Authentication a){if(a==null||!(a.getPrincipal() instanceof TutorIdentity t))throw new ContractException(ErrorCode.SESSION_INVALID);return t.tutorId();} public record Data(Object value){}
}
