package com.itau.desafio_diogo_bernard.config

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BusinessMetricsTest {
    @Test
    fun `conta falso 404 somente para sucesso dentro da janela`() {
        val registry = SimpleMeterRegistry()
        var now = 10L
        val metrics = BusinessMetrics(registry) { now }
        metrics.record404("recente")
        metrics.recordSuccessfulRead("desconhecida")
        now += JANELA_404_NANOS_TESTE
        metrics.recordSuccessfulRead("recente")

        assertEquals(1.0, registry.counter("notafiscal.leitura.404_falso").count())
    }

    @Test
    fun `remove ids expirados ao registrar nova leitura`() {
        val registry = SimpleMeterRegistry()
        var now = 0L
        val metrics = BusinessMetrics(registry) { now }
        metrics.record404("expirada")
        now = JANELA_404_NANOS_TESTE + 1
        metrics.record404("atual")
        metrics.recordSuccessfulRead("expirada")
        metrics.recordSuccessfulRead("atual")

        assertEquals(1.0, registry.counter("notafiscal.leitura.404_falso").count())
    }

    @Test
    fun `nao conta sucesso que chega depois da janela`() {
        val registry = SimpleMeterRegistry()
        var now = 0L
        val metrics = BusinessMetrics(registry) { now }
        metrics.record404("expirada")
        now = JANELA_404_NANOS_TESTE + 1

        metrics.recordSuccessfulRead("expirada")

        assertEquals(0.0, registry.counter("notafiscal.leitura.404_falso").count())
    }

    @Test
    fun `limita quantidade de leituras 404 pendentes`() {
        val registry = SimpleMeterRegistry()
        val metrics = BusinessMetrics(registry) { 1L }
        repeat(10_001) { metrics.record404("leitura-$it") }
        repeat(10_001) { metrics.recordSuccessfulRead("leitura-$it") }

        assertEquals(10_000.0, registry.counter("notafiscal.leitura.404_falso").count())
    }

    private companion object {
        const val JANELA_404_NANOS_TESTE = 60_000_000_000L
    }
}
