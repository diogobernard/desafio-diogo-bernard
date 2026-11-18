package com.itau.desafio_diogo_bernard.usecase.emissao

import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey

data class IssuanceCommand(
    val clientId: ClientId,
    val key: IdempotencyKey,
    val order: Order,
)
