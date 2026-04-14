package com.lms.auth.repository;

import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT u FROM User u")
    Page<User> findPage(Pageable pageable);

    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT u FROM User u JOIN u.role r WHERE r.name = :role")
    Page<User> findPageByRole(@Param("role") RoleName role, Pageable pageable);

    @Query("SELECT u FROM User u JOIN FETCH u.role r WHERE LOWER(u.email) = LOWER(:email) AND r.name = :role")
    Optional<User> findByEmailIgnoreCaseAndRole(@Param("email") String email, @Param("role") RoleName role);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") Long id);
}
