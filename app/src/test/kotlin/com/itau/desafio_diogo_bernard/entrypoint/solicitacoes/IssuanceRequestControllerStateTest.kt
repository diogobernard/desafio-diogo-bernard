package com.itau.desafio_diogo_bernard.entrypoint.solicitacoes

import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssuanceRequestControllerStateTest {
    @Test
    fun `inclui retry after nos dois estados pendentes`() {
        listOf(IssuanceRequestStatus.RECEBIDA, IssuanceRequestStatus.PROCESSANDO).forEach { status ->
            val fixture = IssuanceRequestControllerFixture(issuanceRequestFixture(status))

            val response = fixture.controller.retrieve("cliente-a", SOLICITACAO_ID_TESTE)

            assertEquals("1", response.headers.getFirst(HttpHeaders.RETRY_AFTER))
            assertNull(response.headers.getFirst(HttpHeaders.LINK))
        }
    }

    @Test
    fun `retorna motivo de falha sem retry after`() {
        val issuanceRequest =
            issuanceRequestFixture(IssuanceRequestStatus.FALHA).copy(
                failureReason = FailureReason("Falha simulada"),
            )
        val fixture = IssuanceRequestControllerFixture(issuanceRequest)

        val response = fixture.controller.retrieve("cliente-a", SOLICITACAO_ID_TESTE)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Falha simulada", response.body?.reason)
        assertNull(response.headers.getFirst(HttpHeaders.RETRY_AFTER))
    }
}
