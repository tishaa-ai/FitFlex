package com.fitflex.repository;

import com.fitflex.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for Contact Us messages. */
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
}
