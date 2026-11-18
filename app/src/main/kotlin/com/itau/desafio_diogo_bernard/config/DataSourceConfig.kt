@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import java.time.Duration
import javax.sql.DataSource

@Configuration
class DataSourceConfig {
    @Bean
    @ConfigurationProperties("app.datasource.write")
    fun writeDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    @ConfigurationProperties("app.datasource.read")
    fun readDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    @ConfigurationProperties("app.datasource.write.hikari")
    fun writeDataSource(
        @Qualifier("writeDataSourceProperties") properties: DataSourceProperties,
        @Value("\${app.datasource.write.connection-timeout:10s}") connectionTimeout: Duration,
    ): HikariDataSource =
        properties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .apply { this.connectionTimeout = connectionTimeout.toMillis() }

    @Bean
    @ConfigurationProperties("app.datasource.read.hikari")
    fun readDataSource(
        @Qualifier("readDataSourceProperties") properties: DataSourceProperties,
        @Value("\${app.datasource.read.connection-timeout:30s}") connectionTimeout: Duration,
    ): HikariDataSource =
        properties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
            .apply {
                isReadOnly = true
                this.connectionTimeout = connectionTimeout.toMillis()
            }

    @Bean
    @Primary
    fun dataSource(
        @Qualifier("writeDataSource") write: DataSource,
        @Qualifier("readDataSource") read: DataSource,
    ): DataSource =
        LazyConnectionDataSourceProxy(write).apply {
            setReadOnlyDataSource(read)
        }
}
