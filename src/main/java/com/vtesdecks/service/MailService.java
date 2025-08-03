package com.vtesdecks.service;

import com.vtesdecks.model.Mail;
import com.vtesdecks.service.email.AwsSimpleEmailSenderService;
import com.vtesdecks.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class MailService {
    @Autowired(required = false)
    private AwsSimpleEmailSenderService awsSimpleEmailSenderService;

    public void sendConfirmationMail(String email, String token) {
        String verifyEndpoint = "https://vtesdecks.com/verify?token=" + token;
        Mail mail = Mail.builder()
                .from("no-reply@vtesdecks.com")
                .to(email)
                .subject("VTES Decks - Confirm registration")
                .contentType("text/html")
                .content(Utils.readFile(this.getClass().getClassLoader(), "email/email_verify.html")
                        .replaceAll("\\{\\{CONFIRM_EMAIL\\}\\}", verifyEndpoint))
                .build();
        sendMail(mail);
    }

    public void sendForgotPasswordMail(String email, String token) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String passwordResetUrl = "https://vtesdecks.com/reset-password?email=" + encodedEmail + "&token=" + token;
        Mail mail = Mail.builder()
                .from("no-reply@vtesdecks.com")
                .to(email)
                .subject("VTES Decks - Password Reset")
                .contentType("text/html")
                .content(Utils.readFile(this.getClass().getClassLoader(), "email/email_forgot_password.html")
                        .replaceAll("\\{\\{FORGOT_PASSWORD}}", passwordResetUrl))
                .build();
        sendMail(mail);
    }

    public void sendContactMail(String username, String email, String subject, String message) {
        Mail mail = Mail.builder()
                .from("no-reply@vtesdecks.com")
                .to("support@vtesdecks.com")
                .subject("VTES Decks - Contact - " + username + " - " + email + " - " + subject)
                .contentType("text/plain")
                .content(message)
                .build();
        sendMail(mail);
    }

    private void sendMail(Mail mail) {
        if (awsSimpleEmailSenderService != null) {
            awsSimpleEmailSenderService.sendMail(mail);
        } else {
            log.warn("Local email {}", mail);
        }
    }
}
