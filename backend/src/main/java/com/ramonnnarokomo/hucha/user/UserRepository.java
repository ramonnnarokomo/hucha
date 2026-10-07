package com.ramonnnarokomo.hucha.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    @Query("""
            select u from User u
            where lower(u.email) like :pattern or lower(u.fullName) like :pattern
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);
}
