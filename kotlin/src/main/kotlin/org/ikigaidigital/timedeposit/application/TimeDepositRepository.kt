package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement

/**
 * Outbound port: what the use cases need from storage. Plain Kotlin; adapter.persistence implements
 * it (ADR 0010).
 *
 * The verbs avoid Spring Data's own names (`findAll`, `save…`): the implementing repository extends
 * both interfaces, and a clash with a different return type would not compile.
 */
interface TimeDepositRepository {

    /** Every time deposit by id with its exact stored balance, each with its withdrawals by date, then id. */
    fun loadAllWithWithdrawals(): List<TimeDepositStatement>

    /** Every time deposit by id, its row locked until the caller's transaction ends. */
    fun loadAllForUpdate(): List<TimeDeposit>

    /**
     * Adds each given time deposit's change since [loadAllForUpdate] to its stored balance, rounded
     * HALF_UP to 2 places (ADR 0015). A deposit with no change is not written,
     * and nothing but the balance changes. Call it in the same transaction as [loadAllForUpdate].
     */
    fun saveBalances(deposits: List<TimeDeposit>)
}
