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

    private static final Logger log = LoggerFactory.getLogger(NotificationStrategyConfig.class);

    @Bean
    @Primary
    public OtpSender otpSender(
            StaticOtpSender staticOtpSender,
            @Value("${mediq.notification.otp-strategy:static}") String strategy) {
        OtpSender selected = switch (strategy) {
            case "static" -> staticOtpSender;
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
            default -> {
                log.warn("Unknown SMS strategy: {} — falling back to static", strategy);
                yield staticSmsSender;
            }
        };
        log.info("SMS strategy selected: {}", selected.strategyName());
        return selected;
    }
}
