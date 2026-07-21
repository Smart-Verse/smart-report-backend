package com.smartverse.smartreportbackend.repository.userconfirmation;


import com.smartverse.smartreportbackend_gen.entities.UserConfirmationEntity;
import com.smartverse.smartreportbackend_gen.repositories.UserConfirmationRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Primary
@Repository
public interface UserConfirmationCustomRepository extends UserConfirmationRepository {
    Optional<UserConfirmationEntity> findByHash(String hash);
}
