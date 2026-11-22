package com.fitflex.dto.response;

/** Simple success reply, e.g. {"message": "Thanks! We will get back to you soon."}. */
public record MessageResponse(String message) {
}
