package com.falapet.context.api;
import java.util.*; import org.springframework.http.ResponseEntity; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import com.falapet.auth.TutorIdentity; import com.falapet.context.application.ContextTypeService; import com.falapet.context.domain.ContextType; import com.falapet.shared.contract.http.*; import com.falapet.shared.contract.pagination.PageData;
@RestController @RequestMapping(path="/api/v1/context-types",produces=ApiMediaTypes.APPLICATION_JSON_VALUE) public final class ContextTypeController {
 private final ContextTypeService service; ContextTypeController(ContextTypeService s){service=s;}
 @PostMapping public ResponseEntity<ApiResponse<Data>> create(Authentication a,@RequestHeader("Idempotency-Key") String k,@RequestBody Map<String,Object>b){return ResponseEntity.status(201).body(ApiResponse.of(new Data(service.create(tutor(a),k,b))));}
 @GetMapping public ApiResponse<PageData<ContextType>> list(Authentication a,@RequestParam(required=false)String status,@RequestParam(required=false)String cursor,@RequestParam(required=false)Integer limit){return ApiResponse.of(service.list(tutor(a),status,cursor,limit));}
 @PatchMapping("/{id}") public ApiResponse<Data> update(Authentication a,@PathVariable UUID id,@RequestHeader("If-Match")String v,@RequestBody Map<String,Object>b){return ApiResponse.of(new Data(service.update(tutor(a),id,version(v),b)));}
 @PostMapping("/{id}/deactivation") public ApiResponse<Data> deactivate(Authentication a,@PathVariable UUID id){return ApiResponse.of(new Data(service.status(tutor(a),id,"INACTIVE")));}
 @DeleteMapping("/{id}/deactivation") public ApiResponse<Data> reactivate(Authentication a,@PathVariable UUID id){return ApiResponse.of(new Data(service.status(tutor(a),id,"ACTIVE")));}
 private UUID tutor(Authentication a){if(a==null||!(a.getPrincipal() instanceof TutorIdentity i))throw new ContractException(ErrorCode.SESSION_INVALID);return i.tutorId();} private long version(String h){if(h==null||!h.matches("\\\"[1-9][0-9]*\\\""))throw new ContractException(ErrorCode.VALIDATION_ERROR);return Long.parseLong(h.substring(1,h.length()-1));} public record Data(ContextType contextType){}
}
