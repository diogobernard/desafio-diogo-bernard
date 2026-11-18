package com.itau.desafio_diogo_bernard.usecase.emissao

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest

interface RequestIssuance {
    fun execute(command: IssuanceCommand): IssuanceRequest
}
