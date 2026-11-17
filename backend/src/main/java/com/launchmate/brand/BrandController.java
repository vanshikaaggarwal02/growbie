package com.launchmate.brand;

import com.launchmate.auth.CurrentUser;
import com.launchmate.brand.BrandDtos.BrandSuggestionRequest;
import com.launchmate.brand.BrandDtos.BrandSuggestionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}")
public class BrandController {

    private final BrandSuggestionService brands;

    public BrandController(BrandSuggestionService brands) {
        this.brands = brands;
    }

    @PostMapping("/brand-suggestions")
    public BrandSuggestionResponse suggest(@PathVariable UUID profileId,
                                           @Valid @RequestBody BrandSuggestionRequest request) {
        return brands.generate(CurrentUser.id(), profileId, request);
    }
}
