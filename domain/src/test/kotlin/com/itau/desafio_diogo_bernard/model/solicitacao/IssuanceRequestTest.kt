package com.itau.desafio_diogo_bernard.model.solicitacao

import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.usecase.pedidoValido
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class IssuanceRequestTest {
    @Test
    fun `projeta a situacao sem o pedido preservando estado datas nota e motivo`() {
        val receivedAt = Instant.parse("2026-11-17T10:00:00Z")
        val updatedAt = Instant.parse("2026-11-17T10:00:05Z")
        val reference = InvoiceReference(FiscalNumber(7), Series(1))
        val issuanceRequest =
            IssuanceRequest(
                IssuanceRequestId("0190f3a0-0000-7000-8000-000000000001"),
                ClientId("cliente-a"),
                IdempotencyKey("chave"),
                Fingerprint("hash"),
                pedidoValido(),
                IssuanceRequestStatus.EMITIDA,
                receivedAt,
                updatedAt,
                reference,
                FailureReason("motivo"),
            )

        val state = issuanceRequest.status()

        assertEquals(
            IssuanceRequestState(
                IssuanceRequestId("0190f3a0-0000-7000-8000-000000000001"),
                ClientId("cliente-a"),
                IssuanceRequestStatus.EMITIDA,
                receivedAt,
                updatedAt,
                reference,
                FailureReason("motivo"),
            ),
            state,
        )
    }
}
