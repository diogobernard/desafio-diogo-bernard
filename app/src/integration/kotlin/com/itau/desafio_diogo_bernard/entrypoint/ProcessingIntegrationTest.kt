package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceEventPayload
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxPublication
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class ProcessingIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var publisher: OutboxPublication

    @Autowired
    private lateinit var properties: NotaFiscalProperties

    @Autowired
    private lateinit var json: JsonMapper

    @Test
    fun `numera sem duplicidade nem lacunas com varias instancias`() {
        val executor = Executors.newFixedThreadPool(20)
        val responses = (1..2_000).map { executor.submit<String> { solicitar(PAYLOAD) } }.map { it.get() }
        executor.shutdown()

        await.atMost(120, SECONDS).untilAsserted {
            assertEquals(2_000, jdbc.sql("SELECT count(*) FROM nota_fiscal").query(Int::class.java).single())
        }
        val bracket = jdbc.sql("SELECT max(numero)-min(numero)+1 FROM nota_fiscal").query(Int::class.java).single()
        val distinct = jdbc.sql("SELECT count(DISTINCT numero) FROM nota_fiscal").query(Int::class.java).single()
        assertEquals(2_000, responses.size)
        assertEquals(2_000, bracket)
        assertEquals(2_000, distinct)
    }

    @Test
    fun `evento NotaEmitida e identico a nota persistida`() {
        solicitar(PAYLOAD)

        await.atMost(20, SECONDS).untilAsserted {
            assertEquals(1, jdbc.sql("SELECT count(*) FROM nota_fiscal").query(Int::class.java).single())
        }
        val number = jdbc.sql("SELECT numero FROM nota_fiscal").query(Int::class.java).single()
        val invoice =
            http
                .get()
                .uri("/invoices/$number?series=1")
                .headers { it.addAll(headers()) }
                .exchange()
                .returnResult(String::class.java)

        val eventInvoice =
            jdbc
                .sql("SELECT payload->>'nota' FROM outbox WHERE topico='notafiscal.notas.v1'")
                .query(String::class.java)
                .single()
        val persisted = json.readValue(invoice.responseBody.orEmpty(), InvoiceResponse::class.java)

        assertTrue(invoice.status.is2xxSuccessful)
        assertEquals(InvoiceEventPayload.from(persisted), json.readValue(eventInvoice, InvoiceEventPayload::class.java))
    }

    @Test
    fun `publica outbox ao menos uma vez com duas instancias`() {
        solicitar(PAYLOAD)
        val executor = Executors.newFixedThreadPool(4)
        val topics = listOf(properties.kafka.topicoSolicitacoes, properties.kafka.topicoNotas)
        val dispatches = topics.flatMap { topic -> (1..2).map { executor.submit<Int> { publisher.publishPending(topic, 500) } } }
        dispatches.forEach { it.get() }
        executor.shutdown()

        await.atMost(20, SECONDS).untilAsserted {
            assertEquals(1, jdbc.sql("SELECT count(*) FROM nota_fiscal").query(Int::class.java).single())
            assertEquals(
                0,
                jdbc
                    .sql("SELECT count(*) FROM outbox WHERE publicado_em IS NULL")
                    .query(Int::class.java)
                    .single(),
            )
        }
    }

    @Test
    fun `pedidos sequenciais e paralelos nao acumulam itens`() {
        val executor = Executors.newFixedThreadPool(3)
        (1..3)
            .map { quantity ->
                executor.submit { solicitar(payloadComItens(quantity)) }
            }.forEach { it.get() }
        executor.shutdown()

        await.atMost(20, SECONDS).untilAsserted {
            assertEquals(3, jdbc.sql("SELECT count(*) FROM nota_fiscal").query(Int::class.java).single())
        }
        val consistent =
            jdbc
                .sql(
                    """SELECT count(*) FROM solicitacao s JOIN nota_fiscal n ON n.solicitacao_id=s.id
                WHERE jsonb_array_length(s.pedido->'itens')=(SELECT count(*) FROM nota_fiscal_item i
                WHERE i.serie=n.serie AND i.numero=n.numero)""",
                ).query(Int::class.java)
                .single()
        assertEquals(3, consistent)
    }

    private fun solicitar(payload: String): String {
        val response =
            http
                .post()
                .uri("/issuance-requests")
                .headers { it.addAll(headers(UUID.randomUUID().toString())) }
                .body(payload)
                .exchange()
                .returnResult(String::class.java)
        assertTrue(response.status.is2xxSuccessful)
        return idResposta(response.responseBody.orEmpty())
    }

    private fun payloadComItens(quantity: Int): String {
        val items =
            (1..quantity).joinToString(",") {
                "{\"codigo\":\"SKU-$it\",\"descricao\":\"Produto\",\"valor_unitario\":12.50,\"quantidade\":1}"
            }
        val originalItem = "{\"codigo\":\"SKU-1\",\"descricao\":\"Produto\",\"valor_unitario\":12.50,\"quantidade\":1}"
        return PAYLOAD.replace(originalItem, items)
    }
}
