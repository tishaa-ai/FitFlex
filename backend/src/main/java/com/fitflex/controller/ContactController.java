package com.fitflex.controller;

import com.fitflex.dto.request.ContactRequest;
import com.fitflex.dto.response.MessageResponse;
import com.fitflex.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** PUBLIC endpoint for the Contact Us form. */
@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse submit(@Valid @RequestBody ContactRequest request) {
        return contactService.submit(request);
    }
}
