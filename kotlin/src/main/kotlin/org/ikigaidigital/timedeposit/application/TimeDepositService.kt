package org.ikigaidigital.timedeposit.application

import org.ikigaidigital.TimeDeposit
import org.ikigaidigital.TimeDepositCalculator
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The application's use cases on time deposits, one method per endpoint. A plain class, wired in
 * `ApplicationConfiguration` (ADR 0006).
 *
 * `@Transactional` sits on the class: the Kotlin `spring` compiler plugin opens only classes that
 * carry it (a method-level annotation leaves the class final), and Spring needs an open class to build
 * the transactional proxy (ADR 0007). Spring's proxy rules apply: a call from inside this class
 * bypasses the proxy, so the methods don't call each other.
 */
@Transactional
class TimeDepositService(
    private val repository: TimeDepositRepository,
    private val calculator: TimeDepositCalculator,
) {

    /**
     * Every time deposit with its withdrawals (deposits by id; withdrawals by date, then id). Read-only,
     * in a transaction of its own: the repository has none, and the fetch-join and mapping run inside it.
     */
    @Transactional(readOnly = true)
    fun listAll(): List<TimeDepositStatement> = repository.loadAllWithWithdrawals()

    /**
     * Adds one monthly interest to every time deposit and returns all of them, in the same shape as
     * [listAll]. Every call adds another month; there is no idempotency guard (ADR 0008). Lock,
     * calculate, save and re-read run in one transaction (ADR 0007): the rows stay locked until commit,
     * so a concurrent update waits instead of overwriting this one, and the list returned is exactly the
     * state this call produced.
     */
    fun updateAllBalances(): List<TimeDepositStatement> {
        val snapshots = repository.loadAllForUpdate()
        val deposits = snapshots.map { TimeDeposit(it.id, it.planType, it.balance.toDouble(), it.days) }
        calculator.updateBalance(deposits) // the frozen legacy calculator mutates the balances in place
        val balances = snapshots.zip(deposits).associate { (stored, updated) ->
            stored.id to balanceAfterUpdate(stored.balance, stored.balance.toDouble(), updated.balance)
        }
        repository.saveBalances(balances)
        return repository.loadAllWithWithdrawals()
    }

    /**
     * The stored balance after the calculator moved its Double from [before] (what it was given) to
     * [after]. Adds the change, rounded HALF_UP to cents, to the exact [stored] decimal, and returns
     * [stored] itself when nothing changed, so a deposit that earned nothing is never rewritten (ADR 0002).
     *
     * The subtraction is exact (Sterbenz lemma): one month's interest keeps [after] within a factor of
     * two of [before]. Below 2^46 the change is the calculator's rounded interest plus binary noise, so
     * rounding it to cents only removes the noise (ADR 0002). `-0.0 == 0.0` on a primitive Double, so a
     * signed zero needs no case of its own.
     */
    private fun balanceAfterUpdate(stored: BigDecimal, before: Double, after: Double): BigDecimal {
        val change = after - before
        return if (change == 0.0) stored else stored + BigDecimal.valueOf(change).setScale(2, RoundingMode.HALF_UP)
    }
}
