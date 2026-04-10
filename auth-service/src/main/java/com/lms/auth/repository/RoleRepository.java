package com.lms.auth.repository;

import com.lms.auth.entity.Role;
import com.lms.auth.entity.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
