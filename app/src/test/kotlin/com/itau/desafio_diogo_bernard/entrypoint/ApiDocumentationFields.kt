package com.itau.desafio_diogo_bernard.entrypoint

import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath

internal fun corpoPedidoDocumento(): String =
    """
    {
      "recipient": {
        "person_type": "FISICA",
        "document": "52998224725",
        "tax_regime": null
      },
      "items": [
        {"code": "SKU-1", "description": "Produto", "unit_price": 12.30, "quantity": 1}
      ],
      "freight_value": 5.00,
      "addresses": [{"purpose": "ENTREGA", "region": "SUDESTE"}]
    }
    """.trimIndent()

internal fun camposPedido(): List<FieldDescriptor> =
    listOf(
        fieldWithPath("recipient.person_type").description("Tipo de pessoa do destinatário"),
        fieldWithPath("recipient.document").description("CPF ou CNPJ fictício do destinatário"),
        fieldWithPath(
            "recipient.tax_regime",
        ).type(JsonFieldType.STRING).optional().description("Regime tributário, quando aplicável"),
        fieldWithPath("items[].code").description("Código do produto"),
        fieldWithPath("items[].description").description("Descrição do produto"),
        fieldWithPath("items[].unit_price").description("Valor unitário em reais"),
        fieldWithPath("items[].quantity").description("Quantidade de unidades"),
        fieldWithPath("freight_value").description("Valor do frete informado, em reais"),
        fieldWithPath("addresses[].purpose").description("Finalidade do endereço"),
        fieldWithPath("addresses[].region").description("Região geográfica do endereço"),
        fieldWithPath("addresses[].street").type(JsonFieldType.STRING).optional().description("Logradouro"),
        fieldWithPath("addresses[].city").type(JsonFieldType.STRING).optional().description("Cidade"),
        fieldWithPath("addresses[].state").type(JsonFieldType.STRING).optional().description("Unidade federativa"),
        fieldWithPath("addresses[].postal_code").type(JsonFieldType.STRING).optional().description("CEP"),
    )

internal fun camposSolicitacao(): List<FieldDescriptor> =
    listOf(
        fieldWithPath("id").description("Identificador UUID da solicitação"),
        fieldWithPath("status").description("Estado atual: RECEBIDA, PROCESSANDO, EMITIDA ou FALHA"),
        fieldWithPath("received_at").description("Instante de recebimento em UTC"),
        fieldWithPath("updated_at").description("Instante da última atualização em UTC"),
        fieldWithPath("invoice").type(JsonFieldType.OBJECT).optional().description("Resumo da nota quando emitida"),
        fieldWithPath("reason")
            .type(JsonFieldType.STRING)
            .optional()
            .description("Motivo público da falha definitiva"),
        fieldWithPath("links.self").description("Caminho para consultar esta solicitação"),
        fieldWithPath("links.invoice")
            .type(JsonFieldType.STRING)
            .optional()
            .description("Caminho para consultar a nota emitida"),
    )

internal fun camposProblema(): List<FieldDescriptor> =
    listOf(
        fieldWithPath("type").description("URI que identifica o tipo de problema"),
        fieldWithPath("title").description("Resumo legível do problema"),
        fieldWithPath("status").description("Código HTTP"),
        fieldWithPath("detail").description("Descrição legível do problema"),
        fieldWithPath("instance").description("Caminho da requisição que falhou"),
        fieldWithPath("trace_id").description("Identificador de rastreamento"),
        subsectionWithPath("errors").description("Violações associadas à requisição"),
    )

internal fun camposNotaFiscal(): List<FieldDescriptor> =
    listOf(
        fieldWithPath("number").description("Número sequencial da nota"),
        fieldWithPath("series").description("Série fiscal"),
        fieldWithPath("request_id").description("Identificador da solicitação de emissão"),
        fieldWithPath("issued_at").description("Instante de emissão em UTC"),
        fieldWithPath("taxation_mode").description("Modo de cálculo aplicado"),
        fieldWithPath("tax_rate").description("Alíquota aplicada"),
        fieldWithPath("recipient.person_type").description("Tipo de pessoa do destinatário"),
        fieldWithPath("recipient.masked_document").description("Documento mascarado do destinatário"),
        fieldWithPath("recipient.tax_regime")
            .type(JsonFieldType.STRING)
            .optional()
            .description("Regime tributário"),
        fieldWithPath("items[]").description("Itens e valores calculados"),
        fieldWithPath("freight.provided_value").description("Frete informado, em reais"),
        fieldWithPath("freight.region").description("Região usada no cálculo"),
        fieldWithPath("freight.percentage").description("Percentual de frete aplicado"),
        fieldWithPath("freight.surcharge").description("Acréscimo calculado, em reais"),
        fieldWithPath("freight.total").description("Total do frete, em reais"),
        fieldWithPath("totals.items_total").description("Soma dos itens, em reais"),
        fieldWithPath("totals.tax").description("Tributo calculado, em reais"),
        fieldWithPath("totals.freight").description("Frete calculado, em reais"),
        fieldWithPath("totals.invoice_total").description("Total da nota, em reais"),
    )
