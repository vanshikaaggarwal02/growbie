package com.launchmate.auth;

import com.launchmate.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in user's id, taken from the verified token, never from the request body. */
public final class CurrentUser {

    private CurrentUser() {}

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in.");
        }
        return UUID.fromString(auth.getName());
    }
}
