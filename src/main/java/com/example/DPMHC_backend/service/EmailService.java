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

    public void sendWarningEmail(String toEmail, String username, String reason, String warningMessage, boolean isFinalWarning) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            
            String subject = isFinalWarning ? "⚠️ FINAL WARNING - Content Policy Violation" : "⚠️ WARNING - Content Policy Violation";
            helper.setSubject(subject);
            
            String emailContent = buildWarningEmailContent(username, reason, warningMessage, isFinalWarning);
            helper.setText(emailContent, true);
            
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send warning email", e);
        }
    }
    
    public void sendBanNotificationEmail(String toEmail, String username, String reason) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🚫 Account Suspended - Content Policy Violation");
            
            String emailContent = buildBanEmailContent(username, reason);
            helper.setText(emailContent, true);
            
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send ban notification email", e);
        }
    }
    
    private String buildWarningEmailContent(String username, String reason, String warningMessage, boolean isFinalWarning) {
        return "<html><body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                "<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>" +
                "<h2 style='color: " + (isFinalWarning ? "#dc3545" : "#fd7e14") + "; border-bottom: 2px solid " + (isFinalWarning ? "#dc3545" : "#fd7e14") + "; padding-bottom: 10px;'>" +
                (isFinalWarning ? "🚨 FINAL WARNING" : "⚠️ CONTENT WARNING") + "</h2>" +
                
                "<p>Dear <strong>" + username + "</strong>,</p>" +
                
                "<p>We are writing to inform you about a content policy violation on our platform.</p>" +
                
                "<div style='background-color: #f8f9fa; border-left: 4px solid " + (isFinalWarning ? "#dc3545" : "#fd7e14") + "; padding: 15px; margin: 20px 0;'>" +
                "<h3 style='margin-top: 0; color: " + (isFinalWarning ? "#dc3545" : "#fd7e14") + ";'>Reason for Warning:</h3>" +
                "<p style='margin: 10px 0;'><strong>" + reason + "</strong></p>" +
                (warningMessage != null && !warningMessage.trim().isEmpty() ? 
                    "<p style='margin: 10px 0;'>" + warningMessage + "</p>" : "") +
                "</div>" +
                
                (isFinalWarning ? 
                    "<div style='background-color: #f8d7da; border: 1px solid #f5c6cb; border-radius: 5px; padding: 15px; margin: 20px 0;'>" +
                    "<h3 style='color: #721c24; margin-top: 0;'>⚠️ This is your FINAL WARNING</h3>" +
                    "<p style='color: #721c24; margin-bottom: 0;'>Any future violations will result in permanent account suspension.</p>" +
                    "</div>" : 
                    "<div style='background-color: #fff3cd; border: 1px solid #ffeeba; border-radius: 5px; padding: 15px; margin: 20px 0;'>" +
                    "<p style='color: #856404; margin-bottom: 0;'>Please ensure future posts comply with our community guidelines. Repeated violations may result in account suspension.</p>" +
                    "</div>"
                ) +
                
                "<h3>Community Guidelines:</h3>" +
                "<ul>" +
                "<li>Post content relevant to professional development and career growth</li>" +
                "<li>Be respectful and professional in all interactions</li>" +
                "<li>No spam, promotional content, or misleading information</li>" +
                "<li>Respect intellectual property and privacy rights</li>" +
                "</ul>" +
                
                "<p>If you believe this warning was issued in error, please contact our support team.</p>" +
                
                "<div style='margin-top: 30px; padding-top: 20px; border-top: 1px solid #dee2e6;'>" +
                "<p style='color: #6c757d; font-size: 14px; margin: 0;'>Best regards,<br>The Moderation Team</p>" +
                "</div>" +
                
                "</div></body></html>";
    }
    
    private String buildBanEmailContent(String username, String reason) {
        return "<html><body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
                "<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>" +
                "<h2 style='color: #dc3545; border-bottom: 2px solid #dc3545; padding-bottom: 10px;'>🚫 ACCOUNT SUSPENDED</h2>" +
                
                "<p>Dear <strong>" + username + "</strong>,</p>" +
                
                "<p>Your account has been <strong>permanently suspended</strong> due to repeated content policy violations.</p>" +
                
                "<div style='background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 15px; margin: 20px 0;'>" +
                "<h3 style='margin-top: 0; color: #dc3545;'>Final Violation:</h3>" +
                "<p style='margin-bottom: 0;'><strong>" + reason + "</strong></p>" +
                "</div>" +
                
                "<div style='background-color: #f8f9fa; border: 1px solid #dee2e6; border-radius: 5px; padding: 15px; margin: 20px 0;'>" +
                "<h3 style='margin-top: 0;'>What this means:</h3>" +
                "<ul style='margin-bottom: 0;'>" +
                "<li>You can no longer access your account</li>" +
                "<li>All your posts and data remain on the platform but are inaccessible to you</li>" +
                "<li>You cannot create new accounts</li>" +
                "</ul>" +
                "</div>" +
                
                "<p>This decision was made after you received a warning and continued to violate our community guidelines.</p>" +
                
                "<p>If you believe this suspension was issued in error, you may appeal by contacting our support team within 30 days.</p>" +
                
                "<div style='margin-top: 30px; padding-top: 20px; border-top: 1px solid #dee2e6;'>" +
                "<p style='color: #6c757d; font-size: 14px; margin: 0;'>The Moderation Team</p>" +
                "</div>" +
                
                "</div></body></html>";
    }
}
