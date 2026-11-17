package com.launchmate.content;

import com.launchmate.auth.CurrentUser;
import com.launchmate.content.ContentDtos.ContentView;
import com.launchmate.content.ContentDtos.GenerateRequest;
import com.launchmate.content.ContentDtos.UpdateContentRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ContentController {

    private final ContentGenerationService content;

    public ContentController(ContentGenerationService content) {
        this.content = content;
    }

    /** Writes the content now (a few seconds) and returns it as a draft. */
    @PostMapping("/profiles/{profileId}/generated-content")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentView generate(@PathVariable UUID profileId, @Valid @RequestBody GenerateRequest request) {
        return content.generate(CurrentUser.id(), profileId, request);
    }

    @GetMapping("/profiles/{profileId}/generated-content")
    public List<ContentView> list(@PathVariable UUID profileId, @RequestParam(required = false) String type,
                                  @RequestParam(required = false) UUID recommendationId) {
        return content.list(CurrentUser.id(), profileId, type, recommendationId);
    }

    @GetMapping("/generated-content/{id}")
    public ContentView get(@PathVariable UUID id) {
        return content.get(CurrentUser.id(), id);
    }

    /** Approves one variant (or returns the content to draft). Nothing is published. */
    @PatchMapping("/generated-content/{id}")
    public ContentView update(@PathVariable UUID id, @Valid @RequestBody UpdateContentRequest request) {
        return content.update(CurrentUser.id(), id, request);
    }
}
