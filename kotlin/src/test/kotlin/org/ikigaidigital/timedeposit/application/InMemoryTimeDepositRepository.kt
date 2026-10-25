package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import java.math.BigDecimal

/**
 * A fake port for tests: it hands back exactly the read models it holds, in that order. Loading for
 * update returns immutable decimal snapshots, so a change only reaches the stored deposits through
 * [saveBalances], as with the real adapter. Each port call is appended to [events], so a test can
 * check the order of calls.
 */
class InMemoryTimeDepositRepository(
    var deposits: List<TimeDepositStatement> = emptyList(),
    val events: MutableList<String> = mutableListOf(),
) : TimeDepositRepository {

    override fun loadAllWithWithdrawals(): List<TimeDepositStatement> {
        events += "loadAllWithWithdrawals"
        return deposits
    }

    override fun loadAllForUpdate(): List<TimeDepositSnapshot> {
        events += "loadAllForUpdate"
        return deposits.map { TimeDepositSnapshot(it.id, it.planType, it.balance, it.days) }
    }

    /** Stores final balances without monetary calculation; an unknown id is a caller bug. */
    override fun saveBalances(balances: Map<Int, BigDecimal>) {
        events += "saveBalances" + balances.values.toList()
        require(balances.keys.all { id -> this.deposits.any { it.id == id } }) { "Unknown id in $balances" }
        this.deposits = this.deposits.map { stored ->
            balances[stored.id]
                ?.let { stored.copy(balance = it) }
                ?: stored
        }
    }
}
