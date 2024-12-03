package com.smartverse.smartreportbackend.config.security.repository;


import com.smartverse.smartreportbackend.config.security.model.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthenticationRepository extends JpaRepository<UsersEntity, UUID>  {

    Optional<UsersEntity> findOneByEmail(String email);

    boolean existsByEmail(String email);

    int countAllBy();
}
