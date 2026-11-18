package com.itau.desafio_diogo_bernard.entrypoint.model

import com.itau.desafio_diogo_bernard.config.JacksonConfig
import com.itau.desafio_diogo_bernard.entrypoint.notas.notaFiscalFixture
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals

class InvoiceIssuedNotificationTest {
    private val json: JsonMapper =
        JsonMapper.builder().also { JacksonConfig().jsonMapperBuilderCustomizer().customize(it) }.build()

    @Test
    fun `le do evento completo somente a identificacao da nota`() {
        val occurredAt = Instant.parse("2026-11-17T12:00:00Z")
        val invoice = InvoiceResponse.fromDomain(notaFiscalFixture())
        val payload =
            json.writeValueAsString(
                InvoiceIssuedEvent("evento-1", 1, occurredAt, InvoiceEventPayload.from(invoice)),
            )

        val readBack = json.readValue(payload, InvoiceIssuedNotification::class.java)

        assertEquals(
            InvoiceIssuedNotification("evento-1", occurredAt, InvoiceIssuedReference(77, 1, invoice.requestId)),
            readBack,
        )
    }
}
