package com.smartverse.smartreportbackend.services.email;

import com.potatotech.authorization.exception.ServiceException;

import com.smartverse.smartreportbackend.common.FileCommon;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String model) throws jakarta.mail.MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(model, true);
        helper.setFrom("geovane.araujo@dataon.com.br");

        mailSender.send(mimeMessage);
    }


    public void loadAndSendEmail(String to, String subject, String modelName)  {
        try{
            var emailModel = loadModel(modelName);
            sendEmail(to,subject,emailModel);
        }catch ( MessagingException | jakarta.mail.MessagingException e){
            throw new ServiceException(HttpStatus.BAD_REQUEST,e.getMessage());
        }
    }


    public String loadModel(String modelName){
        return FileCommon.loadMod(modelName);
    }

}
