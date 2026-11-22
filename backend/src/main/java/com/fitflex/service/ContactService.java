package com.fitflex.service;

import com.fitflex.dto.request.ContactRequest;
import com.fitflex.dto.response.MessageResponse;
import com.fitflex.entity.ContactMessage;
import com.fitflex.repository.ContactMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores messages from the public Contact Us form. */
@Service
public class ContactService {

    private final ContactMessageRepository contactRepository;

    public ContactService(ContactMessageRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Transactional
    public MessageResponse submit(ContactRequest request) {
        ContactMessage message = new ContactMessage();
        message.setName(request.name().trim());
        message.setEmail(request.email().trim());
        message.setSubject(request.subject());
        message.setMessage(request.message().trim());
        contactRepository.save(message);
        return new MessageResponse("Thanks " + message.getName() + "! We will get back to you soon.");
    }
}
