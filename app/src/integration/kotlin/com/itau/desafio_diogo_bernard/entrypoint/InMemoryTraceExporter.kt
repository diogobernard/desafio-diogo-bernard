package com.itau.desafio_diogo_bernard.entrypoint

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class InMemoryTraceExporter {
    @Bean
    fun inMemorySpanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Bean
    fun sdkTracerProvider(exporter: InMemorySpanExporter): SdkTracerProvider =
        SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()

    @Bean
    fun openTelemetry(provider: SdkTracerProvider): OpenTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build()
}
