package com.launchmate.channel;

import com.launchmate.auth.CurrentUser;
import com.launchmate.channel.ChannelAnalysisDtos.ChannelAnalysisView;
import com.launchmate.channel.ChannelAnalysisDtos.SnapshotItem;
import com.launchmate.channel.ChannelAnalysisDtos.StartAnalysisRequest;
import com.launchmate.channel.ChannelAnalysisDtos.StartAnalysisResponse;
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
public class ChannelAnalysisController {

    private final ChannelAnalysisService analyses;

    public ChannelAnalysisController(ChannelAnalysisService analyses) {
        this.analyses = analyses;
    }

    /** Returns at once; poll the analysis until its status is COMPLETED, PARTIAL or FAILED. */
    @PostMapping("/profiles/{profileId}/channel-analyses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartAnalysisResponse start(@PathVariable UUID profileId, @Valid @RequestBody StartAnalysisRequest request) {
        return analyses.start(CurrentUser.id(), profileId, request);
    }

    /** Measured snapshots over time, newest first. */
    @GetMapping("/profiles/{profileId}/channel-analyses")
    public List<SnapshotItem> snapshots(@PathVariable UUID profileId) {
        return analyses.list(CurrentUser.id(), profileId);
    }

    @GetMapping("/channel-analyses/{analysisId}")
    public ChannelAnalysisView get(@PathVariable UUID analysisId) {
        return analyses.get(CurrentUser.id(), analysisId);
    }
}
