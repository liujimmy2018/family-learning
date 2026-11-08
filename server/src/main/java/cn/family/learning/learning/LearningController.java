package cn.family.learning.learning;

import cn.family.learning.common.Values;

import cn.family.learning.ai.JobService;
import cn.family.learning.auth.SecurityConfig.Identity;
import cn.family.learning.common.ApiEnvelope;
import cn.family.learning.learning.Dtos.*;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import cn.family.learning.common.Ids;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1")
public class LearningController {
    private final LearningService service;private final AssetService assets;private final JobService jobs;
    public LearningController(LearningService service,AssetService assets,JobService jobs){this.service=service;this.assets=assets;this.jobs=jobs;}
    @GetMapping("/children") Object children(@AuthenticationPrincipal Identity i){return ApiEnvelope.ok(service.children(i.ownerId()));}
    @PostMapping("/children") Object create(@AuthenticationPrincipal Identity i,@Valid @RequestBody ChildInput r){return ApiEnvelope.ok(service.createChild(i.ownerId(),r));}
    @PatchMapping("/children/{child}") Object update(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@Valid @RequestBody ChildInput r){return ApiEnvelope.ok(service.updateChild(i.ownerId(),child,r));}
    @DeleteMapping("/children/{child}") Object archive(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child){service.archiveChild(i.ownerId(),child);return ApiEnvelope.ok(Values.map("archived",true));}
    @PostMapping("/children/{child}/assets") Object upload(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@RequestPart("file")MultipartFile file)throws Exception{cn.family.learning.data.Rows.Asset a=assets.upload(i.ownerId(),child,file);return ApiEnvelope.ok(new AssetDto(a.id.toString(),a.contentType,a.sizeBytes,a.width,a.height));}
    @GetMapping("/children/{child}/assets/{id}/content") ResponseEntity<byte[]> content(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id)throws Exception{cn.family.learning.data.Rows.Asset a=assets.asset(i.ownerId(),child,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.contentType)).cacheControl(CacheControl.noStore()).body(assets.content(i.ownerId(),child,id));}
    @PostMapping("/children/{child}/recognitions") ResponseEntity<?> recognize(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody MaterialInput r){return ResponseEntity.accepted().body(ApiEnvelope.ok(jobs.recognize(i.ownerId(),child,key,r)));}
    @GetMapping("/children/{child}/jobs/{id}") Object job(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id){return ApiEnvelope.ok(jobs.get(i.ownerId(),child,id));}
    @PostMapping("/children/{child}/audio-preparations") ResponseEntity<?> speech(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@Valid @RequestBody SpeechInput r){return ResponseEntity.accepted().body(ApiEnvelope.ok(jobs.speech(i.ownerId(),child,r)));}
    @PostMapping("/children/{child}/word-lookups") ResponseEntity<?> lookup(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@Valid @RequestBody LookupInput r){return ResponseEntity.accepted().body(ApiEnvelope.ok(jobs.lookup(i.ownerId(),child,r)));}
    @GetMapping("/children/{child}/lists") Object lists(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child){return ApiEnvelope.ok(service.lists(i.ownerId(),child));}
    @GetMapping("/children/{child}/lists/{id}") Object list(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id){return ApiEnvelope.ok(service.list(i.ownerId(),child,id));}
    @PostMapping("/children/{child}/lists") Object save(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody ListInput r){return ApiEnvelope.ok(service.saveList(i.ownerId(),child,null,key,r));}
    @PostMapping("/children/{child}/lists/{id}/versions") Object version(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody ListInput r){return ApiEnvelope.ok(service.saveList(i.ownerId(),child,id,key,r));}
    @PostMapping("/children/{child}/practices") Object start(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody PracticeInput r){return ApiEnvelope.ok(service.createPractice(i.ownerId(),child,key,r));}
    @GetMapping("/children/{child}/practices") Object history(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child){return ApiEnvelope.ok(service.practices(i.ownerId(),child));}
    @GetMapping("/children/{child}/practices/{id}") Object practice(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id){return ApiEnvelope.ok(service.practice(i.ownerId(),child,id));}
    @PostMapping("/children/{child}/practices/{id}/events") Object progress(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@Valid @RequestBody ProgressInput r){return ApiEnvelope.ok(service.progress(i.ownerId(),child,id,r));}
}
