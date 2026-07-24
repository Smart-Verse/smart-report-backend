package com.smartverse.smartreportbackend.config.security.service;

import com.smartverse.smartreportbackend_gen.authorization.exception.ServiceException;
import com.smartverse.smartreportbackend_gen.authorization.security.Authenticate;
import com.smartverse.smartreportbackend_gen.authorization.security.UserSupplier;
import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;

import com.smartverse.smartreportbackend.config.security.model.RegisterDTO;
import com.smartverse.smartreportbackend.config.security.model.UsersDTO;
import com.smartverse.smartreportbackend.config.security.model.UsersEntity;
import com.smartverse.smartreportbackend.config.security.repository.AuthenticationRepository;
import com.smartverse.smartreportbackend.services.email.EmailService;

import com.smartverse.smartreportbackend_gen.entities.UserConfirmationEntity;
import com.smartverse.smartreportbackend.repository.userconfirmation.UserConfirmationCustomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Service
public class AuthenticationService {

    @Value("${app.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:4200}}")
    private String frontendBaseUrl;

    @Autowired
    AuthenticationRepository authenticationRepository;

    @Autowired
    UserConfirmationCustomRepository userConfirmationRepository;

    @Autowired
    Authenticate authenticate;

    @Autowired
    EmailService emailService;

    public String login(UsersDTO userSupplierDTO){
        TenantContext.setCurrentTenant("admin");
        var user = authenticationRepository.findOneByEmail(userSupplierDTO.email())
                .filter(found -> new BCryptPasswordEncoder()
                        .matches(userSupplierDTO.password(), found.getPassword()))
                .orElseThrow(() -> new ServiceException(
                        HttpStatus.UNAUTHORIZED, "User or password invalid"));

        if (!user.isUserConfirm() || !user.isActive()) {
            throw new ServiceException(
                    HttpStatus.FORBIDDEN, "Confirme sua conta antes de fazer login");
        }

        return authenticate.generateToken(setUserSupplier(user));
    }

    public UserSupplier validateToken(String token){
        token = token.replace("Bearer ","");
        return authenticate.isAuthenticated(token);
    }

    private UserSupplier setUserSupplier(UsersEntity userSupplier){
        var usersup =  UserSupplier.builder().build();
        usersup.setId(userSupplier.getId());
        usersup.setName(userSupplier.getName());
        usersup.setTenant(userSupplier.getTenant());
        usersup.setEmail(userSupplier.getEmail());
        usersup.setRoles(Collections.emptyList());
        return usersup;
    }

    @Transactional
    public boolean onRegisterUser(RegisterDTO register){

        if(register.name().isEmpty() || register.password().isEmpty() || register.email().isEmpty()){
            throw new ServiceException(HttpStatus.BAD_REQUEST,"Campos com dados inválidos");
        }

        var existingUser = authenticationRepository.findOneByEmail(register.email());
        if (existingUser.isPresent()) {
            var user = existingUser.get();
            if (!user.isUserConfirm() && !user.isActive()) {
                throw new ServiceException(HttpStatus.CONFLICT, "ACCOUNT_CONFIRMATION_PENDING");
            }
            throw new ServiceException(HttpStatus.FORBIDDEN, "Ja existe um email cadastrado!");
        }

        var count = authenticationRepository.countAllBy();
        var user = new UsersEntity();
        user.setName(register.name());
        user.setEmail(register.email());
        var pass = new BCryptPasswordEncoder().encode(register.password());
        user.setPassword(pass);
        user.setUserConfirm(false);
        user.setActive(false);
        user.setTenant(String.format("SMARTVARSE_%s",count));

        user = authenticationRepository.save(user);


        var userConfirmation = new UserConfirmationEntity();

        userConfirmation.setUserId(user.getId());
        userConfirmation.setHash(UUID.randomUUID().toString());
        userConfirmation = userConfirmationRepository.save(userConfirmation);

        sendConfirmationEmail(user.getEmail(), userConfirmation.getHash());

        return true;
    }


    @Transactional
    public boolean resendConfirmation(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }

        authenticationRepository.findOneByEmail(email).ifPresent(user -> {
            if (user.isUserConfirm() || user.isActive()) {
                return;
            }

            var confirmation = userConfirmationRepository.findByUserId(user.getId())
                    .orElseGet(UserConfirmationEntity::new);
            confirmation.setUserId(user.getId());
            confirmation.setHash(UUID.randomUUID().toString());
            confirmation = userConfirmationRepository.save(confirmation);
            sendConfirmationEmail(user.getEmail(), confirmation.getHash());
        });
        return true;
    }

    private void sendConfirmationEmail(String email, String token) {
        var confirmationUrl = String.format(
                "%s/user-confirmation/%s",
                frontendBaseUrl.replaceAll("/+$", ""),
                token);
        var emailContent = emailService.loadModel("register")
                .replace("{{url}}", confirmationUrl);
        emailService.sendEmail(email, "Confirmação de email", emailContent, token);
    }
}
