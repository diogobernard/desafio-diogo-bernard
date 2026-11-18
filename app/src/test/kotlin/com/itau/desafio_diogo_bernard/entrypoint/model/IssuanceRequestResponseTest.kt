package com.itau.desafio_diogo_bernard.entrypoint.model

import com.itau.desafio_diogo_bernard.entrypoint.solicitacoes.issuanceRequestFixture
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssuanceRequestResponseTest {
    @Test
    fun `mapeia motivo de solicitacao que falhou`() {
        val issuanceRequest =
            issuanceRequestFixture(IssuanceRequestStatus.FALHA).copy(
                failureReason = FailureReason("Falha simulada"),
            )

        val response = IssuanceRequestResponse.fromDomain(issuanceRequest.status())

        assertEquals("Falha simulada", response.reason)
        assertNull(response.invoice)
        assertNull(response.links.invoice)
    }
}
