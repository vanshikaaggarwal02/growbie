package com.launchmate.contact;

import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/contact")
public class ContactController {

    public record ContactRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 160) String business,
            @NotBlank @Size(min = 10, max = 5000) String message,
            /** Hidden field that people leave empty. */
            @Size(max = 200) String website) {}

    public record Message(String message) {}

    private final ContactService contact;

    public ContactController(ContactService contact) {
        this.contact = contact;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Message send(@Valid @RequestBody ContactRequest r, HttpServletRequest http) {
        contact.submit(http.getRemoteAddr(), r.name(), r.email(), r.business(), r.message(), r.website());
        return new Message("Thanks. Your message is with us and we'll reply by email.");
    }
}
