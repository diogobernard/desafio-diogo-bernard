package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.NumberingCounterEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NumberingCounterRepository : JpaRepository<NumberingCounterEntity, Int> {
    @Query(
        value =
            "UPDATE contador_numeracao SET ultimo_numero = ultimo_numero + 1 " +
                "WHERE serie = :serie AND ultimo_numero < 999999999 RETURNING ultimo_numero",
        nativeQuery = true,
    )
    fun reserve(
        @Param("serie") series: Int,
    ): Int?

    @Query("select c.lastNumber from NumberingCounterEntity c where c.series = :serie")
    fun lastNumber(
        @Param("serie") series: Int,
    ): Int?
}
