@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private const val ESPERA_CONFIRMACAO_PADRAO_SEGUNDOS = 10L
private const val CAPACIDADE_INGRESSO_PADRAO = 1000
private const val LOTE_MAXIMO_PADRAO = 100
private const val ESCRITORES_PADRAO = 2
private const val ESPERA_INGRESSO_PADRAO_SEGUNDOS = 8L

@ConfigurationProperties("nota-fiscal")
data class NotaFiscalProperties(
    val tributacao: Tributacao = Tributacao(),
    val numeracao: Numeracao = Numeracao(),
    val consulta: Consulta = Consulta(),
    val outbox: Outbox = Outbox(),
    val ingresso: Ingresso = Ingresso(),
    val processamento: Processamento = Processamento(),
    val kafka: Kafka = Kafka(),
    val consumidores: Consumidores = Consumidores(),
) {
    data class Tributacao(
        val modo: String = "POR_FORA",
    )

    data class Numeracao(
        val seriePadrao: Int = 1,
    )

    data class Consulta(
        val janelaLeituraConsistente: Duration = Duration.ofSeconds(60),
        val retryAfter: Duration = Duration.ofSeconds(1),
        val cacheUltimoNumero: Duration = Duration.ofSeconds(2),
    )

    data class Outbox(
        val intervalo: Duration = Duration.ofMillis(50),
        val lote: Int = 500,
        val retencao: Duration = Duration.ofHours(24),
        val esperaConfirmacao: Duration = Duration.ofSeconds(ESPERA_CONFIRMACAO_PADRAO_SEGUNDOS),
    ) {
        init {
            require(esperaConfirmacao.isPositive)
        }
    }

    data class Ingresso(
        val capacidade: Int = CAPACIDADE_INGRESSO_PADRAO,
        val esperaMaxima: Duration = Duration.ofSeconds(ESPERA_INGRESSO_PADRAO_SEGUNDOS),
        val loteMaximo: Int = LOTE_MAXIMO_PADRAO,
        val escritores: Int = ESCRITORES_PADRAO,
    ) {
        init {
            require(capacidade > 0)
            require(esperaMaxima.isPositive)
            require(loteMaximo > 0)
            require(escritores > 0)
        }
    }

    data class Processamento(
        val tentativas: Long = 5,
        val backoff: Duration = Duration.ofMillis(200),
        val backoffMaximo: Duration = Duration.ofSeconds(5),
    )

    data class Kafka(
        val replicacao: Int = 1,
        val topicoSolicitacoes: String = "notafiscal.solicitacoes.v1",
        val topicoSolicitacoesDlq: String = "notafiscal.solicitacoes.v1.dlq",
        val topicoNotas: String = "notafiscal.notas.v1",
    )

    data class Consumidores(
        val estoque: Consumidor = Consumidor(),
        val registro: Consumidor = Consumidor(),
        val entrega: Consumidor = Consumidor(),
        val financeiro: Consumidor = Consumidor(),
    )

    data class Consumidor(
        val latenciaMinima: Duration = Duration.ofMillis(50),
        val latenciaMaxima: Duration = Duration.ofMillis(300),
        val taxaFalha: Double = 0.0,
        val tentativas: Long = 4,
        val maxEmVoo: Int = 200,
    ) {
        init {
            require(!latenciaMinima.isNegative)
            require(latenciaMaxima >= latenciaMinima)
            require(taxaFalha in 0.0..1.0)
            require(tentativas > 0)
            require(maxEmVoo > 0)
        }
    }
}
