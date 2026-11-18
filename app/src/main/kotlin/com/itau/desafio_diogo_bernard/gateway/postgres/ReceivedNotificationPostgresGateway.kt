package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceIssuedNotification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import com.itau.desafio_diogo_bernard.repository.ReceivedNotificationRepository as Notifications

@Component
class ReceivedNotificationPostgresGateway(
    private val notifications: Notifications,
    private val clock: Clock,
) {
    @Transactional
    fun register(
        consumer: String,
        event: InvoiceIssuedNotification,
    ) {
        notifications.register(
            consumer,
            UUID.fromString(event.eventId),
            event.invoice.series,
            event.invoice.number,
            clock.instant(),
        )
    }
}
