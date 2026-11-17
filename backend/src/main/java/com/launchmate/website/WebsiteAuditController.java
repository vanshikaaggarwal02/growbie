package com.launchmate.website;

import com.launchmate.auth.CurrentUser;
import com.launchmate.website.WebsiteAuditDtos.AuditListItem;
import com.launchmate.website.WebsiteAuditDtos.AuditView;
import com.launchmate.website.WebsiteAuditDtos.StartAuditRequest;
import com.launchmate.website.WebsiteAuditDtos.StartAuditResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WebsiteAuditController {

    private final WebsiteAuditService audits;

    public WebsiteAuditController(WebsiteAuditService audits) {
        this.audits = audits;
    }

    /** Returns at once; poll the audit until its status is COMPLETED, PARTIAL or FAILED. */
    @PostMapping("/profiles/{profileId}/website-audits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartAuditResponse start(@PathVariable UUID profileId, @Valid @RequestBody StartAuditRequest request) {
        return audits.start(CurrentUser.id(), profileId, request);
    }

    @GetMapping("/profiles/{profileId}/website-audits")
    public List<AuditListItem> list(@PathVariable UUID profileId) {
        return audits.list(CurrentUser.id(), profileId);
    }

    @GetMapping("/profiles/{profileId}/website-audits/compare")
    public AuditComparison compare(@PathVariable UUID profileId,
                                   @org.springframework.web.bind.annotation.RequestParam(required = false) UUID from,
                                   @org.springframework.web.bind.annotation.RequestParam(required = false) UUID to) {
        return audits.compare(CurrentUser.id(), profileId, from, to);
    }

    @GetMapping("/website-audits/{auditId}")
    public AuditView get(@PathVariable UUID auditId) {
        return audits.get(CurrentUser.id(), auditId);
    }
}
