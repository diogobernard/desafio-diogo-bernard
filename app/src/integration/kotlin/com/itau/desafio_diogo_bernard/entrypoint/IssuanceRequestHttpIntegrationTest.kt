package com.itau.desafio_diogo_bernard.entrypoint

import org.junit.jupiter.api.Tag
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.client.EntityExchangeResult
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class IssuanceRequestHttpIntegrationTest : PostgresIntegrationBase() {
    @Test
    fun `aceita pedido e grava solicitacao e outbox na mesma transacao`() {
        val response = postar(PAYLOAD, UUID.randomUUID().toString())
        assertEquals(HttpStatus.ACCEPTED, response.status)
        assertEquals(1, contar("solicitacao"))
        assertEquals(1, contar("outbox"))
    }

    @Test
    fun `rejeita pedido invalido sem persistir`() {
        val response = postar("""{"recipient":{},"items":[],"freight_value":-1,"addresses":[]}""", KEY)
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(0, contar("solicitacao"))
    }

    @Test
    fun `cria uma unica solicitacao para chaves simultaneas`() {
        val executor = Executors.newFixedThreadPool(8)
        val tasks = (1..8).map { executor.submit<String> { postar(PAYLOAD, KEY).responseBody.orEmpty() } }
        val responses = tasks.map { it.get() }
        executor.shutdown()
        assertEquals(1, responses.map(::idResposta).distinct().size)
        assertEquals(1, contar("solicitacao"))
    }

    @Test
    fun `rejeita chave reutilizada com outro payload`() {
        assertEquals(HttpStatus.ACCEPTED, postar(PAYLOAD, KEY).status)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, postar(PAYLOAD.replace("12.50", "15.50"), KEY).status)
        assertEquals(1, contar("solicitacao"))
    }

    @Test
    fun `consulta imediata e isola solicitacoes por cliente`() {
        val id = idResposta(postar(PAYLOAD, UUID.randomUUID().toString()).responseBody.orEmpty())
        assertEquals(HttpStatus.OK, retrieve(id, CLIENTE).status)
        assertEquals(HttpStatus.NOT_FOUND, retrieve(id, "cliente-b").status)
    }

    @Test
    fun `responde problem details para JSON malformado`() {
        val response = postar("{", UUID.randomUUID().toString())
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertTrue(response.responseBody.orEmpty().contains("json_malformed"))
        assertEquals(0, contar("solicitacao"))
    }

    private fun postar(
        payload: String,
        key: String,
    ): EntityExchangeResult<String> =
        http
            .post()
            .uri("/issuance-requests")
            .headers { it.addAll(headers(key)) }
            .body(payload)
            .exchange()
            .returnResult(String::class.java)

    private fun retrieve(
        id: String,
        client: String,
    ): EntityExchangeResult<String> =
        http
            .get()
            .uri("/issuance-requests/$id")
            .headers { it.addAll(headers(client = client)) }
            .exchange()
            .returnResult(String::class.java)
}
