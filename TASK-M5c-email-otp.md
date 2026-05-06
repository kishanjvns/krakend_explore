# mediq — Task M5c: OTP + Email + SMS Strategy Pattern

## Branch
```powershell
git checkout main
git pull origin main
git checkout -b feature/mediq-m5c-email-otp
```

## What This Task Builds

```
STRATEGY PATTERN — notification-service:
  OtpSender interface   → StaticOtpSender (logs OTP)
  EmailSender interface → MailtrapEmailSender (real SMTP to Mailtrap)
  SmsSender interface   → StaticSmsSender (logs message)
  Each swappable via config — zero service layer change for future providers

OTP FLOW — user-service:
  POST /users/{userId}/send-otp    → publishes OtpRequestedEvent per contact
  POST /users/{userId}/verify-otp → Redis validation, Option B (5 wrong = invalidate)

NOTIFICATION TRIGGERS — notification-service:
  Trigger 1: OtpRequestedEvent → email (Mailtrap) + phone (log)
  Trigger 2: PaymentSucceeded  → confirmation email + SMS log
  Trigger 2: PaymentFailed     → retry link email + SMS log (Stripe PaymentLink)
  Trigger 3: AppointmentCancelled/Rescheduled → email + SMS to patient AND doctor

24HR AUTO-CANCEL — appointment-service:
  Scheduler runs every hour
  Finds PAYMENT_FAILED appointments older than 24hrs
  Auto-cancels → publishes AppointmentCancelled
```

## Services Modified

```
notification-service  → strategy pattern + all consumers
user-service          → send-otp + verify-otp endpoints
appointment-service   → 24hr auto-cancel scheduler
```

---

# PART 1: Strategy Pattern in notification-service

## Step 1: Add JavaMail dependency to notification-service pom.xml

```xml
<!-- JavaMail for SMTP sending -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- Thymeleaf for email templates -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

## Step 2: Add Mailtrap config to notification-service application.properties

```properties
# ── Email (Mailtrap SMTP sandbox) ─────────────────────────────────────────────
spring.mail.host=${MAIL_HOST:sandbox.smtp.mailtrap.io}
spring.mail.port=${MAIL_PORT:2525}
spring.mail.username=${MAIL_USERNAME:c08b98ac63c238}
spring.mail.password=${MAIL_PASSWORD:dac5bf469f22ae}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=false
spring.mail.default-encoding=UTF-8

# ── Notification Strategy ─────────────────────────────────────────────────────
mediq.notification.otp-strategy=static        # static | msg91 | twilio
mediq.notification.email-strategy=mailtrap    # mailtrap | ses | sendgrid
mediq.notification.sms-strategy=static        # static | msg91 | twilio
mediq.notification.from-email=noreply@mediq.com
mediq.notification.from-name=mediq Healthcare
```

## Step 3: OtpSender — Interface + Implementation

Create `notification-service/src/main/java/com/mediq/notification/strategy/otp/OtpSender.java`:

```java
package com.mediq.notification.strategy.otp;

public interface OtpSender {

    /**
     * Send OTP to destination (phone number).
     * @param userId    the user this OTP belongs to
     * @param phone     destination phone number
     * @param otp       the 6-digit OTP to send
     * @param expiresIn minutes until OTP expires
     */
    void sendOtp(String userId, String phone, String otp, int expiresIn);

    /**
     * Strategy identifier — used for logging and monitoring
     */
    String strategyName();
}
```

Create `notification-service/src/main/java/com/mediq/notification/strategy/otp/StaticOtpSender.java`:

```java
package com.mediq.notification.strategy.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("staticOtpSender")
public class StaticOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(StaticOtpSender.class);

    @Override
    public void sendOtp(String userId, String phone,
                        String otp, int expiresIn) {
        // In production: replace with real SMS API call
        // For now: log so developer can copy OTP from logs
        log.info("╔══════════════════════════════════════════╗");
        log.info("║           mediq OTP NOTIFICATION         ║");
        log.info("║  userId   : {}                           ", userId);
        log.info("║  phone    : {}                           ", phone);
        log.info("║  OTP      : {}                           ", otp);
        log.info("║  expires  : {} minutes                   ", expiresIn);
        log.info("╚══════════════════════════════════════════╝");
    }

    @Override
    public String strategyName() {
        return "STATIC_OTP_SENDER";
    }
}
```

**Future concrete class — add when ready (zero change to service layer):**

```java
// placeholder — implement when Msg91 account available
@Component("msg91OtpSender")
public class Msg91OtpSender implements OtpSender {
    // real API call to Msg91
    @Override
    public void sendOtp(String userId, String phone,
                        String otp, int expiresIn) {
        // POST https://api.msg91.com/api/v5/otp
    }
    @Override public String strategyName() { return "MSG91_OTP_SENDER"; }
}
```

## Step 4: EmailSender — Interface + Implementation

Create `notification-service/src/main/java/com/mediq/notification/strategy/email/EmailSender.java`:

```java
package com.mediq.notification.strategy.email;

public interface EmailSender {

    /**
     * Send an email.
     * @param to          recipient email address
     * @param subject     email subject
     * @param body        HTML or plain text body
     * @param isHtml      true for HTML email, false for plain text
     */
    void sendEmail(String to, String subject, String body, boolean isHtml);

    String strategyName();
}
```

Create `notification-service/src/main/java/com/mediq/notification/strategy/email/MailtrapEmailSender.java`:

```java
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
    public void sendEmail(String to, String subject,
                          String body, boolean isHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, isHtml);

            mailSender.send(message);
            log.info("Email sent via Mailtrap → to={} subject={}",
                to, subject);

        } catch (Exception e) {
            log.error("Mailtrap email failed → to={} error={}",
                to, e.getMessage());
            // Do not rethrow — email failure should not break business flow
        }
    }

    @Override
    public String strategyName() {
        return "MAILTRAP_EMAIL_SENDER";
    }
}
```

## Step 5: SmsSender — Interface + Implementation

Create `notification-service/src/main/java/com/mediq/notification/strategy/sms/SmsSender.java`:

```java
package com.mediq.notification.strategy.sms;

public interface SmsSender {

    /**
     * Send an SMS message.
     * @param phone    destination phone number (with country code)
     * @param message  SMS body (max 160 chars for single SMS)
     */
    void sendSms(String phone, String message);

    String strategyName();
}
```

Create `notification-service/src/main/java/com/mediq/notification/strategy/sms/StaticSmsSender.java`:

```java
package com.mediq.notification.strategy.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("staticSmsSender")
public class StaticSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(StaticSmsSender.class);

    @Override
    public void sendSms(String phone, String message) {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║           mediq SMS NOTIFICATION         ║");
        log.info("║  to      : {}                            ", phone);
        log.info("║  message : {}                            ", message);
        log.info("╚══════════════════════════════════════════╝");
    }

    @Override
    public String strategyName() {
        return "STATIC_SMS_SENDER";
    }
}
```

## Step 6: Strategy Factory — selects concrete class via config

Create `notification-service/src/main/java/com/mediq/notification/strategy/NotificationStrategyConfig.java`:

```java
package com.mediq.notification.strategy;

import com.mediq.notification.strategy.email.EmailSender;
import com.mediq.notification.strategy.email.MailtrapEmailSender;
import com.mediq.notification.strategy.otp.OtpSender;
import com.mediq.notification.strategy.otp.StaticOtpSender;
import com.mediq.notification.strategy.sms.SmsSender;
import com.mediq.notification.strategy.sms.StaticSmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class NotificationStrategyConfig {

    private static final Logger log =
        LoggerFactory.getLogger(NotificationStrategyConfig.class);

    @Bean
    @Primary
    public OtpSender otpSender(
            StaticOtpSender staticOtpSender,
            @Value("${mediq.notification.otp-strategy:static}") String strategy) {
        OtpSender selected = switch (strategy) {
            case "static" -> staticOtpSender;
            // case "msg91"  -> msg91OtpSender;   ← inject + add when ready
            // case "twilio" -> twilioOtpSender;
            default -> {
                log.warn("Unknown OTP strategy: {} — falling back to static", strategy);
                yield staticOtpSender;
            }
        };
        log.info("OTP strategy selected: {}", selected.strategyName());
        return selected;
    }

    @Bean
    @Primary
    public EmailSender emailSender(
            MailtrapEmailSender mailtrapEmailSender,
            @Value("${mediq.notification.email-strategy:mailtrap}") String strategy) {
        EmailSender selected = switch (strategy) {
            case "mailtrap" -> mailtrapEmailSender;
            // case "ses"      -> sesEmailSender;
            // case "sendgrid" -> sendgridEmailSender;
            default -> {
                log.warn("Unknown email strategy: {} — falling back to mailtrap", strategy);
                yield mailtrapEmailSender;
            }
        };
        log.info("Email strategy selected: {}", selected.strategyName());
        return selected;
    }

    @Bean
    @Primary
    public SmsSender smsSender(
            StaticSmsSender staticSmsSender,
            @Value("${mediq.notification.sms-strategy:static}") String strategy) {
        SmsSender selected = switch (strategy) {
            case "static" -> staticSmsSender;
            // case "msg91"  -> msg91SmsSender;
            default -> {
                log.warn("Unknown SMS strategy: {} — falling back to static", strategy);
                yield staticSmsSender;
            }
        };
        log.info("SMS strategy selected: {}", selected.strategyName());
        return selected;
    }
}
```

---

# PART 2: Email Templates

## Step 7: Create Thymeleaf email templates

Create `notification-service/src/main/resources/templates/email/`:

**`otp-email.html`** — OTP verification email:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>mediq — OTP Verification</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f4f4f4; margin: 0; }
        .container { max-width: 600px; margin: 40px auto; background: white;
                     border-radius: 8px; padding: 40px; }
        .header { background: #2563eb; color: white; padding: 20px;
                  border-radius: 8px 8px 0 0; text-align: center; }
        .otp-box { font-size: 36px; font-weight: bold; letter-spacing: 8px;
                   color: #2563eb; text-align: center; padding: 20px;
                   background: #eff6ff; border-radius: 8px; margin: 20px 0; }
        .footer { color: #6b7280; font-size: 12px; text-align: center;
                  margin-top: 20px; }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>mediq Healthcare</h1>
    </div>
    <h2>Verify your account</h2>
    <p>Hello <span th:text="${userName}">User</span>,</p>
    <p>Your OTP for account verification is:</p>
    <div class="otp-box" th:text="${otp}">483920</div>
    <p>This OTP is valid for <strong th:text="${expiresIn}">5</strong> minutes.</p>
    <p>If you did not request this, please ignore this email.</p>
    <div class="footer">
        &copy; 2024 mediq Healthcare. Do not reply to this email.
    </div>
</div>
</body>
</html>
```

**`appointment-confirmed-email.html`** — appointment confirmation:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>mediq — Appointment Confirmed</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f4f4f4; }
        .container { max-width: 600px; margin: 40px auto;
                     background: white; border-radius: 8px; padding: 40px; }
        .header { background: #16a34a; color: white; padding: 20px;
                  border-radius: 8px 8px 0 0; text-align: center; }
        .detail-box { background: #f0fdf4; border-left: 4px solid #16a34a;
                      padding: 20px; border-radius: 4px; margin: 20px 0; }
        .footer { color: #6b7280; font-size: 12px; text-align: center; }
    </style>
</head>
<body>
<div class="container">
    <div class="header"><h1>Appointment Confirmed ✓</h1></div>
    <p>Dear <span th:text="${patientName}">Patient</span>,</p>
    <p>Your appointment has been confirmed successfully.</p>
    <div class="detail-box">
        <p><strong>Doctor:</strong> <span th:text="${doctorName}">Dr. X</span></p>
        <p><strong>Date:</strong> <span th:text="${appointmentDate}">Jan 15, 2024</span></p>
        <p><strong>Time:</strong> <span th:text="${appointmentTime}">10:00 AM</span></p>
        <p><strong>Booking ID:</strong> <span th:text="${appointmentId}">APT-001</span></p>
    </div>
    <p>Please arrive 10 minutes before your scheduled time.</p>
    <div class="footer">&copy; 2024 mediq Healthcare</div>
</div>
</body>
</html>
```

**`payment-failed-email.html`** — payment failed with retry link:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>mediq — Payment Failed</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f4f4f4; }
        .container { max-width: 600px; margin: 40px auto;
                     background: white; border-radius: 8px; padding: 40px; }
        .header { background: #dc2626; color: white; padding: 20px;
                  border-radius: 8px 8px 0 0; text-align: center; }
        .warning-box { background: #fef2f2; border-left: 4px solid #dc2626;
                       padding: 20px; border-radius: 4px; margin: 20px 0; }
        .retry-btn { display: block; width: 200px; margin: 20px auto;
                     padding: 15px; background: #2563eb; color: white;
                     text-align: center; text-decoration: none;
                     border-radius: 8px; font-weight: bold; }
        .footer { color: #6b7280; font-size: 12px; text-align: center; }
    </style>
</head>
<body>
<div class="container">
    <div class="header"><h1>Payment Failed ✗</h1></div>
    <p>Dear <span th:text="${patientName}">Patient</span>,</p>
    <p>Unfortunately your payment for the appointment could not be processed.</p>
    <div class="warning-box">
        <p><strong>Reason:</strong> <span th:text="${failureReason}">Card declined</span></p>
        <p><strong>Amount:</strong> ₹<span th:text="${amount}">500</span></p>
        <p><strong>Appointment:</strong> Dr. <span th:text="${doctorName}">X</span>
           on <span th:text="${appointmentDate}">Jan 15, 2024</span></p>
    </div>
    <p>You have <strong>24 hours</strong> to complete payment before
       this appointment is automatically cancelled.</p>
    <a th:href="${retryLink}" class="retry-btn">Retry Payment</a>
    <p style="text-align:center; color: #6b7280; font-size: 12px;">
        Link expires in 24 hours
    </p>
    <div class="footer">&copy; 2024 mediq Healthcare</div>
</div>
</body>
</html>
```

**`appointment-cancelled-email.html`** — for both patient and doctor:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title>mediq — Appointment Cancelled</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f4f4f4; }
        .container { max-width: 600px; margin: 40px auto;
                     background: white; border-radius: 8px; padding: 40px; }
        .header { background: #f59e0b; color: white; padding: 20px;
                  border-radius: 8px 8px 0 0; text-align: center; }
        .detail-box { background: #fffbeb; border-left: 4px solid #f59e0b;
                      padding: 20px; border-radius: 4px; margin: 20px 0; }
        .footer { color: #6b7280; font-size: 12px; text-align: center; }
    </style>
</head>
<body>
<div class="container">
    <div class="header"><h1>Appointment Cancelled</h1></div>
    <p>Dear <span th:text="${recipientName}">User</span>,</p>
    <p th:text="${message}">Your appointment has been cancelled.</p>
    <div class="detail-box">
        <p><strong>Doctor:</strong> <span th:text="${doctorName}">Dr. X</span></p>
        <p><strong>Patient:</strong> <span th:text="${patientName}">Patient X</span></p>
        <p><strong>Date:</strong> <span th:text="${appointmentDate}">Jan 15, 2024</span></p>
        <p><strong>Reason:</strong> <span th:text="${reason}">Payment not completed</span></p>
    </div>
    <p>Please contact us if you have any questions.</p>
    <div class="footer">&copy; 2024 mediq Healthcare</div>
</div>
</body>
</html>
```

---

# PART 3: Notification Service Layer

## Step 8: NotificationService — uses injected strategies

Create `notification-service/src/main/java/com/mediq/notification/service/NotificationService.java`:

```java
package com.mediq.notification.service;

import com.mediq.notification.strategy.email.EmailSender;
import com.mediq.notification.strategy.otp.OtpSender;
import com.mediq.notification.strategy.sms.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class NotificationService {

    private static final Logger log =
        LoggerFactory.getLogger(NotificationService.class);

    private final OtpSender otpSender;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final TemplateEngine templateEngine;
    private final String fromName;

    public NotificationService(
            OtpSender otpSender,
            EmailSender emailSender,
            SmsSender smsSender,
            TemplateEngine templateEngine,
            @Value("${mediq.notification.from-name}") String fromName) {
        this.otpSender = otpSender;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.templateEngine = templateEngine;
        this.fromName = fromName;
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    public void sendOtpViaPhone(String userId, String phone,
                                 String otp, int expiresIn) {
        otpSender.sendOtp(userId, phone, otp, expiresIn);
    }

    public void sendOtpViaEmail(String userId, String email,
                                 String userName, String otp, int expiresIn) {
        Context ctx = new Context();
        ctx.setVariable("userName", userName);
        ctx.setVariable("otp", otp);
        ctx.setVariable("expiresIn", expiresIn);

        String body = templateEngine.process("email/otp-email", ctx);
        emailSender.sendEmail(email,
            "mediq — Your OTP Verification Code", body, true);

        log.info("OTP email sent → userId={} email={}", userId, email);
    }

    // ── Appointment Confirmed ─────────────────────────────────────────────────

    public void sendAppointmentConfirmed(
            String patientEmail, String patientPhone,
            String patientName, String doctorName,
            String appointmentId, String date, String time) {

        // Email via Mailtrap
        Context ctx = new Context();
        ctx.setVariable("patientName", patientName);
        ctx.setVariable("doctorName", doctorName);
        ctx.setVariable("appointmentDate", date);
        ctx.setVariable("appointmentTime", time);
        ctx.setVariable("appointmentId", appointmentId);

        String body = templateEngine.process(
            "email/appointment-confirmed-email", ctx);
        emailSender.sendEmail(patientEmail,
            "mediq — Appointment Confirmed ✓", body, true);

        // SMS via StaticSmsSender
        String smsMessage = String.format(
            "mediq: Your appointment with %s is confirmed on %s at %s. " +
            "Booking ID: %s", doctorName, date, time, appointmentId);
        smsSender.sendSms(patientPhone, smsMessage);
    }

    // ── Payment Failed ────────────────────────────────────────────────────────

    public void sendPaymentFailed(
            String patientEmail, String patientPhone,
            String patientName, String doctorName,
            String appointmentDate, String amount,
            String failureReason, String retryLink) {

        // Email via Mailtrap
        Context ctx = new Context();
        ctx.setVariable("patientName", patientName);
        ctx.setVariable("doctorName", doctorName);
        ctx.setVariable("appointmentDate", appointmentDate);
        ctx.setVariable("amount", amount);
        ctx.setVariable("failureReason", failureReason);
        ctx.setVariable("retryLink", retryLink);

        String body = templateEngine.process(
            "email/payment-failed-email", ctx);
        emailSender.sendEmail(patientEmail,
            "mediq — Action Required: Payment Failed", body, true);

        // SMS via StaticSmsSender
        String smsMessage = String.format(
            "mediq: Payment of Rs.%s failed for your appointment with %s. " +
            "Retry within 24hrs or appointment will be cancelled.", amount, doctorName);
        smsSender.sendSms(patientPhone, smsMessage);
    }

    // ── Appointment Cancelled/Rescheduled ─────────────────────────────────────

    public void sendAppointmentCancelled(
            // Patient details
            String patientEmail, String patientPhone, String patientName,
            // Doctor details
            String doctorEmail, String doctorPhone, String doctorName,
            // Appointment details
            String appointmentDate, String reason) {

        // Email to PATIENT
        Context patientCtx = new Context();
        patientCtx.setVariable("recipientName", patientName);
        patientCtx.setVariable("message", "Your appointment has been cancelled.");
        patientCtx.setVariable("doctorName", doctorName);
        patientCtx.setVariable("patientName", patientName);
        patientCtx.setVariable("appointmentDate", appointmentDate);
        patientCtx.setVariable("reason", reason);

        String patientBody = templateEngine.process(
            "email/appointment-cancelled-email", patientCtx);
        emailSender.sendEmail(patientEmail,
            "mediq — Appointment Cancelled", patientBody, true);

        // SMS to PATIENT
        smsSender.sendSms(patientPhone, String.format(
            "mediq: Your appointment with Dr.%s on %s has been cancelled. " +
            "Reason: %s", doctorName, appointmentDate, reason));

        // Email to DOCTOR
        Context doctorCtx = new Context();
        doctorCtx.setVariable("recipientName", "Dr. " + doctorName);
        doctorCtx.setVariable("message",
            "An appointment with your patient has been cancelled.");
        doctorCtx.setVariable("doctorName", doctorName);
        doctorCtx.setVariable("patientName", patientName);
        doctorCtx.setVariable("appointmentDate", appointmentDate);
        doctorCtx.setVariable("reason", reason);

        String doctorBody = templateEngine.process(
            "email/appointment-cancelled-email", doctorCtx);
        emailSender.sendEmail(doctorEmail,
            "mediq — Patient Appointment Cancelled", doctorBody, true);

        // SMS to DOCTOR
        smsSender.sendSms(doctorPhone, String.format(
            "mediq: Appointment with patient %s on %s has been cancelled. " +
            "Reason: %s", patientName, appointmentDate, reason));
    }
}
```

## Step 9: Kafka Event Consumers in notification-service

Create `notification-service/src/main/java/com/mediq/notification/consumer/`:

**`OtpEventConsumer.java`:**
```java
package com.mediq.notification.consumer;

import com.mediq.notification.event.OtpRequestedEvent;
import com.mediq.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OtpEventConsumer {

    private static final Logger log =
        LoggerFactory.getLogger(OtpEventConsumer.class);

    private final NotificationService notificationService;

    public OtpEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.user-events}",
        groupId = "mediq-notification-otp-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserEvent(OtpRequestedEvent event, Acknowledgment ack) {
        if (!"OTP_REQUESTED".equals(event.eventType())) {
            ack.acknowledge();
            return;
        }

        log.info("OTP event received → userId={} contactType={}",
            event.userId(), event.contactType());

        try {
            if ("EMAIL".equals(event.contactType())) {
                notificationService.sendOtpViaEmail(
                    event.userId(),
                    event.destination(),
                    event.userName(),
                    event.otp(),
                    event.expiresInMinutes()
                );
            } else if ("PHONE".equals(event.contactType())) {
                notificationService.sendOtpViaPhone(
                    event.userId(),
                    event.destination(),
                    event.otp(),
                    event.expiresInMinutes()
                );
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("OTP notification failed → userId={} error={}",
                event.userId(), e.getMessage());
            // Do NOT ack — Kafka retries
        }
    }
}
```

**`PaymentEventConsumer.java`:**
```java
package com.mediq.notification.consumer;

import com.mediq.notification.event.PaymentNotificationEvent;
import com.mediq.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log =
        LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final NotificationService notificationService;

    public PaymentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.payment-events}",
        groupId = "mediq-notification-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(PaymentNotificationEvent event,
                               Acknowledgment ack) {
        log.info("Payment event → type={} appointmentId={}",
            event.eventType(), event.appointmentId());

        try {
            switch (event.eventType()) {
                case "PAYMENT_SUCCEEDED" ->
                    notificationService.sendAppointmentConfirmed(
                        event.patientEmail(), event.patientPhone(),
                        event.patientName(), event.doctorName(),
                        event.appointmentId(), event.appointmentDate(),
                        event.appointmentTime());

                case "PAYMENT_FAILED" ->
                    notificationService.sendPaymentFailed(
                        event.patientEmail(), event.patientPhone(),
                        event.patientName(), event.doctorName(),
                        event.appointmentDate(), event.amount(),
                        event.failureReason(), event.retryLink());

                default -> log.debug("Unhandled payment event: {}",
                    event.eventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Payment notification failed → error={}", e.getMessage());
        }
    }
}
```

**`AppointmentEventConsumer.java`:**
```java
package com.mediq.notification.consumer;

import com.mediq.notification.event.AppointmentNotificationEvent;
import com.mediq.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEventConsumer {

    private static final Logger log =
        LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final NotificationService notificationService;

    public AppointmentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
        topics = "${mediq.kafka.topic.appointment-events}",
        groupId = "mediq-notification-appointment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAppointmentEvent(AppointmentNotificationEvent event,
                                   Acknowledgment ack) {
        log.info("Appointment event → type={} appointmentId={}",
            event.eventType(), event.appointmentId());

        try {
            switch (event.eventType()) {
                case "AppointmentCancelled", "AppointmentAutoCalculated" ->
                    notificationService.sendAppointmentCancelled(
                        event.patientEmail(), event.patientPhone(),
                        event.patientName(),
                        event.doctorEmail(), event.doctorPhone(),
                        event.doctorName(),
                        event.appointmentDate(), event.reason());

                default -> log.debug("No notification for: {}",
                    event.eventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Appointment notification failed → error={}", e.getMessage());
        }
    }
}
```

---

# PART 4: OTP Flow in user-service

## Step 10: OTP Event Record

Create `user-service/src/main/java/com/mediq/event/OtpRequestedEvent.java`:

```java
package com.mediq.event;

import java.time.Instant;
import java.util.UUID;

public record OtpRequestedEvent(
    String eventId,        // idempotency key
    String eventType,      // always "OTP_REQUESTED"
    String userId,
    String contactType,    // "EMAIL" or "PHONE"
    String destination,    // email address or phone number
    String userName,       // for email greeting
    String otp,            // the generated OTP
    int expiresInMinutes,  // 5
    Instant occurredAt
) {
    public static OtpRequestedEvent of(String userId, String contactType,
            String destination, String userName, String otp) {
        return new OtpRequestedEvent(
            UUID.randomUUID().toString(),
            "OTP_REQUESTED",
            userId,
            contactType,
            destination,
            userName,
            otp,
            5,
            Instant.now()
        );
    }
}
```

## Step 11: OTP Service in user-service

Create `user-service/src/main/java/com/mediq/service/OtpService.java`:

```java
package com.mediq.service;

import com.mediq.event.OtpRequestedEvent;
import com.mediq.exception.UserNotFoundException;
import com.mediq.model.ContactType;
import com.mediq.model.UserEntity;
import com.mediq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String OTP_KEY       = "otp:";
    private static final String ATTEMPTS_KEY  = "otp:attempts:";
    private static final int    OTP_TTL_MIN   = 5;
    private static final int    MAX_ATTEMPTS  = 5;

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final KafkaTemplate<String, OtpRequestedEvent> kafkaTemplate;
    private final String userEventsTopic;

    public OtpService(
            UserRepository userRepository,
            RedisTemplate<String, String> stringRedisTemplate,
            KafkaTemplate<String, OtpRequestedEvent> kafkaTemplate,
            @Value("${mediq.kafka.topic.user-events}") String userEventsTopic) {
        this.userRepository = userRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.userEventsTopic = userEventsTopic;
    }

    @Transactional(readOnly = true)
    public void sendOtp(UUID userId) {
        UserEntity user = userRepository.findByIdWithDetails(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // Generate ONE OTP for this user
        String otp = generateOtp();

        // Store in Redis with 5 min TTL
        String otpKey = OTP_KEY + userId;
        stringRedisTemplate.opsForValue()
            .set(otpKey, otp, Duration.ofMinutes(OTP_TTL_MIN));

        // Reset attempt counter
        String attemptsKey = ATTEMPTS_KEY + userId;
        stringRedisTemplate.delete(attemptsKey);

        String userName = user.getFirstName() + " " + user.getLastName();

        // Publish ONE OtpRequestedEvent per contact
        // notification-service handles delivery per channel
        user.getContacts().forEach(contact -> {
            OtpRequestedEvent event = OtpRequestedEvent.of(
                userId.toString(),
                contact.getContactType().name(),
                contact.getContactValue(),
                userName,
                otp
            );
            kafkaTemplate.send(userEventsTopic, userId.toString(), event);
            log.info("OtpRequestedEvent published → userId={} contactType={}",
                userId, contact.getContactType());
        });
    }

    @Transactional
    public OtpVerificationResult verifyOtp(UUID userId, String submittedOtp) {
        String otpKey      = OTP_KEY + userId;
        String attemptsKey = ATTEMPTS_KEY + userId;

        // Check if OTP exists (not expired)
        String storedOtp = stringRedisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            return OtpVerificationResult.expired(
                "OTP expired or not found. Please request a new OTP.");
        }

        // Check attempt count
        String attemptsStr = stringRedisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_ATTEMPTS) {
            // Should not reach here normally — invalidated below at 5th attempt
            stringRedisTemplate.delete(otpKey);
            return OtpVerificationResult.invalidated(
                "Too many wrong attempts. Please request a new OTP.");
        }

        // Verify OTP
        if (!storedOtp.equals(submittedOtp)) {
            int newAttempts = attempts + 1;
            stringRedisTemplate.opsForValue()
                .set(attemptsKey, String.valueOf(newAttempts),
                    Duration.ofMinutes(OTP_TTL_MIN));

            if (newAttempts >= MAX_ATTEMPTS) {
                // Invalidate OTP — force re-request
                stringRedisTemplate.delete(otpKey);
                return OtpVerificationResult.invalidated(
                    "Too many wrong attempts. OTP invalidated. " +
                    "Please request a new OTP.");
            }

            int remaining = MAX_ATTEMPTS - newAttempts;
            return OtpVerificationResult.wrong(
                "Invalid OTP. " + remaining + " attempts remaining.");
        }

        // OTP correct — clean up Redis + mark user verified
        stringRedisTemplate.delete(otpKey);
        stringRedisTemplate.delete(attemptsKey);

        userRepository.findById(userId).ifPresent(user -> {
            user.setVerified(true);
            userRepository.save(user);
            log.info("User verified successfully → userId={}", userId);
        });

        return OtpVerificationResult.success();
    }

    private String generateOtp() {
        // 6-digit OTP: 100000 to 999999
        int otp = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }
}
```

Create `OtpVerificationResult.java`:
```java
package com.mediq.service;

public record OtpVerificationResult(
    boolean success,
    String status,   // SUCCESS | WRONG | EXPIRED | INVALIDATED
    String message
) {
    public static OtpVerificationResult success() {
        return new OtpVerificationResult(true, "SUCCESS",
            "OTP verified successfully. Account activated.");
    }
    public static OtpVerificationResult wrong(String message) {
        return new OtpVerificationResult(false, "WRONG", message);
    }
    public static OtpVerificationResult expired(String message) {
        return new OtpVerificationResult(false, "EXPIRED", message);
    }
    public static OtpVerificationResult invalidated(String message) {
        return new OtpVerificationResult(false, "INVALIDATED", message);
    }
}
```

## Step 12: OTP Endpoints in UserController

Add to `user-service/src/main/java/com/mediq/controller/UserController.java`:

```java
// Inject OtpService
private final OtpService otpService;

// ── OTP endpoints ─────────────────────────────────────────────────────────

@PostMapping("/{userId}/send-otp")
public ResponseEntity<Map<String, String>> sendOtp(
        @PathVariable UUID userId) {
    otpService.sendOtp(userId);
    return ResponseEntity.ok(Map.of(
        "message", "OTP sent to your registered contact(s)",
        "expiresIn", "5 minutes"
    ));
}

@PostMapping("/{userId}/verify-otp")
public ResponseEntity<Map<String, String>> verifyOtp(
        @PathVariable UUID userId,
        @RequestBody Map<String, String> request) {

    String otp = request.get("otp");
    if (otp == null || otp.isBlank()) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "OTP is required"));
    }

    OtpVerificationResult result = otpService.verifyOtp(userId, otp);

    if (result.success()) {
        return ResponseEntity.ok(Map.of("message", result.message()));
    }

    // EXPIRED or INVALIDATED → 400
    // WRONG → 400 with remaining attempts
    return ResponseEntity.badRequest()
        .body(Map.of(
            "status", result.status(),
            "message", result.message()
        ));
}
```

## Step 13: Auto-trigger OTP on Registration

Update `UserService.java` — in `registerPatient` and `registerDoctor`,
after saving user, trigger OTP send:

```java
@Transactional
public UserResponse registerPatient(RegisterPatientRequest request) {
    UserEntity user = userMapper.toEntity(request, UserType.PATIENT);
    userRepository.save(user);

    // Publish UserRegistered event (existing)
    UserEvent event = buildEvent("USER_REGISTERED", user, request.contacts());
    eventPublisher.publish(event);

    // Auto-trigger OTP for each contact (new)
    // Small delay not needed — Kafka is async
    otpService.sendOtp(user.getId());

    log.info("Patient registered + OTP triggered: userId={}", user.getId());
    return userMapper.toResponse(user);
}
```

---

# PART 5: 24hr Auto-Cancel in appointment-service

## Step 14: Auto-cancel scheduler

Create `appointment-service/src/main/java/com/mediq/appointment/scheduler/PaymentTimeoutScheduler.java`:

```java
package com.mediq.appointment.scheduler;

import com.mediq.appointment.model.AppointmentEntity;
import com.mediq.appointment.model.AppointmentStatus;
import com.mediq.appointment.model.SlotStatus;
import com.mediq.appointment.repository.AppointmentRepository;
import com.mediq.appointment.repository.AppointmentSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class PaymentTimeoutScheduler {

    private static final Logger log =
        LoggerFactory.getLogger(PaymentTimeoutScheduler.class);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentTimeoutScheduler(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // Runs every hour
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cancelExpiredPayments() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        // Find appointments stuck in PAYMENT_FAILED for > 24 hours
        List<AppointmentEntity> expired = appointmentRepository
            .findByStatusAndCreatedAtBefore(
                AppointmentStatus.PAYMENT_FAILED, cutoff);

        if (expired.isEmpty()) {
            log.debug("No expired payment appointments found");
            return;
        }

        log.warn("Auto-cancelling {} expired payment appointments",
            expired.size());

        expired.forEach(appointment -> {
            // Cancel appointment
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment.setCancelledAt(Instant.now());
            appointment.setCancellationReason(
                "Payment not completed within 24 hours — auto-cancelled");
            appointmentRepository.save(appointment);

            // Release slot
            slotRepository.findById(appointment.getSlot().getId())
                .ifPresent(slot -> {
                    slot.setStatus(SlotStatus.AVAILABLE);
                    slotRepository.save(slot);
                });

            // Publish AppointmentCancelled event
            // notification-service will email/SMS patient + doctor
            Map<String, String> event = Map.of(
                "eventType", "AppointmentAutoCalculated",
                "appointmentId", appointment.getId().toString(),
                "patientId", appointment.getPatientId().toString(),
                "doctorId", appointment.getDoctorId().toString(),
                "reason", "Payment not completed within 24 hours"
            );
            kafkaTemplate.send("mediq.appointment.events",
                appointment.getId().toString(), event);

            log.info("Auto-cancelled appointmentId={}", appointment.getId());
        });
    }
}
```

Add to `AppointmentRepository`:
```java
List<AppointmentEntity> findByStatusAndCreatedAtBefore(
    AppointmentStatus status, Instant before);
```

---

# PART 6: KrakenD Routes

Add OTP endpoints to `helm/gateway/krakend/config/partials/endpoint_users.tmpl`:

```json
{
  "endpoint": "/api/v1/users/{userId}/send-otp",
  "method": "POST",
  "backend": [{
    "url_pattern": "/users/{userId}/send-otp",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json"
  }]
},
{
  "endpoint": "/api/v1/users/{userId}/verify-otp",
  "method": "POST",
  "backend": [{
    "url_pattern": "/users/{userId}/verify-otp",
    "host": ["{{ .hosts.user_service }}"],
    "encoding": "json"
  }]
}
```

Also add to `krakend/partials/endpoint_users.tmpl` (docker-compose version).

---

# Verification

## 1. Build and start
```powershell
docker build -t mediq/user-service:latest ./user-service
docker build -t mediq/notification-service:latest ./notification-service
docker build -t mediq/appointment-service:latest ./appointment-service
docker compose up --build
```

## 2. Register patient — OTP auto-triggered
```powershell
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Rahul",
    "lastName": "Sharma",
    "dateOfBirth": "1990-05-15",
    "password": "Test@1234",
    "contacts": [
      {"contactType": "EMAIL",
       "contactValue": "rahul@example.com", "isPrimary": true},
      {"contactType": "PHONE",
       "contactValue": "+919876543210", "isPrimary": false}
    ]
  }'
```

## 3. Check OTP in notification-service logs
```powershell
docker logs mediq-notification-service | grep "OTP"
# Expected:
# ╔══════════════════════════════════════════╗
# ║           mediq OTP NOTIFICATION         ║
# ║  userId   : xxxxx                        ║
# ║  phone    : +919876543210                ║
# ║  OTP      : 483920   ← copy this        ║
# ║  expires  : 5 minutes                   ║
# ╚══════════════════════════════════════════╝
```

## 4. Check OTP email in Mailtrap
```
Open: https://mailtrap.io → Email Testing → My Inbox
→ Email from noreply@mediq.com
→ Subject: "mediq — Your OTP Verification Code"
→ OTP visible in styled HTML email ✅
```

## 5. Verify OTP
```powershell
curl -X POST http://localhost:8080/api/v1/users/{userId}/verify-otp `
  -H "Content-Type: application/json" `
  -d '{"otp": "483920"}'
# Expected: {"message": "OTP verified successfully. Account activated."}
```

## 6. Test wrong OTP (Option B lockout)
```powershell
# Send wrong OTP 5 times
for ($i=1; $i -le 5; $i++) {
  curl -X POST http://localhost:8080/api/v1/users/{userId}/verify-otp `
    -H "Content-Type: application/json" `
    -d '{"otp": "000000"}'
}
# Attempt 1-4: {"status":"WRONG","message":"Invalid OTP. X attempts remaining."}
# Attempt 5:   {"status":"INVALIDATED","message":"Too many wrong attempts..."}

# Request new OTP
curl -X POST http://localhost:8080/api/v1/users/{userId}/send-otp
# Expected: {"message": "OTP sent to your registered contact(s)"}
```

## 7. Test payment failed email (Mailtrap)
```
After a failed payment Stripe webhook:
  Mailtrap inbox → email from noreply@mediq.com
  Subject: "mediq — Action Required: Payment Failed"
  → retry link visible ✅
```

## 8. Test appointment cancelled email (Mailtrap)
```
After auto-cancel runs (or manual cancel):
  Mailtrap inbox → TWO emails:
    1. To patient: "Your appointment has been cancelled"
    2. To doctor:  "Patient appointment has been cancelled"
```

## 9. Check SMS logs for all events
```powershell
docker logs mediq-notification-service | grep "SMS NOTIFICATION"
# Should show SMS logs for:
# OTP (phone contact)
# Appointment confirmed
# Payment failed
# Appointment cancelled
```

---

## Commit
```powershell
git add .
git commit -m "feat(m5c): OTP + email + SMS strategy pattern

Strategy Pattern:
  OtpSender interface → StaticOtpSender (logs to console)
  EmailSender interface → MailtrapEmailSender (real SMTP)
  SmsSender interface → StaticSmsSender (logs to console)
  NotificationStrategyConfig — strategy selected via config property
  Future providers: add new class + add case in factory, zero other change

OTP Flow (user-service):
  OtpService: generates 6-digit OTP, stores in Redis (5min TTL)
  Option B: 5 wrong attempts → OTP invalidated, request new one
  Publishes OtpRequestedEvent per contact (email + phone separately)
  Auto-triggered on registration

notification-service:
  OtpEventConsumer → email via Mailtrap + SMS via log
  PaymentEventConsumer → confirmed/failed emails + SMS logs
  AppointmentEventConsumer → cancelled emails to patient + doctor
  Thymeleaf HTML email templates for all triggers

appointment-service:
  PaymentTimeoutScheduler → runs every hour
  Auto-cancels PAYMENT_FAILED appointments older than 24hrs
  Releases slot back to AVAILABLE
  Publishes AppointmentAutoCalculated event → triggers cancellation emails

KrakenD:
  /api/v1/users/{userId}/send-otp (POST)
  /api/v1/users/{userId}/verify-otp (POST)"
```
