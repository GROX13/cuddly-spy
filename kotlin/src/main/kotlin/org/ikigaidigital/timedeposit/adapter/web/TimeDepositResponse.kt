package org.ikigaidigital.timedeposit.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import java.math.BigDecimal
import java.math.RoundingMode

@Schema(description = "A time deposit with its withdrawals")
data class TimeDepositResponse(
    @field:Schema(description = "Time deposit id", example = "2")
    val id: Int,
    @field:Schema(description = "Plan type code: basic, student or premium", example = "basic")
    val planType: String,
    @field:Schema(description = "Current balance, two decimals", example = "1500.00")
    val balance: BigDecimal,
    @field:Schema(description = "Days since the time deposit was opened", example = "400")
    val days: Int,
    @field:Schema(description = "Withdrawals ordered by date, then id; empty when there are none")
    val withdrawals: List<WithdrawalResponse>,
)

/**
 * The balance is a Double in the frozen `TimeDeposit`; it leaves with the same rounding as the
 * persistence write. `valueOf` rounds the shortest decimal form, so 2.675 gives 2.68, not 2.67.
 */
internal fun TimeDepositWithWithdrawals.toResponse() = TimeDepositResponse(
    id = deposit.id,
    planType = deposit.planType,
    balance = BigDecimal.valueOf(deposit.balance).setScale(2, RoundingMode.HALF_UP),
    days = deposit.days,
    withdrawals = withdrawals.map { it.toResponse() },
)
