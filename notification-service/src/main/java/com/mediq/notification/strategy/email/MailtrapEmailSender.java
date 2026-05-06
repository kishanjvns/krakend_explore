package com.mediq.notification.strategy.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component("mailtrapEmailSender")
public class MailtrapEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(MailtrapEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String fromName;

    public MailtrapEmailSender(
            JavaMailSender mailSender,
            @Value("${mediq.notification.from-email}") String fromEmail,
            @Value("${mediq.notification.from-name}") String fromName) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void sendEmail(String to, String subject, String body, boolean isHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, isHtml);
            mailSender.send(message);
            log.info("Email sent via Mailtrap → to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Mailtrap email failed → to={} error={}", to, e.getMessage());
        }
    }

    @Override
    public String strategyName() {
        return "MAILTRAP_EMAIL_SENDER";
    }
}
