package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@Tag("integration")
class NumberingRollbackIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var processing: ProcessingGateway

    @Test
    fun `rollback da nota nao consome numero fiscal`() {
        val id = UUID.randomUUID()
        val collidingId = UUID.randomUUID()
        createRequest(id)
        criarNotaColisao(collidingId)
        val requestId = IssuanceRequestId(id.toString())
        val issuanceRequest = assertNotNull(processing.startProcessing(requestId))
        val calculated = CalculateInvoice().calculate(issuanceRequest.order, TaxationMode.POR_FORA)

        assertFailsWith<DataIntegrityViolationException> { processing.issue(issuanceRequest, calculated) }

        assertEquals(
            0,
            jdbc
                .sql("SELECT ultimo_numero FROM contador_numeracao WHERE serie=1")
                .query(Int::class.java)
                .single(),
        )
        jdbc.sql("DELETE FROM nota_fiscal WHERE serie=1 AND numero=1").update()
        jdbc.sql("DELETE FROM solicitacao WHERE id=:id").param("id", collidingId).update()
        assertEquals(
            1,
            processing
                .issue(issuanceRequest, calculated)
                .reference.number.value,
        )
    }

    private fun createRequest(id: UUID) {
        val now = Timestamp.from(Instant.parse("2026-11-14T18:00:00Z"))
        jdbc
            .sql(
                """INSERT INTO solicitacao
                (id,cliente_id,idempotency_key,impressao_digital,pedido,status,recebida_em,atualizada_em)
                VALUES (:id,:cliente,:chave,:digital,CAST(:pedido AS jsonb),'RECEBIDA',:agora,:agora)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("chave", UUID.randomUUID())
            .param("digital", "a".repeat(64))
            .param("pedido", PEDIDO_GRAVADO)
            .param("agora", now)
            .update()
    }

    private fun criarNotaColisao(id: UUID) {
        val now = Timestamp.from(Instant.parse("2026-11-14T18:00:00Z"))
        createRequest(id)
        jdbc
            .sql(
                """INSERT INTO nota_fiscal
                (serie, numero, solicitacao_id, cliente_id, modo_tributacao, aliquota,
                 total_itens, tributo, frete, total_nota, emitida_em, destinatario_tipo_pessoa,
                 destinatario_documento, frete_informado, frete_regiao, frete_percentual)
                VALUES (1,1,:id,:cliente,'POR_FORA',0,0,0,0,0,:agora,'FISICA','***.982.247-**',0,'SUDESTE',0.048)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("agora", now)
            .update()
    }
}
