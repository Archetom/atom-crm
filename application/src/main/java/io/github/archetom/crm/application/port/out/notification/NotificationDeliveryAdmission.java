package io.github.archetom.crm.application.port.out.notification;

import java.util.Optional;

/** Source admission result before provider I/O; plaintext endpoints live only in the command. */
public record NotificationDeliveryAdmission(
        Optional<NotificationDispatchCommand> command,
        NotificationDelivery delivery,
        Optional<NotificationDeliveryStatus> terminalStatus,
        boolean recoverNoDispatch) {

    public NotificationDeliveryAdmission {
        command = command == null ? Optional.empty() : command;
        terminalStatus = terminalStatus == null ? Optional.empty() : terminalStatus;
        if (delivery == null) throw new IllegalArgumentException("notification delivery admission is invalid");
    }

    public static NotificationDeliveryAdmission admitted(
            NotificationDispatchCommand command, NotificationDelivery delivery) {
        return new NotificationDeliveryAdmission(Optional.of(command), delivery, Optional.empty(), false);
    }

    public static NotificationDeliveryAdmission terminal(
            NotificationDelivery delivery, NotificationDeliveryStatus status) {
        return new NotificationDeliveryAdmission(Optional.empty(), delivery, Optional.of(status), false);
    }

    public static NotificationDeliveryAdmission deferred(NotificationDelivery delivery) {
        return new NotificationDeliveryAdmission(Optional.empty(), delivery, Optional.empty(), true);
    }

    public static NotificationDeliveryAdmission none(NotificationDelivery delivery) {
        return new NotificationDeliveryAdmission(Optional.empty(), delivery, Optional.empty(), false);
    }
}
