package cn.family.learning.chinese;

import cn.family.learning.auth.SecurityConfig.Identity;
import cn.family.learning.common.ApiEnvelope;
import cn.family.learning.common.Ids;
import cn.family.learning.common.Values;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping("/api/v1")
public class RecitationController {
    private final RecitationService service;

    public RecitationController(RecitationService service) {
        this.service=service;
    }

    @GetMapping("/recitation/lessons")
    Object lessons(@AuthenticationPrincipal Identity user) {
        return ApiEnvelope.ok(service.lessons(user.ownerId()));
    }

    @GetMapping("/recitation/lessons/{id}")
    Object lesson(@AuthenticationPrincipal Identity user,@PathVariable @Pattern(regexp=Ids.PATTERN) String id) {
        return ApiEnvelope.ok(service.lesson(user.ownerId(),id));
    }

    @PostMapping("/recitation/lessons")
    Object create(@AuthenticationPrincipal Identity user,@RequestHeader("Idempotency-Key") String key,
                  @Valid @RequestBody RecitationService.LessonInput input) {
        return ApiEnvelope.ok(service.create(user.ownerId(),key,input));
    }

    @PatchMapping("/recitation/lessons/{id}")
    Object update(@AuthenticationPrincipal Identity user,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,
                  @Valid @RequestBody RecitationService.LessonInput input) {
        return ApiEnvelope.ok(service.update(user.ownerId(),id,input));
    }

    @DeleteMapping("/recitation/lessons/{id}")
    Object delete(@AuthenticationPrincipal Identity user,@PathVariable @Pattern(regexp=Ids.PATTERN) String id,
                  @RequestParam @Min(1) int expectedRevision) {
        service.delete(user.ownerId(),id,expectedRevision);
        return ApiEnvelope.ok(Values.map("deleted",true));
    }

    @GetMapping("/children/{child}/recitation-attempts")
    Object history(@AuthenticationPrincipal Identity user,@PathVariable @Pattern(regexp=Ids.PATTERN) String child) {
        return ApiEnvelope.ok(service.history(user.ownerId(),child));
    }

    @PostMapping("/children/{child}/recitation-attempts")
    Object record(@AuthenticationPrincipal Identity user,@PathVariable @Pattern(regexp=Ids.PATTERN) String child,
                  @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RecitationService.AttemptInput input) {
        return ApiEnvelope.ok(service.record(user.ownerId(),child,key,input));
    }
}
