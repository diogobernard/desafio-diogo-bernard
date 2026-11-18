package com.itau.desafio_diogo_bernard.exception

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.validations.Violation

class InvalidOrderException(
    val violations: List<Violation>,
) : RuntimeException("Pedido inválido")

class IdempotencyKeyReusedException : RuntimeException("A chave de idempotência já foi usada com outro pedido")

class NumberingExhaustedException : RuntimeException("A numeração fiscal da série foi esgotada")

class IssuanceRequestNotFoundException(
    id: IssuanceRequestId,
) : RuntimeException("Solicitação não encontrada: ${id.value}")

class InvoiceNotFoundException : RuntimeException("Nota fiscal não encontrada")
