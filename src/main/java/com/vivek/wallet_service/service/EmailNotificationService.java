package com.vivek.wallet_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.vivek.wallet_service.dto.TransferEvent;

@Service
public class EmailNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String fromAddress;

    public EmailNotificationService(
            JavaMailSender mailSender,
            @Value("${app.email.enabled:false}") boolean emailEnabled,
            @Value("${app.email.from:no-reply@wallet-service.local}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.fromAddress = fromAddress;
    }

    public void sendTransferEmails(TransferEvent event) {
        if (!emailEnabled) {
            LOGGER.info(
                    "Email disabled. Transfer notification skipped for sender={} receiver={} amount={}",
                    event.getFromEmail(),
                    event.getToEmail(),
                    event.getAmount()
            );
            return;
        }

        sendEmail(
                event.getFromEmail(),
                "Wallet transfer sent",
                "You sent " + event.getAmount() + " to " + event.getToEmail() + "."
        );

        sendEmail(
                event.getToEmail(),
                "Wallet transfer received",
                "You received " + event.getAmount() + " from " + event.getFromEmail() + "."
        );
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}
