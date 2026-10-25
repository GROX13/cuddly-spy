package org.ikigaidigital.timedeposit.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import org.ikigaidigital.timedeposit.domain.Withdrawal
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "A withdrawal from a time deposit: history, already reflected in the balance")
data class WithdrawalResponse(
    @field:Schema(description = "Withdrawal id", example = "1")
    val id: Int,
    @field:Schema(description = "Amount withdrawn, two decimals", example = "100.00")
    val amount: BigDecimal,
    @field:Schema(description = "Date of the withdrawal (ISO-8601)", example = "2026-03-15")
    val date: LocalDate,
)

/** The amount is stored as NUMERIC(19, 2), so it already has two decimals (ADR 0002). */
internal fun Withdrawal.toResponse() = WithdrawalResponse(id, amount, date)
