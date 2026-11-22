package com.fitflex.repository;

import com.fitflex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Database access for {@link User}. Spring Data writes the SQL from the method names. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
