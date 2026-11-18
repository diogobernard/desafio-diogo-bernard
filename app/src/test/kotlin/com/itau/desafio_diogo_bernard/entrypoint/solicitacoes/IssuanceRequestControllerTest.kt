package com.itau.desafio_diogo_bernard.entrypoint.solicitacoes

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.AddressRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.ItemRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.RecipientRequest
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveIssuanceRequest
import com.itau.desafio_diogo_bernard.usecase.emissao.IssuanceCommand
import com.itau.desafio_diogo_bernard.usecase.emissao.RequestIssuance
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.springframework.http.HttpHeaders
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IssuanceRequestControllerTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = BusinessMetrics(registry)
    private val correlation = ObservabilityCorrelation(ObservationRegistry.create())

    @Test
    fun `monta Location Retry-After e links por status`() {
        val moment = Instant.parse("2026-11-14T18:00:00Z")
        val order =
            IssueInvoiceRequest(
                RecipientRequest(PersonType.FISICA, "52998224725", null),
                listOf(ItemRequest("SKU-1", "Produto", BigDecimal("12.30"), BigDecimal.ONE)),
                BigDecimal("5.00"),
                listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
            )
        val issuanceRequest =
            order.toDomain().let {
                IssuanceRequest(
                    IssuanceRequestId(ID),
                    ClientId("cliente-a"),
                    IdempotencyKey(KEY),
                    Fingerprint("a".repeat(64)),
                    it,
                    IssuanceRequestStatus.RECEBIDA,
                    moment,
                    moment,
                )
            }
        val issuance =
            object : RequestIssuance {
                override fun execute(command: IssuanceCommand): IssuanceRequest = issuanceRequest
            }
        val query =
            object : RetrieveIssuanceRequest {
                override fun execute(
                    clientId: ClientId,
                    id: IssuanceRequestId,
                ): IssuanceRequestState = issuanceRequest.status()
            }

        val response =
            IssuanceRequestController(issuance, query, NotaFiscalProperties(), metrics, correlation)
                .create("cliente-a", KEY, order)

        assertEquals("/issuance-requests/$ID", response.headers.location?.toString())
        assertEquals("1", response.headers.getFirst(HttpHeaders.RETRY_AFTER))
        assertNotNull(response.body)
        assertEquals("/issuance-requests/$ID", response.body?.links?.self)
    }

    @Test
    fun `inclui Link para nota emitida e omite Retry-After`() {
        val issuanceRequest =
            issuanceRequest().copy(
                status = IssuanceRequestStatus.EMITIDA,
                invoiceReference = InvoiceReference(FiscalNumber(77), Series(1)),
            )
        val query =
            object : RetrieveIssuanceRequest {
                override fun execute(
                    clientId: ClientId,
                    id: IssuanceRequestId,
                ): IssuanceRequestState = issuanceRequest.status()
            }
        val issuance =
            object : RequestIssuance {
                override fun execute(command: IssuanceCommand): IssuanceRequest = issuanceRequest
            }

        val response =
            IssuanceRequestController(issuance, query, NotaFiscalProperties(), metrics, correlation)
                .retrieve("cliente-a", ID)

        assertEquals("</invoices/77?series=1>; rel=\"invoice\"", response.headers.getFirst(HttpHeaders.LINK))
        assertEquals(null, response.headers.getFirst(HttpHeaders.RETRY_AFTER))
    }

    private fun issuanceRequest(): IssuanceRequest {
        val order =
            IssueInvoiceRequest(
                RecipientRequest(PersonType.FISICA, "52998224725", null),
                listOf(ItemRequest("SKU-1", "Produto", BigDecimal("12.30"), BigDecimal.ONE)),
                BigDecimal("5.00"),
                listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
            ).toDomain()
        return IssuanceRequest(
            IssuanceRequestId(ID),
            ClientId("cliente-a"),
            IdempotencyKey(KEY),
            Fingerprint("a".repeat(64)),
            order,
            IssuanceRequestStatus.RECEBIDA,
            Instant.EPOCH,
            Instant.EPOCH,
        )
    }

    private companion object {
        const val ID = "01933b6e-7c1a-7001-9f10-2b6a4c8d0e11"
        const val KEY = "6f1c2b7e-3a4d-4e5f-8a9b-0c1d2e3f4a5b"
    }
}
