package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import java.math.BigDecimal

/**
 * Outbound port: what the use cases need from storage. Plain Kotlin; adapter.persistence implements
 * it (ADR 0006).
 */
interface TimeDepositRepository {

    /** Every time deposit by id with its exact stored balance, each with its withdrawals by date, then id. */
    fun loadAllWithWithdrawals(): List<TimeDepositStatement>

    /** Every time deposit by id, its row locked until the caller's transaction ends. */
    fun loadAllForUpdate(): List<TimeDepositSnapshot>

    /**
     * Stores the supplied final balances (already rounded to cents), changing only the balance.
     * Equal balances are not written; unknown ids fail. Call in the same transaction as
     * [loadAllForUpdate] so the rows remain locked. No monetary calculation happens here.
     */
    fun saveBalances(balances: Map<Int, BigDecimal>)
}
