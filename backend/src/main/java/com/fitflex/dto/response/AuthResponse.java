package com.fitflex.dto.response;

import com.fitflex.entity.Role;

/**
 * Returned by every register/login endpoint. The frontend stores {@code token} in localStorage
 * and sends it as "Authorization: Bearer &lt;token&gt;" on later requests.
 *
 * @param id        the User id
 * @param trainerId the Trainer profile id (only for trainers, otherwise null)
 */
public record AuthResponse(String token, Role role, String name, Long id, Long trainerId, String email) {
}
