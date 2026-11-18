package com.itau.desafio_diogo_bernard.gateway.id

import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestIdGenerator
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@Component
class UuidV7Generator(
    private val clock: Clock,
) : IssuanceRequestIdGenerator {
    override fun generate(): IssuanceRequestId {
        val timestamp = clock.millis().coerceAtLeast(0)
        val sequence = counter.incrementAndGet() and MASCARA_SEQUENCIA
        val random = randomness.nextLong()
        val msb = (timestamp shl 16) or VERSAO_V7 or sequence
        val lsb = (random and MASCARA_VARIANTE) or VARIANTE_RFC
        return IssuanceRequestId(UUID(msb, lsb).toString())
    }

    companion object {
        private const val VERSAO_V7 = 0x7000L
        private const val MASCARA_SEQUENCIA = 0x0fffL
        private const val MASCARA_VARIANTE = 0x3fffffffffffffffL
        private const val VARIANTE_RFC = Long.MIN_VALUE
        private val counter = AtomicLong()
        private val randomness = SecureRandom()
    }
}
