package com.itau.desafio_diogo_bernard.entrypoint

import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.headers.RequestHeadersSnippet
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.OperationPreprocessor
import org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import java.util.regex.Pattern

internal fun traceIdFicticio(): OperationPreprocessor =
    replacePattern(
        Pattern.compile("\"trace_id\"\\s*:\\s*\"[a-f0-9]+\""),
        "\"trace_id\" : \"00000000000000000000000000000000\"",
    )

internal fun postPedidoDocumento(
    client: String,
    key: String,
): MockHttpServletRequestBuilder =
    post("/issuance-requests")
        .header("X-Client-Id", client)
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(corpoPedidoDocumento())

internal fun cabecalhosPedidoDocumento(): RequestHeadersSnippet =
    requestHeaders(
        headerWithName("X-Client-Id").description("Cliente fornecido pelo gateway"),
        headerWithName("Idempotency-Key").description("UUID para deduplicar retries"),
    )
