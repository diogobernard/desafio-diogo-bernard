package com.itau.desafio_diogo_bernard.entrypoint.notas

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.JacksonConfig
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.camposNotaFiscal
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveInvoice
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(InvoiceController::class)
@AutoConfigureRestDocs(uriHost = "api.notafiscal.local", uriScheme = "https")
@Import(JacksonConfig::class)
@EnableConfigurationProperties(NotaFiscalProperties::class)
class InvoiceControllerDocumentationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean private lateinit var retrieveInvoice: RetrieveInvoice

    @MockitoBean private lateinit var metrics: BusinessMetrics

    @MockitoBean private lateinit var ingressMetrics: IngressMetrics

    @Test
    fun `documenta consulta de nota fiscal emitida`() {
        given(
            retrieveInvoice.execute(
                ClientId("cliente-ficticio"),
                InvoiceReference(FiscalNumber(77), Series(1)),
            ),
        ).willReturn(notaFiscalFixture("cliente-ficticio"))

        mockMvc
            .perform(
                get("/invoices/{number}", "77")
                    .header("X-Client-Id", "cliente-ficticio")
                    .header("If-None-Match", "\"1-76\"")
                    .param("series", "1"),
            ).andExpect(status().isOk)
            .andDo(
                document(
                    "invoices/consultar",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("number").description("Número sequencial da nota"),
                    ),
                    queryParameters(
                        parameterWithName("series").description("Série fiscal; usa a série padrão quando ausente"),
                    ),
                    requestHeaders(
                        headerWithName("X-Client-Id").description("Identificador do cliente fornecido pelo gateway"),
                        headerWithName("If-None-Match")
                            .description("ETag já recebido; uma correspondência produz `304`"),
                    ),
                    responseHeaders(
                        headerWithName("ETag").description("Validador da versão da nota"),
                        headerWithName("Cache-Control").description("Diretivas de cache privado por 24 horas"),
                    ),
                    responseFields(camposNotaFiscal()),
                ),
            )
    }
}
