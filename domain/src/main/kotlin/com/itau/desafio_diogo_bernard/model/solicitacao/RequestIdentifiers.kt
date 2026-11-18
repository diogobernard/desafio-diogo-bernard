package com.itau.desafio_diogo_bernard.model.solicitacao

@JvmInline
value class IssuanceRequestId(
    val value: String,
)

@JvmInline
value class ClientId(
    val value: String,
)

@JvmInline
value class IdempotencyKey(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class Fingerprint(
    val value: String,
)

@JvmInline
value class FailureReason(
    val value: String,
) {
    init {
        require(value.isNotBlank())
    }
}
