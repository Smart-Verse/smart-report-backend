package com.smartverse.smartreportbackend.handlers.userconfirmation;

import com.smartverse.smartreportbackend_gen.authorization.stereotype.Anonymous;
import com.smartverse.smartreportbackend.config.migration.DBMigration;
import com.smartverse.smartreportbackend.config.security.repository.AuthenticationRepository;
import com.smartverse.smartreportbackend.config.security.service.AuthenticationService;
import com.smartverse.smartreportbackend.repository.userconfirmation.UserConfirmationCustomRepository;
import com.smartverse.smartreportbackend_gen.endpoints.ResendConfirmation;
import com.smartverse.smartreportbackend_gen.endpoints.ResendConfirmationInput;
import com.smartverse.smartreportbackend_gen.endpoints.ResendConfirmationOutput;
import com.smartverse.smartreportbackend_gen.endpoints.VerifyURL;
import com.smartverse.smartreportbackend_gen.endpoints.VerifyURLOutput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins="*")
@RestController
public class UserConfirmation implements VerifyURL, ResendConfirmation {

    @Autowired
    UserConfirmationCustomRepository userConfirmationRepository;

    @Autowired
    AuthenticationRepository authenticationRepository;

    @Autowired
    DBMigration dbMigration;

    @Autowired
    AuthenticationService authenticationService;

    @Anonymous
    @Override
    public ResponseEntity<VerifyURLOutput> verifyURL(String token) {

        var userConfirmation = userConfirmationRepository.findByHash(token).orElse(null);
        var output = new VerifyURLOutput();
        output.authorize = false;
        if(userConfirmation != null) {
            output.authorize = true;

            var user = authenticationRepository.findById(userConfirmation.getUserId()).orElse(null);
            if(user != null) {
                user.setUserConfirm(true);
                user.setActive(true);
                authenticationRepository.save(user);
                dbMigration.loadMigrateTenants(user.getTenant());
            }
        }

        return new ResponseEntity<>(output, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ResendConfirmationOutput> resendConfirmation(ResendConfirmationInput input) {
        var output = new ResendConfirmationOutput();
        output.accepted = authenticationService.resendConfirmation(input.email);
        return ResponseEntity.ok(output);
    }
}
