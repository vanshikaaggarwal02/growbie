package com.launchmate.domain;

import com.launchmate.auth.CurrentUser;
import com.launchmate.domain.DomainDtos.AnalyseRequest;
import com.launchmate.domain.DomainDtos.AnalyseResponse;
import com.launchmate.domain.DomainDtos.DomainResult;
import com.launchmate.domain.DomainFitDtos.FitRequest;
import com.launchmate.domain.DomainFitDtos.FitResponse;
import com.launchmate.domain.DomainDtos.SuggestRequest;
import com.launchmate.domain.DomainDtos.SuggestResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}")
public class DomainController {

    private final DomainService domains;
    private final DomainFitService fit;

    public DomainController(DomainService domains, DomainFitService fit) {
        this.domains = domains;
        this.fit = fit;
    }

    @PostMapping("/domain-analyses")
    public AnalyseResponse analyse(@PathVariable UUID profileId, @Valid @RequestBody AnalyseRequest request) {
        return domains.analyse(CurrentUser.id(), profileId, request);
    }

    @PostMapping("/domain-suggestions")
    public SuggestResponse suggest(@PathVariable UUID profileId, @Valid @RequestBody SuggestRequest request) {
        return domains.suggest(CurrentUser.id(), profileId, request);
    }

    @PostMapping("/domain-fit")
    public FitResponse fit(@PathVariable UUID profileId, @Valid @RequestBody FitRequest request) {
        return fit.assess(CurrentUser.id(), profileId, request);
    }

    @GetMapping("/domains")
    public List<DomainResult> list(@PathVariable UUID profileId) {
        return domains.list(CurrentUser.id(), profileId);
    }
}
