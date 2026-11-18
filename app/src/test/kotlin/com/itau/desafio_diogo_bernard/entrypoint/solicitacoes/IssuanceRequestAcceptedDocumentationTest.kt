package com.itau.desafio_diogo_bernard.entrypoint.solicitacoes

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.JacksonConfig
import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.camposPedido
import com.itau.desafio_diogo_bernard.entrypoint.camposSolicitacao
import com.itau.desafio_diogo_bernard.entrypoint.corpoPedidoDocumento
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveIssuanceRequest
import com.itau.desafio_diogo_bernard.usecase.emissao.IssuanceCommand
import com.itau.desafio_diogo_bernard.usecase.emissao.RequestIssuance
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(IssuanceRequestController::class)
@AutoConfigureRestDocs(uriHost = "api.notafiscal.local", uriScheme = "https")
@Import(JacksonConfig::class)
@EnableConfigurationProperties(NotaFiscalProperties::class)
class IssuanceRequestAcceptedDocumentationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean private lateinit var requestIssuance: RequestIssuance

    @MockitoBean private lateinit var retrieveRequest: RetrieveIssuanceRequest

    @MockitoBean private lateinit var metrics: BusinessMetrics

    @MockitoBean private lateinit var ingressMetrics: IngressMetrics

    @MockitoBean private lateinit var correlation: ObservabilityCorrelation

    @Test
    fun `documenta solicitacao aceita`() {
        val issuanceRequest = issuanceRequestFixture()
        given(
            requestIssuance.execute(
                IssuanceCommand(
                    ClientId("cliente-ficticio"),
                    IdempotencyKey(CHAVE_TESTE),
                    issuanceRequest.order,
                ),
            ),
        ).willReturn(issuanceRequest)
        mockMvc
            .perform(
                post("/issuance-requests")
                    .header("X-Client-Id", "cliente-ficticio")
                    .header("Idempotency-Key", CHAVE_TESTE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoPedidoDocumento()),
            ).andExpect(status().isAccepted)
            .andDo(
                document(
                    "issuance-requests/criar",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("X-Client-Id").description("Cliente fornecido pelo gateway"),
                        headerWithName("Idempotency-Key").description("UUID para deduplicar retries"),
                    ),
                    requestFields(camposPedido()),
                    responseHeaders(
                        headerWithName("Location").description("Caminho para consultar a solicitação"),
                        headerWithName("Retry-After")
                            .description("Intervalo mínimo para a próxima consulta, em segundos"),
                    ),
                    responseFields(camposSolicitacao()),
                ),
            )
    }
}
