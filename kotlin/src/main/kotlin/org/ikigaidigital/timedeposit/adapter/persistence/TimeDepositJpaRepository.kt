package org.ikigaidigital.timedeposit.adapter.persistence

import jakarta.persistence.LockModeType
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.timedeposit.application.TimeDepositRepository
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The persistence adapter: Spring Data generates it, and the port's methods are Kotlin default
 * methods on top of the declared queries, so there is no hand-written adapter class (ADR 0010).
 * The Spring Data proxy runs JVM default methods itself; the annotation drops the `DefaultImpls`
 * bridges Kotlin would otherwise generate for Kotlin callers, which nothing here calls.
 */
@JvmDefaultWithoutCompatibility
interface TimeDepositJpaRepository : Repository<TimeDepositEntity, Int>, TimeDepositRepository {

    /** One query: Hibernate keeps one root per deposit, and `@OrderBy` sorts the fetched withdrawals. */
    @Query("select d from TimeDepositEntity d left join fetch d.withdrawals order by d.id")
    fun fetchAllWithWithdrawals(): List<TimeDepositEntity>

    override fun loadAllWithWithdrawals(): List<TimeDepositWithWithdrawals> =
        fetchAllWithWithdrawals().map { entity ->
            TimeDepositWithWithdrawals(entity.toDomain(), entity.withdrawals.map { it.toDomain() })
        }

    /**
     * `select … for no key update` (Hibernate 7.4's PESSIMISTIC_WRITE on PostgreSQL): a concurrent
     * balance update waits until this transaction ends, so no monthly run reads a balance another run
     * is about to overwrite (ADR 0008, ADR 0010).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TimeDepositEntity d order by d.id")
    fun lockAll(): List<TimeDepositEntity>

    override fun loadAllForUpdate(): List<TimeDeposit> = lockAll().map { it.toDomain() }

    /** Served from the persistence context when [loadAllForUpdate] ran in the same transaction. */
    fun findById(id: Int): TimeDepositEntity?

    /**
     * Updates the managed entities; Hibernate flushes the dirty balances when the transaction
     * commits. An id that doesn't exist is a caller bug (the deposits come from [loadAllForUpdate]),
     * so it fails instead of being skipped silently.
     */
    override fun saveBalances(deposits: List<TimeDeposit>) {
        deposits.forEach { deposit ->
            val entity = requireNotNull(findById(deposit.id)) { "No time deposit with id ${deposit.id}" }
            // valueOf rounds the shortest decimal form of the Double, not its binary expansion
            // (2.675 -> 2.68, where new BigDecimal(2.675) would give 2.67).
            entity.balance = BigDecimal.valueOf(deposit.balance).setScale(2, RoundingMode.HALF_UP)
        }
    }
}

private fun TimeDepositEntity.toDomain() = TimeDeposit(id, planType, balance.toDouble(), days)

private fun WithdrawalEntity.toDomain() = Withdrawal(id, amount, date)
