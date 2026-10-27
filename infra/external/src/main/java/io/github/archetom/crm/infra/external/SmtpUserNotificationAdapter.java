package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.UserNotificationPort;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Explicit production welcome-mail delivery; SMTP failures propagate to the event consumer. */
@Component
@Profile("prod")
@ConditionalOnProperty(prefix = "atom.user-notification.mail", name = "enabled", havingValue = "true")
public class SmtpUserNotificationAdapter implements UserNotificationPort {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpUserNotificationAdapter(JavaMailSender mailSender,
            @Value("${atom.user-notification.mail.from:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = validatedFromAddress(fromAddress);
    }

    @Override
    public void sendWelcomeNotification(String email, String username) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("欢迎使用 Atom CRM");
        message.setText("你的 Atom CRM 账号已创建。请使用管理员提供的站点地址登录。\n"
                + "如有疑问，请联系你的工作区管理员。");
        mailSender.send(message);
    }

    private static String validatedFromAddress(String address) {
        if (address == null || address.isBlank() || address.contains("\r") || address.contains("\n")) {
            throw new IllegalArgumentException("Welcome mail requires a valid sender address");
        }
        try {
            new InternetAddress(address, true).validate();
            return address;
        } catch (AddressException invalidAddress) {
            // Do not expose deployment configuration through a nested provider exception.
            throw new IllegalArgumentException("Welcome mail requires a valid sender address");
        }
    }
}
