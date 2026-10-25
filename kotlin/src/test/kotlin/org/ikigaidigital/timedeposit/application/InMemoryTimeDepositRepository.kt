package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.timedeposit.adapter.persistence.balanceAfterUpdate
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement

/**
 * A fake port for tests: it hands back exactly the read models it holds, in that order. Loading for
 * update returns new `TimeDeposit`s, so a change only reaches the stored deposits through
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

    override fun loadAllForUpdate(): List<TimeDeposit> {
        events += "loadAllForUpdate"
        return deposits.map { TimeDeposit(it.id, it.planType, it.balance.toDouble(), it.days) }
    }

    /** Applies the real adapter's rule (`balanceAfterUpdate`, ADR 0015); an unknown id is a caller bug. */
    override fun saveBalances(deposits: List<TimeDeposit>) {
        events += "saveBalances" + deposits.map { it.balance }
        val balances = deposits.associate { it.id to it.balance }
        require(balances.keys.all { id -> this.deposits.any { it.id == id } }) { "Unknown id in $balances" }
        this.deposits = this.deposits.map { stored ->
            balances[stored.id]
                ?.let { stored.copy(balance = balanceAfterUpdate(stored.balance, stored.balance.toDouble(), it)) }
                ?: stored
        }
    }
}
