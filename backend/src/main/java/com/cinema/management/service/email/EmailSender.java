package com.cinema.management.service.email;

import java.util.concurrent.CompletableFuture;

public interface EmailSender {
    CompletableFuture<Void> sendHtmlEmail(String recipient, String subject, String htmlBody);
}

