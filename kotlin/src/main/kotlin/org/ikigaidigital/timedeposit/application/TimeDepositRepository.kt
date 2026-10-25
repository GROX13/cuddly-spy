package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals

/**
 * Outbound port: what the use cases need from storage. Plain Kotlin; adapter.persistence implements
 * it (ADR 0010).
 *
 * The verbs avoid Spring Data's own names (`findAll`, `save…`): the implementing repository extends
 * both interfaces, and a clash with a different return type would not compile.
 */
interface TimeDepositRepository {

    /** Every time deposit by id, each with its withdrawals by date, then id. */
    fun loadAllWithWithdrawals(): List<TimeDepositWithWithdrawals>

    /** Every time deposit by id, its row locked until the caller's transaction ends. */
    fun loadAllForUpdate(): List<TimeDeposit>

    /**
     * Writes the balance of each given time deposit, rounded HALF_UP to 2 places; nothing else
     * changes. Call it in the same transaction as [loadAllForUpdate].
     */
    fun saveBalances(deposits: List<TimeDeposit>)
}
