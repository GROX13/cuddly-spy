package org.ikigaidigital.timedeposit.adapter.persistence

import org.ikigaidigital.timedeposit.application.Transactions
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * The [Transactions] port on Spring's [TransactionTemplate]: a read-write, REQUIRED transaction that
 * commits when the block returns and rolls back on any RuntimeException or Error (ADR 0012).
 *
 * Spring Data applies no transaction attributes to declared query methods or default methods, and any
 * repository method that does carry them (`readOnly = true` on SimpleJpaRepository's reads) joins this
 * transaction under REQUIRED instead of starting its own. The outer read-write transaction therefore
 * decides, and the dirty balances flush at commit. BalanceUpdatesApiIT and
 * TransactionTemplateTransactionsIT prove it by reading the balances back with JDBC.
 */
@Component
class TransactionTemplateTransactions(transactionManager: PlatformTransactionManager) : Transactions {

    private val template = TransactionTemplate(transactionManager)

    // execute is declared @Nullable; it returns exactly what the block returned, so T (nullable or not) holds.
    @Suppress("UNCHECKED_CAST")
    override fun <T> inTransaction(block: () -> T): T = template.execute { block() } as T
}
