@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.exception

class IngressCapacityExhaustedException(
    val reason: IngressRejectionReason,
) : RuntimeException("Capacidade de ingresso esgotada: ${reason.tag}", null, false, false)
