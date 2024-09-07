package com.vtesdecks.model.mail;

import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class MailNotificationData.
 */
@Data
@Slf4j
public class MailNotificationData {

    private List<String> to;
    private String subject;
    private String body;
    private String fileName;
    private ByteArrayResource byteArrayResource;
    private String encoding;
    private String from;

    /**
     * Generate mime message preparator.
     *
     * @return the mime message preparator
     */
    public MimeMessagePreparator generateMimeMessagePreparator() {
        final MailNotificationData mailData = this;
        return new MimeMessagePreparator() {
            @Override
            public void prepare(final MimeMessage mimeMessage) throws MessagingException {
                final MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, mailData.getEncoding());
                message.setFrom(mailData.getFrom());
                message.setTo(mailData.getTo().toArray(new String[0]));
                message.setSubject(mailData.getSubject());
                message.setText(mailData.getBody(), true);

                if (mailData.getFileName() != null) {
                    message.addAttachment(mailData.getFileName(), mailData.getByteArrayResource());
                }
            }
        };
    }

}
