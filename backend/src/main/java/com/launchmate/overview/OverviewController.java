package com.launchmate.overview;

import com.launchmate.auth.CurrentUser;
import com.launchmate.overview.OverviewService.Overview;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OverviewController {

    private final OverviewService overview;

    public OverviewController(OverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/profiles/{profileId}/overview")
    public Overview get(@PathVariable UUID profileId) {
        return overview.get(CurrentUser.id(), profileId);
    }
}
