package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.authentication.PasswordResetDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP adapter enabled only when the deployment explicitly configures password-recovery mail. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "atom.authentication.password-reset.mail", name = "enabled", havingValue = "true")
public class SmtpPasswordResetDelivery implements PasswordResetDelivery {

    private final JavaMailSender mailSender;

    @Value("${atom.authentication.password-reset.mail.from:}")
    private String fromAddress;

    @Override
    public void deliver(PasswordResetMessage message) {
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Password reset mail sender is not configured");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(message.recipientEmail());
        mail.setSubject("重置 Atom CRM 密码");
        mail.setText("你请求了重置 Atom CRM 密码。请在 " + message.expiresAt()
                + " 前打开以下链接设置新密码：\n" + message.resetUrl()
                + "\n\n如果这不是你的操作，请忽略此邮件。");
        mailSender.send(mail);
    }
}
