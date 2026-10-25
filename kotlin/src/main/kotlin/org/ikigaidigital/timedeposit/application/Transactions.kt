package org.ikigaidigital.timedeposit.application

/**
 * Outbound port: runs a block in one transaction, committing when it returns and rolling back when
 * it throws. The core stays Spring-free, so a use case states its own boundary through this port
 * instead of `@Transactional` (ADR 0012). A plain interface, not a `fun interface`: Kotlin doesn't
 * allow a type parameter on a fun interface's method.
 */
interface Transactions {
    fun <T> inTransaction(block: () -> T): T
}
