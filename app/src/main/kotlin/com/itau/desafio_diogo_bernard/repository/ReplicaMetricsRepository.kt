package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.OutboxEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.transaction.annotation.Transactional

interface ReplicaMetricsRepository : Repository<OutboxEntity, Long> {
    @Transactional(readOnly = true)
    @Query(
        value = "SELECT COALESCE(EXTRACT(EPOCH FROM (clock_timestamp() - pg_last_xact_replay_timestamp())), 0)::float8",
        nativeQuery = true,
    )
    fun lagSeconds(): Double
}
