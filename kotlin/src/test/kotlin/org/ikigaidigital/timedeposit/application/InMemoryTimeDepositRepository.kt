package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A fake port for tests: it hands back exactly the read models it holds, in that order. Loading for
 * update returns copies, so a change only reaches the stored deposits through [saveBalances], as with
 * the real adapter. Each port call is appended to [events], which a test can share with
 * [FakeTransactions] to check the order of calls.
 */
class InMemoryTimeDepositRepository(
    var deposits: List<TimeDepositWithWithdrawals> = emptyList(),
    val events: MutableList<String> = mutableListOf(),
) : TimeDepositRepository {

    override fun loadAllWithWithdrawals(): List<TimeDepositWithWithdrawals> {
        events += "loadAllWithWithdrawals"
        return deposits
    }

    override fun loadAllForUpdate(): List<TimeDeposit> {
        events += "loadAllForUpdate"
        return deposits.map { it.deposit.copy() }
    }

    /** Rounds HALF_UP to two places like the port's contract; an unknown id is a caller bug. */
    override fun saveBalances(deposits: List<TimeDeposit>) {
        events += "saveBalances" + deposits.map { it.balance }
        val balances = deposits.associate {
            it.id to BigDecimal.valueOf(it.balance).setScale(2, RoundingMode.HALF_UP).toDouble()
        }
        require(balances.keys.all { id -> this.deposits.any { it.deposit.id == id } }) { "Unknown id in $balances" }
        this.deposits = this.deposits.map { stored ->
            balances[stored.deposit.id]?.let { stored.copy(deposit = stored.deposit.copy(balance = it)) } ?: stored
        }
    }
}
