package com.itau.desafio_diogo_bernard.entrypoint

import org.junit.jupiter.api.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class InvoiceHttpIntegrationTest : PostgresIntegrationBase() {
    @Test
    fun `GET da nota responde ETag e 304`() {
        prepararNota()
        val headers = headers()
        val first =
            http
                .get()
                .uri(
                    "/invoices/77?series=1",
                ).headers { it.addAll(headers) }
                .exchange()
                .returnResult(String::class.java)
        assertEquals(HttpStatus.OK, first.status)
        val body = first.responseBodyContent?.toString(Charsets.UTF_8).orEmpty()
        assertTrue(body.contains("\"tax_rate\":0.12,"), body)
        assertTrue(body.contains("\"description\":\"Teclado\""), body)
        assertTrue(body.contains("\"masked_document\":\"***.982.247-**\""), body)
        assertTrue(body.contains("\"percentage\":0.048,\"surcharge\":2.40,\"total\":52.40"), body)
        assertEquals("\"1-77\"", first.responseHeaders.getFirst(HttpHeaders.ETAG))
        assertTrue(
            first.responseHeaders
                .getFirst(HttpHeaders.CACHE_CONTROL)
                .orEmpty()
                .contains("immutable"),
        )

        headers.set(HttpHeaders.IF_NONE_MATCH, "\"1-77\"")
        val conditional =
            http
                .get()
                .uri(
                    "/invoices/77?series=1",
                ).headers { it.addAll(headers) }
                .exchange()
                .returnResult(String::class.java)
        assertEquals(HttpStatus.NOT_MODIFIED, conditional.status)
    }

    private fun prepararNota() {
        val id = UUID.fromString(ID)
        val now = Timestamp.from(Instant.parse("2026-11-14T18:00:00Z"))
        jdbc.sql("UPDATE contador_numeracao SET ultimo_numero=77 WHERE serie=1").update()
        jdbc
            .sql(
                """INSERT INTO solicitacao
            (id, cliente_id, idempotency_key, impressao_digital, pedido, status, recebida_em, atualizada_em)
            VALUES (:id, :cliente, :chave, :digital, '{}'::jsonb, 'EMITIDA', :agora, :agora)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("chave", UUID.randomUUID())
            .param("digital", "a".repeat(64))
            .param("agora", now)
            .update()
        jdbc
            .sql(
                """INSERT INTO nota_fiscal
            (serie, numero, solicitacao_id, cliente_id, modo_tributacao, aliquota, total_itens, tributo, frete,
             total_nota, emitida_em, destinatario_tipo_pessoa, destinatario_documento, destinatario_regime,
             frete_informado, frete_regiao, frete_percentual)
            VALUES (1, 77, :id, :cliente, 'POR_FORA', 0.1200, 200.00, 24.00, 52.40, 276.40, :agora,
             'FISICA', '***.982.247-**', NULL, 50.00, 'SUDESTE', 0.0480)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("agora", now)
            .update()
        jdbc
            .sql(
                """INSERT INTO nota_fiscal_item
            (serie, numero, sequencia, codigo, descricao, valor_unitario, quantidade, total_item, tributo_item)
            VALUES (1, 77, 1, 'SKU-001', 'Teclado', 100.00, 2, 200.00, 24.00)""",
            ).update()
    }

    private companion object {
        const val ID = "01933b6e-7c1a-7001-9f10-2b6a4c8d0e11"
    }
}
