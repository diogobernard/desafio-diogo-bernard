@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.exception

enum class IngressRejectionReason(
    val tag: String,
) {
    FILA_CHEIA("fila_cheia"),
    ESPERA_EXCEDIDA("espera_excedida"),
    ENCERRANDO("encerrando"),
}
