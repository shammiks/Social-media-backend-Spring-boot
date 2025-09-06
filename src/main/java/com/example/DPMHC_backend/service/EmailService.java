package com.example.DPMHC_backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendVerificationEmail(String to, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Verify your email");
            helper.setText(
                    "<p>Please click the link below to verify your email:</p>" +
                            "<a href=\"" + verificationLink + "\">Verify Email</a>", true
            );
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Password Reset Request");
        message.setText("You requested to reset your password. Click the link below to proceed:\n\n" +
                resetLink + "\n\nIf you didn't request this, please ignore this email.");
        mailSender.send(message);
    }

    public void sendPasswordResetCodeEmail(String toEmail, String resetCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Password Reset Code");
        message.setText("You requested to reset your password. Use the following 5-digit code to proceed:\n\n" +
                "Reset Code: " + resetCode + "\n\n" +
                "This code will expire in 15 minutes.\n\n" +
                "If you didn't request this, please ignore this email.");
        mailSender.send(message);
    }
}
