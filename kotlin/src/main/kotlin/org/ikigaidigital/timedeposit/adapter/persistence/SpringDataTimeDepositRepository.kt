package org.ikigaidigital.timedeposit.adapter.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

/** Spring implements the entity queries and CRUD methods; the application uses the mapping adapter. */
interface SpringDataTimeDepositRepository : JpaRepository<TimeDepositEntity, Int> {

    @Query("select d from TimeDepositEntity d left join fetch d.withdrawals order by d.id")
    fun findAllWithWithdrawals(): List<TimeDepositEntity>

    /** Joins the application's transaction; rows remain locked until that transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TimeDepositEntity d order by d.id")
    fun findAllForUpdate(): List<TimeDepositEntity>
}
