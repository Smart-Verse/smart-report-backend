package com.smartverse.smartreportbackend.repository.userconfirmation;


import com.smartverse.smartreportbackend_gen.UserConfirmationEntity;
import com.smartverse.smartreportbackend_gen.UserConfirmationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserConfirmationCustomRepository extends UserConfirmationRepository {
    Optional<UserConfirmationEntity> findByHash(String hash);
}
