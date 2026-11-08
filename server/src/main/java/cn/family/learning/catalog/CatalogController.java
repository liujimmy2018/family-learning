package cn.family.learning.catalog;

import cn.family.learning.auth.SecurityConfig.Identity;
import cn.family.learning.common.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.Valid;
import javax.validation.constraints.*;
import static cn.family.learning.catalog.CatalogDtos.*;

@Validated @RestController @RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;private final ExcelCatalog excel;private final ReviewService reviews;
    public CatalogController(CatalogService catalog,ExcelCatalog excel,ReviewService reviews){this.catalog=catalog;this.excel=excel;this.reviews=reviews;}
    private static final String SUBJECTS="ENGLISH|CHINESE";
    @GetMapping("/catalog/template.xlsx") ResponseEntity<byte[]> template(@RequestParam(defaultValue="ENGLISH") @Pattern(regexp=SUBJECTS) String subject)throws Exception{return ResponseEntity.ok().header("Content-Disposition","attachment; filename=word-catalog-template.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(excel.template("CHINESE".equals(subject)));}
    @PostMapping("/catalog/preview") Object preview(@RequestPart("file") MultipartFile file,@RequestParam(defaultValue="ENGLISH") @Pattern(regexp=SUBJECTS) String subject)throws Exception{return ApiEnvelope.ok(excel.preview(file,"CHINESE".equals(subject)));}
    @GetMapping("/catalog/collections") Object list(@AuthenticationPrincipal Identity i,@RequestParam(defaultValue="ENGLISH") @Pattern(regexp=SUBJECTS) String subject){return ApiEnvelope.ok(catalog.collections(i.ownerId(),subject));}
    @PostMapping("/catalog/collections") Object create(@AuthenticationPrincipal Identity i,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ImportInput input,@RequestParam(defaultValue="ENGLISH") @Pattern(regexp=SUBJECTS) String subject){return ApiEnvelope.ok(catalog.importEntries(i.ownerId(),key,input,subject));}
    @GetMapping("/catalog/collections/{id}") Object get(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String id){return ApiEnvelope.ok(catalog.detail(i.ownerId(),id));}
    @PatchMapping("/catalog/collections/{id}") Object rename(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@Valid @RequestBody Rename input){return ApiEnvelope.ok(catalog.rename(i.ownerId(),id,input));}
    @DeleteMapping("/catalog/collections/{id}") Object delete(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@RequestParam @Min(1) int expectedRevision){catalog.deleteCollection(i.ownerId(),id,expectedRevision);return ApiEnvelope.ok(Values.map("deleted",true));}
    @PostMapping("/catalog/collections/{id}/entries") Object add(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@Valid @RequestBody EntryChange input){return ApiEnvelope.ok(catalog.saveEntry(i.ownerId(),id,null,input));}
    @PatchMapping("/catalog/collections/{id}/entries/{entry}") Object edit(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@PathVariable @Pattern(regexp=Ids.PATTERN) String entry,@Valid @RequestBody EntryChange input){return ApiEnvelope.ok(catalog.saveEntry(i.ownerId(),id,entry,input));}
    @DeleteMapping("/catalog/collections/{id}/entries/{entry}") Object remove(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@PathVariable @Pattern(regexp=Ids.PATTERN) String entry,@RequestParam @Min(1) int expectedRevision){return ApiEnvelope.ok(catalog.deleteEntry(i.ownerId(),id,entry,expectedRevision));}
    @PostMapping("/children/{child}/catalog-practices") Object practice(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Selection input){return ApiEnvelope.ok(catalog.createPractice(i.ownerId(),child,key,input));}
    @GetMapping("/children/{child}/practices/{id}/review") Object review(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id){return ApiEnvelope.ok(reviews.review(i.ownerId(),child,id));}
    @PostMapping("/children/{child}/practices/{id}/timing") Object timing(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@Valid @RequestBody Timing input){return ApiEnvelope.ok(reviews.timing(i.ownerId(),child,id,input));}
    @PostMapping("/children/{child}/practices/{id}/review") Object grade(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@Valid @RequestBody GradeInput input){return ApiEnvelope.ok(reviews.grade(i.ownerId(),child,id,input));}
    @GetMapping("/children/{child}/wrong-words") Object wrong(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@RequestParam(defaultValue="ENGLISH") @Pattern(regexp=SUBJECTS) String subject){return ApiEnvelope.ok(reviews.wrongWords(i.ownerId(),child,subject));}
    @GetMapping("/children/{child}/dictation-history") Object history(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@RequestParam(defaultValue="ALL") String filter,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(defaultValue="ENGLISH") @Pattern(regexp=SUBJECTS) String subject){return ApiEnvelope.ok(reviews.history(i.ownerId(),child,filter,page,size,subject));}
    @PatchMapping("/children/{child}/wrong-words/{id}") Object mastery(@AuthenticationPrincipal Identity i,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,@Valid @RequestBody Mastery input){reviews.mastery(i.ownerId(),child,id,input);return ApiEnvelope.ok(Values.map("saved",true));}
}
