package org.ikigaidigital.timedeposit.adapter.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The persistence adapter: one class over JPA's [EntityManager] (ADR 0016). `@Repository` makes it
 * a bean and puts it behind Spring's exception translation, so a JPA or Hibernate exception, or an
 * `IllegalArgumentException` from a caller bug, arrives as Spring's `DataAccessException`. Every
 * method joins the caller's transaction (`TimeDepositService`).
 */
@Repository
class JpaTimeDepositRepository(private val entityManager: EntityManager) : TimeDepositRepository {

    /**
     * One query: Hibernate keeps one root per deposit, and `@OrderBy` sorts the fetched withdrawals.
     * The stored NUMERIC(19,2) balance goes out as is; it never passes through a Double (ADR 0015).
     */
    override fun loadAllWithWithdrawals(): List<TimeDepositStatement> =
        entityManager
            .createQuery(
                "select d from TimeDepositEntity d left join fetch d.withdrawals order by d.id",
                TimeDepositEntity::class.java,
            )
            .resultList
            .map { entity ->
                TimeDepositStatement(
                    id = entity.id,
                    planType = entity.planType,
                    balance = entity.balance,
                    days = entity.days,
                    withdrawals = entity.withdrawals.map { it.toDomain() },
                )
            }

    /**
     * `select … for no key update` (Hibernate 7.4's PESSIMISTIC_WRITE on PostgreSQL): a concurrent
     * balance update waits until this transaction ends, so no monthly run reads a balance another run
     * is about to overwrite (ADR 0008, ADR 0016).
     */
    override fun loadAllForUpdate(): List<TimeDeposit> =
        entityManager
            .createQuery("select d from TimeDepositEntity d order by d.id", TimeDepositEntity::class.java)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .map { it.toDomain() }

    /**
     * Updates the managed entities; Hibernate flushes the dirty balances when the transaction
     * commits. `find` serves each entity from the persistence context, where [loadAllForUpdate] locked
     * and mapped it, so `toDouble()` gives exactly the Double the calculator was handed, and the stored
     * decimal gets only the change (ADR 0015). An unchanged balance is the same instance, so the entity
     * stays clean and no UPDATE runs. An id that doesn't exist is a caller bug (the deposits come from
     * [loadAllForUpdate]), so it fails instead of being skipped silently.
     */
    override fun saveBalances(deposits: List<TimeDeposit>) {
        deposits.forEach { deposit ->
            val entity = requireNotNull(entityManager.find(TimeDepositEntity::class.java, deposit.id)) {
                "No time deposit with id ${deposit.id}"
            }
            entity.balance = balanceAfterUpdate(entity.balance, entity.balance.toDouble(), deposit.balance)
        }
    }
}

private fun TimeDepositEntity.toDomain() = TimeDeposit(id, planType, balance.toDouble(), days)

private fun WithdrawalEntity.toDomain() = Withdrawal(id, amount, date)

/**
 * The stored balance after the calculator moved its Double from [before] (what it was given) to
 * [after]. Adds the change, rounded HALF_UP to cents, to the exact [stored] decimal, and returns
 * [stored] itself when nothing changed, so a deposit that earned nothing is never rewritten (ADR 0015).
 *
 * The subtraction is exact (Sterbenz lemma): one month's interest keeps [after] within a factor of
 * two of [before]. `valueOf` rounds the shortest decimal form of the change, not its binary expansion
 * (2.675 -> 2.68, where new BigDecimal(2.675) would give 2.67). `-0.0 == 0.0` on a primitive Double,
 * so a signed zero needs no case of its own.
 */
internal fun balanceAfterUpdate(stored: BigDecimal, before: Double, after: Double): BigDecimal {
    val change = after - before
    return if (change == 0.0) stored else stored + BigDecimal.valueOf(change).setScale(2, RoundingMode.HALF_UP)
}
