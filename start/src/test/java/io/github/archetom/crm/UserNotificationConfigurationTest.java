package io.github.archetom.crm;

import io.github.archetom.crm.application.event.listener.UserEventListener;
import io.github.archetom.crm.application.port.out.UserNotificationPort;
import io.github.archetom.crm.application.port.out.authentication.PasswordResetDelivery;
import io.github.archetom.crm.infra.external.LoggingUserNotificationAdapter;
import io.github.archetom.crm.infra.external.SmtpPasswordResetDelivery;
import io.github.archetom.crm.infra.external.SmtpUserNotificationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(UserEventListener.class, LoggingUserNotificationAdapter.class,
                    SmtpUserNotificationAdapter.class, SmtpPasswordResetDelivery.class);

    @Test
    void productionResolvesRealSmtpAdapterWithExplicitSettings() {
        contextRunner.withPropertyValues("spring.profiles.active=prod", "spring.mail.host=smtp.example.test",
                "atom.user-notification.mail.enabled=true", "atom.user-notification.mail.from=crm@example.test")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(UserNotificationPort.class)
                            .hasSingleBean(SmtpUserNotificationAdapter.class)
                            .doesNotHaveBean(LoggingUserNotificationAdapter.class);
                });
    }

    @Test
    void productionStillFailsWhenDeliveryIsUnconfigured() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("spring.profiles.active=prod",
                "atom.user-notification.mail.enabled=true", "atom.user-notification.mail.from=crm@example.test")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("spring.profiles.active=prod", "spring.mail.host=smtp.example.test",
                "atom.user-notification.mail.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void developmentDoesNotDeliverRealWelcomeMailEvenWhenSmtpIsConfigured() {
        contextRunner.withPropertyValues("spring.profiles.active=dev", "spring.mail.host=smtp.example.test",
                "atom.user-notification.mail.enabled=true", "atom.user-notification.mail.from=crm@example.test")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(UserNotificationPort.class)
                        .hasSingleBean(LoggingUserNotificationAdapter.class)
                        .doesNotHaveBean(SmtpUserNotificationAdapter.class));
    }

    @Test
    void explicitlyEnabledPasswordResetResolvesAutoConfiguredSender() {
        contextRunner.withPropertyValues("spring.profiles.active=prod", "spring.mail.host=smtp.example.test",
                "atom.user-notification.mail.enabled=true", "atom.user-notification.mail.from=crm@example.test",
                "atom.authentication.password-reset.mail.enabled=true",
                "atom.authentication.password-reset.mail.from=crm@example.test")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(PasswordResetDelivery.class)
                        .hasSingleBean(SmtpPasswordResetDelivery.class));
    }

    @Test
    void passwordResetRemainsOptInAndCannotEnableWithoutSmtp() {
        contextRunner.withPropertyValues("spring.profiles.active=dev", "spring.mail.host=smtp.example.test")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(PasswordResetDelivery.class));
        contextRunner.withPropertyValues("spring.profiles.active=dev",
                "atom.authentication.password-reset.mail.enabled=true",
                "atom.authentication.password-reset.mail.from=crm@example.test")
                .run(context -> assertThat(context).hasFailed());
    }
}
