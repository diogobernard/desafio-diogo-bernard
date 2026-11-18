package com.itau.desafio_diogo_bernard.repository

import java.time.Instant
import java.util.UUID

class IssuanceBatchParameters(
    inserts: List<IssuanceRequestInsert>,
) {
    val ids: Array<UUID> = inserts.map { it.id }.toTypedArray()
    val clients: Array<String> = inserts.map { it.client }.toTypedArray()
    val keys: Array<UUID> = inserts.map { it.key }.toTypedArray()
    val fingerprints: Array<String> = inserts.map { it.fingerprintValue }.toTypedArray()
    val orders: Array<String> = inserts.map { it.order }.toTypedArray()
    val receivedTimes: Array<Instant> = inserts.map { it.received }.toTypedArray()
    val updatedTimes: Array<Instant> = inserts.map { it.updated }.toTypedArray()
    val eventIds: Array<UUID> = inserts.map { it.event.eventId }.toTypedArray()
    val topics: Array<String> = inserts.map { it.event.topic }.toTypedArray()
    val eventKeys: Array<String> = inserts.map { it.event.key }.toTypedArray()
    val payloads: Array<String> = inserts.map { it.event.payload }.toTypedArray()
    val traceparents: Array<String?> = inserts.map { it.event.traceparent }.toTypedArray()
    val createdTimes: Array<Instant> = inserts.map { it.event.createdAt }.toTypedArray()
}
