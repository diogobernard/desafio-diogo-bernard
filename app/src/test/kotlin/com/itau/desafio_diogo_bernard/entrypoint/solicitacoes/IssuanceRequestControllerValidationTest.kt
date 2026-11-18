package com.itau.desafio_diogo_bernard.entrypoint.solicitacoes

import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.exception.InvalidOrderException
import com.itau.desafio_diogo_bernard.exception.IssuanceRequestNotFoundException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IssuanceRequestControllerValidationTest {
    private val fixture = IssuanceRequestControllerFixture()
    private val request = issueInvoiceRequestFixture()

    @Test
    fun `rejeita cliente ausente vazio ou maior que o limite no POST`() {
        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.create(null, CHAVE_TESTE, request) }
        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.create(" ", CHAVE_TESTE, request) }
        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.create("a".repeat(65), CHAVE_TESTE, request) }
    }

    @Test
    fun `rejeita chave de idempotencia ausente ou invalida`() {
        assertStatus(HttpStatus.BAD_REQUEST) { fixture.controller.create("cliente-a", null, request) }
        assertStatus(HttpStatus.BAD_REQUEST) { fixture.controller.create("cliente-a", "chave-invalida", request) }
        assertStatus(HttpStatus.BAD_REQUEST) { fixture.controller.create("cliente-a", "1-1-1-1-1", request) }
    }

    @Test
    fun `reune todos os campos ausentes do corpo antes de solicitar a emissao`() {
        val empty = IssueInvoiceRequest(null, null, null, null)

        val error =
            assertFailsWith<InvalidOrderException> {
                fixture.controller.create("cliente-a", CHAVE_TESTE, empty)
            }

        assertEquals(5, error.violations.size)
    }

    @Test
    fun `valida cliente e chave antes do corpo`() {
        val empty = IssueInvoiceRequest(null, null, null, null)

        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.create(null, CHAVE_TESTE, empty) }
        assertStatus(HttpStatus.BAD_REQUEST) { fixture.controller.create("cliente-a", null, empty) }
    }

    @Test
    fun `aceita uuid canonico em maiusculas e normaliza cliente`() {
        val response = fixture.controller.create(" cliente-a ", CHAVE_TESTE.uppercase(), request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("/issuance-requests/$SOLICITACAO_ID_TESTE", response.headers.location?.path)
    }

    @Test
    fun `rejeita cliente e identificador invalidos na consulta`() {
        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.retrieve(null, SOLICITACAO_ID_TESTE) }
        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.retrieve(" ", SOLICITACAO_ID_TESTE) }
        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.retrieve("a".repeat(65), SOLICITACAO_ID_TESTE) }
        assertStatus(HttpStatus.NOT_FOUND) { fixture.controller.retrieve("cliente-a", "id-invalido") }
        assertStatus(HttpStatus.NOT_FOUND) { fixture.controller.retrieve("cliente-a", "1-1-1-1-1") }
    }

    @Test
    fun `aceita identificador canonico em maiusculas`() {
        val response = fixture.controller.retrieve("cliente-a", SOLICITACAO_ID_TESTE.uppercase())

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `aceita cliente no tamanho maximo e rejeita somente espacos`() {
        val response = fixture.controller.retrieve("a".repeat(64), SOLICITACAO_ID_TESTE)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertStatus(HttpStatus.UNAUTHORIZED) { fixture.controller.retrieve("   ", SOLICITACAO_ID_TESTE) }
    }

    @Test
    fun `registra consulta ausente como candidato a falso 404`() {
        val fixture = IssuanceRequestControllerFixture { _, id -> throw IssuanceRequestNotFoundException(id) }
        assertFailsWith<IssuanceRequestNotFoundException> {
            fixture.controller.retrieve("cliente-a", SOLICITACAO_ID_TESTE)
        }
        fixture.metrics.recordSuccessfulRead(SOLICITACAO_ID_TESTE)

        assertEquals(1.0, fixture.registry.counter("notafiscal.leitura.404_falso").count())
    }

    private fun assertStatus(
        status: HttpStatus,
        action: () -> Any,
    ) {
        assertEquals(status, assertFailsWith<ResponseStatusException> { action() }.statusCode)
    }
}
