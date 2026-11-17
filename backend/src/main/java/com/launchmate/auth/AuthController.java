package com.launchmate.auth;

import com.launchmate.auth.AuthService.AuthResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 160) String displayName) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {}

    public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetRequest(@NotBlank @Size(max = 200) String token,
                               @NotBlank @Size(min = 10, max = 128) String password) {}

    public record Message(String message) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        return auth.register(r.email(), r.password(), r.displayName());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    /** Always answers the same way, whether or not the email has an account. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Message forgot(@Valid @RequestBody ForgotRequest r) {
        auth.requestPasswordReset(r.email());
        return new Message("If an account exists for that email, we've sent a link to reset the password.");
    }

    @PostMapping("/reset-password")
    public Message reset(@Valid @RequestBody ResetRequest r) {
        auth.resetPassword(r.token(), r.password());
        return new Message("Your password has been changed. You can sign in with it now.");
    }
}
