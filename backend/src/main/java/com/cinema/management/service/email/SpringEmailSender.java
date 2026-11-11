package com.cinema.management.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
public class SpringEmailSender implements EmailSender {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public SpringEmailSender(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public CompletableFuture<Void> sendHtmlEmail(
            String recipient, String subject, String htmlBody) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Email is not configured. Set spring.mail.host and SMTP credentials."));
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            return CompletableFuture.completedFuture(null);
        } catch (MessagingException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
