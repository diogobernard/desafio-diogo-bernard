@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.config

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DataSourceConfigTest {
    private val config = DataSourceConfig()

    @Test
    fun `constroi propriedades das fontes de dados`() {
        assertIs<DataSourceProperties>(config.writeDataSourceProperties())
        assertIs<DataSourceProperties>(config.readDataSourceProperties())
    }

    @Test
    fun `cria pool de escrita com a configuracao recebida`() {
        val properties = dataSourceProperties("write")

        config.writeDataSource(properties, Duration.ofSeconds(5)).use { dataSource ->
            assertEquals("jdbc:postgresql://localhost/notafiscal", dataSource.jdbcUrl)
            assertEquals("write-user", dataSource.username)
        }
    }

    @Test
    fun `aplica o connection timeout configurado a cada pool`() {
        config.writeDataSource(dataSourceProperties("write"), Duration.ofMillis(1_500)).use { dataSource ->
            assertEquals(1_500L, dataSource.connectionTimeout)
        }
        config.readDataSource(dataSourceProperties("read"), Duration.ofSeconds(3)).use { dataSource ->
            assertEquals(3_000L, dataSource.connectionTimeout)
        }
    }

    @Test
    fun `cria pool de leitura somente leitura`() {
        val properties = dataSourceProperties("read")

        config.readDataSource(properties, Duration.ofSeconds(5)).use { dataSource ->
            assertEquals("jdbc:postgresql://localhost/notafiscal", dataSource.jdbcUrl)
            assertTrue(dataSource.isReadOnly)
        }
    }

    @Test
    fun `usa proxy lazy com fonte de leitura`() {
        val write = HikariDataSource()
        val read = HikariDataSource().apply { isReadOnly = true }

        val proxy = config.dataSource(write, read)

        assertIs<LazyConnectionDataSourceProxy>(proxy)
        write.close()
        read.close()
    }

    private fun dataSourceProperties(user: String): DataSourceProperties =
        config.writeDataSourceProperties().apply {
            url = "jdbc:postgresql://localhost/notafiscal"
            username = "$user-user"
            password = "password"
        }
}
