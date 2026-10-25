package org.ikigaidigital.timedeposit.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import org.ikigaidigital.timedeposit.domain.TimeDepositStatement
import java.math.BigDecimal

@Schema(description = "A time deposit with its withdrawals")
data class TimeDepositResponse(
    @field:Schema(description = "Time deposit id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    val id: Int,
    @field:Schema(
        description = "Plan type code: basic, student or premium", example = "basic",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val planType: String,
    @field:Schema(
        description = "Current balance, two decimals", example = "1500.00",
        requiredMode = Schema.RequiredMode.REQUIRED,
        type = "number", types = ["number"], multipleOf = 0.01,
    )
    val balance: BigDecimal,
    @field:Schema(
        description = "Days since the time deposit was opened", example = "400",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val days: Int,
    @field:Schema(
        description = "Withdrawals ordered by date, then id; empty when there are none",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val withdrawals: List<WithdrawalResponse>,
)

/**
 * The balance is the stored NUMERIC(19,2), so it already has two decimals and is sent as is; it
 * never passes through the frozen `TimeDeposit`'s Double (ADR 0002, ADR 0015).
 */
internal fun TimeDepositStatement.toResponse() = TimeDepositResponse(
    id = id,
    planType = planType,
    balance = balance,
    days = days,
    withdrawals = withdrawals.map { it.toResponse() },
)
