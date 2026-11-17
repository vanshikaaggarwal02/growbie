package com.launchmate.profile;

import com.launchmate.auth.CurrentUser;
import com.launchmate.profile.ProfileDtos.CreateProfileRequest;
import com.launchmate.profile.ProfileDtos.ProfileResponse;
import com.launchmate.profile.ProfileDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse create(@Valid @RequestBody CreateProfileRequest request) {
        return profiles.create(CurrentUser.id(), request);
    }

    @GetMapping
    public List<ProfileResponse> list() {
        return profiles.list(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public ProfileResponse get(@PathVariable UUID id) {
        return profiles.get(CurrentUser.id(), id);
    }

    @PutMapping("/{id}")
    public ProfileResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(CurrentUser.id(), id, request);
    }
}
