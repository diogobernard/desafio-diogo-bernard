package com.itau.desafio_diogo_bernard.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.StreamWriteFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies

@Configuration
class JacksonConfig {
    @Bean
    fun jsonMapperBuilderCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            builder.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
        }
}
