package com.vtesdecks.service.email;

import com.vtesdecks.model.Mail;
import com.vtesdecks.model.mail.MailNotificationData;
import com.vtesdecks.service.MailSenderService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Properties;

@Slf4j
@Service
@ConditionalOnProperty(value = "smtp.aws.enabled", havingValue = "true", matchIfMissing = false)
public class AwsSimpleEmailSenderService implements MailSenderService {
    @Value("${smtp.aws.host}")
    private String host;
    @Value("${smtp.aws.username}")
    private String username;
    @Value("${smtp.aws.password}")
    private String password;
    private JavaMailSenderImpl javaMailSender;

    @PostConstruct
    public void setUp() {
        javaMailSender = new JavaMailSenderImpl();
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2");
        properties.setProperty("mail.smtp.quitwait", "false");
        javaMailSender.setJavaMailProperties(properties);
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
