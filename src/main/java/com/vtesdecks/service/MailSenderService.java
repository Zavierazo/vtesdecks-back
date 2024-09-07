package com.vtesdecks.service;


import com.vtesdecks.model.Mail;

/**
 * The interface Mail sender service.
 */
public interface MailSenderService {
    /**
     * Send mail.
     *
     * @param mail the mail
     */
    void sendMail(Mail mail);
}
