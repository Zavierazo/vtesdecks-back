package com.vtesdecks.service.email;

import com.vtesdecks.model.Mail;
import com.vtesdecks.model.mail.MailNotificationData;
import com.vtesdecks.service.MailSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;

@Slf4j
@Service
public class LocalSmtpSenderService implements MailSenderService {

    @Value("${smtp.local.host}")
    private String host;
    @Value("${smtp.local.username}")
    private String username;
    @Value("${smtp.local.password}")
    private String password;
    private JavaMailSenderImpl javaMailSender;

    @PostConstruct
    public void setUp() {
        javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(host);
        javaMailSender.setUsername(username);
        javaMailSender.setPassword(password);
    }

    @Override
    public void sendMail(Mail mail) {
        MailNotificationData notificationData = new MailNotificationData();
        notificationData.setFrom(mail.getFrom());
        notificationData.setTo(Arrays.asList(mail.getTo()));
        notificationData.setSubject(mail.getSubject());
        notificationData.setBody(mail.getContent());
        notificationData.setEncoding("utf-8");
        try {
            log.debug("Mail send! {}", mail);
            javaMailSender.send(notificationData.generateMimeMessagePreparator());
        } catch (Exception e) {
            log.error("Unable to send email {}", mail, e);
        }
    }
}
