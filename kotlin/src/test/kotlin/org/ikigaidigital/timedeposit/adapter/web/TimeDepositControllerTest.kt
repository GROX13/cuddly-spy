package org.ikigaidigital.timedeposit.adapter.web

import org.assertj.core.api.Assertions.assertThat
import org.ikigaidigital.timedeposit.domain.TimeDeposit
import org.ikigaidigital.UseCaseConfiguration
import org.ikigaidigital.timedeposit.application.FakeTransactions
import org.ikigaidigital.timedeposit.application.InMemoryTimeDepositRepository
import org.ikigaidigital.timedeposit.domain.TimeDepositWithWithdrawals
import org.ikigaidigital.timedeposit.domain.Withdrawal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The web slice with the real use cases and their real wiring (`UseCaseConfiguration`) over an
 * in-memory port and a fake transaction boundary, so nothing is mocked. Bodies are compared as raw text to pin the number and date formats.
 */
@WebMvcTest(TimeDepositController::class)
@Import(UseCaseConfiguration::class, TimeDepositControllerTest.FakePorts::class)
class TimeDepositControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    class FakePorts {
        @Bean
        fun timeDepositRepository() = InMemoryTimeDepositRepository()

        @Bean
        fun transactions() = FakeTransactions()
    }

    @Autowired
    private lateinit var mvc: MockMvcTester

    @Autowired
    private lateinit var repository: InMemoryTimeDepositRepository

    @Test
    fun `should return time deposits with camelCase fields, two-decimal money and ISO dates`() {
        repository.deposits = listOf(
            TimeDepositWithWithdrawals(
                TimeDeposit(1, "basic", 1000.0, 400),
                listOf(
                    Withdrawal(11, BigDecimal("100.00"), LocalDate.of(2026, 3, 15)),
                    Withdrawal(10, BigDecimal("50.00"), LocalDate.of(2026, 6, 1)),
                ),
            ),
        )

        assertThat(mvc.get().uri("/time-deposits"))
            .hasStatusOk()
            .hasContentType(MediaType.APPLICATION_JSON)
            .bodyText().isEqualTo(
                """[{"id":1,"planType":"basic","balance":1000.00,"days":400,"withdrawals":[""" +
                    """{"id":11,"amount":100.00,"date":"2026-03-15"},""" +
                    """{"id":10,"amount":50.00,"date":"2026-06-01"}]}]"""
            )
    }

    /**
     * Balances are Doubles in the frozen contract and can carry binary noise; they leave as
     * `BigDecimal.valueOf(balance).setScale(2, HALF_UP)`, the same rule as the persistence write.
     * Checked independently in jshell: 2.675 rounds to 2.68 only from its shortest decimal form, and
     * 1.0E7 must not come out in exponent notation.
     */
    @Test
    fun `should round the balance half up to two decimals and keep empty withdrawals as an empty array`() {
        repository.deposits = listOf(
            TimeDepositWithWithdrawals(TimeDeposit(1, "premium", 10.850000000000001, 46), emptyList()),
            TimeDepositWithWithdrawals(TimeDeposit(2, "basic", 2.675, 31), emptyList()),
            TimeDepositWithWithdrawals(TimeDeposit(3, "student", 1.0E7, 200), emptyList()),
        )

        assertThat(mvc.get().uri("/time-deposits"))
            .hasStatusOk()
            .bodyText().isEqualTo(
                """[{"id":1,"planType":"premium","balance":10.85,"days":46,"withdrawals":[]},""" +
                    """{"id":2,"planType":"basic","balance":2.68,"days":31,"withdrawals":[]},""" +
                    """{"id":3,"planType":"student","balance":10000000.00,"days":200,"withdrawals":[]}]"""
            )
    }

    @Test
    fun `should return an empty array when there are no time deposits`() {
        repository.deposits = emptyList()

        assertThat(mvc.get().uri("/time-deposits")).hasStatusOk().bodyText().isEqualTo("[]")
    }

    /**
     * One month by the legacy rules (checked in jshell): basic 1000.00 at 400 days earns
     * 1000 * 0.01 / 12 = 0.8333… -> 0.83; premium at 40 days is before its eligible days.
     */
    @Test
    fun `should add one month of interest on a POST with no body and return every time deposit`() {
        repository.deposits = listOf(
            TimeDepositWithWithdrawals(
                TimeDeposit(1, "basic", 1000.0, 400),
                listOf(Withdrawal(11, BigDecimal("100.00"), LocalDate.of(2026, 3, 15))),
            ),
            TimeDepositWithWithdrawals(TimeDeposit(2, "premium", 5000.0, 40), emptyList()),
        )

        assertThat(mvc.post().uri("/time-deposits/balance-updates"))
            .hasStatusOk()
            .hasContentType(MediaType.APPLICATION_JSON)
            .bodyText().isEqualTo(
                """[{"id":1,"planType":"basic","balance":1000.83,"days":400,"withdrawals":[""" +
                    """{"id":11,"amount":100.00,"date":"2026-03-15"}]},""" +
                    """{"id":2,"planType":"premium","balance":5000.00,"days":40,"withdrawals":[]}]"""
            )
    }
}
