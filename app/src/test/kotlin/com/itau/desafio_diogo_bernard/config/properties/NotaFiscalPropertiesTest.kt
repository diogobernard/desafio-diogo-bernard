@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config.properties

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NotaFiscalPropertiesTest {
    @Test
    fun `usa parametros padrao do fluxo fiscal`() {
        val properties = NotaFiscalProperties()

        assertEquals("POR_FORA", properties.tributacao.modo)
        assertEquals(1, properties.numeracao.seriePadrao)
        assertEquals(Duration.ofSeconds(60), properties.consulta.janelaLeituraConsistente)
        assertEquals(Duration.ofSeconds(1), properties.consulta.retryAfter)
        assertEquals(Duration.ofSeconds(2), properties.consulta.cacheUltimoNumero)
    }

    @Test
    fun `usa parametros padrao de publicacao do outbox e de ingresso`() {
        val properties = NotaFiscalProperties()

        assertEquals(Duration.ofSeconds(10), properties.outbox.esperaConfirmacao)
        assertEquals(1000, properties.ingresso.capacidade)
        assertEquals(Duration.ofSeconds(8), properties.ingresso.esperaMaxima)
        assertEquals(100, properties.ingresso.loteMaximo)
        assertEquals(2, properties.ingresso.escritores)
    }

    @Test
    fun `rejeita espera de confirmacao e limites de ingresso nao positivos`() {
        assertFailsWith<IllegalArgumentException> { NotaFiscalProperties.Outbox(esperaConfirmacao = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { NotaFiscalProperties.Ingresso(capacidade = 0) }
        assertFailsWith<IllegalArgumentException> { NotaFiscalProperties.Ingresso(esperaMaxima = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { NotaFiscalProperties.Ingresso(loteMaximo = 0) }
        assertFailsWith<IllegalArgumentException> { NotaFiscalProperties.Ingresso(escritores = 0) }
        assertEquals(1, NotaFiscalProperties.Ingresso(capacidade = 1, loteMaximo = 1, escritores = 1).capacidade)
    }

    @Test
    fun `valida limites configuraveis do consumidor`() {
        assertFailsWith<IllegalArgumentException> { consumer(minLatency = Duration.ofMillis(-1)) }
        assertFailsWith<IllegalArgumentException> {
            consumer(minLatency = Duration.ofMillis(2), maxLatency = Duration.ofMillis(1))
        }
        assertFailsWith<IllegalArgumentException> { consumer(failureRate = -0.1) }
        assertFailsWith<IllegalArgumentException> { consumer(failureRate = 1.1) }
        assertFailsWith<IllegalArgumentException> { consumer(attempts = 0) }
        assertFailsWith<IllegalArgumentException> { consumer(maxInFlight = 0) }
        assertEquals(1.0, consumer(failureRate = 1.0).taxaFalha)
        assertEquals(1L, consumer(attempts = 1).tentativas)
    }

    private fun consumer(
        minLatency: Duration = Duration.ZERO,
        maxLatency: Duration = Duration.ofMillis(1),
        failureRate: Double = 0.5,
        attempts: Long = 2,
        maxInFlight: Int = 1,
    ): NotaFiscalProperties.Consumidor =
        NotaFiscalProperties.Consumidor(
            minLatency,
            maxLatency,
            failureRate,
            attempts,
            maxInFlight,
        )
}
