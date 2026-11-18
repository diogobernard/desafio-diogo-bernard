package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.entrypoint.model.AddressRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.ItemRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.RecipientRequest
import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

@Tag("integration")
class ProcessingGatewayCoverageIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var processing: ProcessingGateway

    @Test
    fun `rejeita processamento de solicitacao ausente`() {
        assertFailsWith<IllegalStateException> {
            processing.issue(issuanceRequest(UUID.randomUUID()), notaCalculada())
        }
    }

    @Test
    fun `rejeita solicitacao emitida sem referencia fiscal`() {
        val id = UUID.randomUUID()
        inserirEmitida(id)

        assertFailsWith<IllegalArgumentException> {
            processing.issue(issuanceRequest(id), notaCalculada())
        }
    }

    @Test
    fun `rejeita solicitacao emitida cuja nota fiscal nao foi persistida`() {
        val id = UUID.randomUUID()
        inserirEmitida(id)
        jdbc.sql("UPDATE solicitacao SET nota_serie=1,nota_numero=88 WHERE id=:id").param("id", id).update()

        assertFailsWith<IllegalStateException> {
            processing.issue(issuanceRequest(id), notaCalculada())
        }
    }

    private fun inserirEmitida(id: UUID) {
        val now = Timestamp.from(Instant.parse("2026-11-14T18:00:00Z"))
        jdbc
            .sql(
                """INSERT INTO solicitacao
            (id,cliente_id,idempotency_key,impressao_digital,pedido,status,recebida_em,atualizada_em)
            VALUES (:id,:cliente,:chave,:digital,CAST(:pedido AS jsonb),'EMITIDA',:agora,:agora)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("chave", UUID.randomUUID())
            .param("digital", "a".repeat(64))
            .param("pedido", PEDIDO_GRAVADO)
            .param("agora", now)
            .update()
    }

    private fun issuanceRequest(id: UUID): IssuanceRequest = novaSolicitacao().copy(id = IssuanceRequestId(id.toString()))

    private fun notaCalculada(): CalculatedInvoice =
        CalculateInvoice().calculate(
            IssueInvoiceRequest(
                RecipientRequest(PersonType.FISICA, "52998224725", null),
                listOf(ItemRequest("SKU-1", "Produto", BigDecimal("12.50"), BigDecimal.ONE)),
                BigDecimal("2.00"),
                listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
            ).toDomain(),
            TaxationMode.POR_FORA,
        )
}
