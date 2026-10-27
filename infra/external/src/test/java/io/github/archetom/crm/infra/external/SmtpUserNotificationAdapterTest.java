package io.github.archetom.crm.infra.external;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.*;

class SmtpUserNotificationAdapterTest {

    @Test
    void sendsPlainWelcomeWithoutCredentialsOrLoginIdentifiers() {
        RecordingSender sender = new RecordingSender();
        var adapter = new SmtpUserNotificationAdapter(sender, "crm@example.test");

        adapter.sendWelcomeNotification("member@example.test", "private-login-name");

        assertEquals("crm@example.test", sender.message.getFrom());
        assertArrayEquals(new String[]{"member@example.test"}, sender.message.getTo());
        assertEquals("欢迎使用 Atom CRM", sender.message.getSubject());
        assertNotNull(sender.message.getText());
        assertFalse(sender.message.getText().contains("private-login-name"));
        assertFalse(sender.message.getText().contains("member@example.test"));
    }

    @Test
    void rejectsMissingMalformedAndHeaderInjectionSenderWithoutEchoingIt() {
        for (String address : new String[]{"", " ", "missing-domain", "one@example.test,two@example.test",
                "crm@example.test\r\nBcc: other@example.test"}) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> new SmtpUserNotificationAdapter(new RecordingSender(), address));
            assertEquals("Welcome mail requires a valid sender address", failure.getMessage());
            assertNull(failure.getCause());
        }
    }

    @Test
    void propagatesDeliveryFailureInsteadOfReportingSuccess() {
        var failure = new MailSendException("Synthetic SMTP failure");
        var sender = new JavaMailSenderImpl() {
            @Override
            public void send(SimpleMailMessage message) {
                throw failure;
            }
        };
        var adapter = new SmtpUserNotificationAdapter(sender, "crm@example.test");
        assertSame(failure, assertThrows(MailSendException.class,
                () -> adapter.sendWelcomeNotification("member@example.test", "member")));
    }

    private static final class RecordingSender extends JavaMailSenderImpl {
        private SimpleMailMessage message;

        @Override
        public void send(SimpleMailMessage message) {
            this.message = new SimpleMailMessage(message);
        }
    }
}
