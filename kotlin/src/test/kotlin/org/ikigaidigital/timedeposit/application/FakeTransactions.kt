package org.ikigaidigital.timedeposit.application

/**
 * A fake transaction boundary: runs the block directly and records `begin` and `commit` (or
 * `rollback` when the block throws) in [events], so a test can see which port calls ran inside it.
 */
class FakeTransactions(val events: MutableList<String> = mutableListOf()) : Transactions {

    override fun <T> inTransaction(block: () -> T): T {
        events += "begin"
        return try {
            block().also { events += "commit" }
        } catch (e: RuntimeException) {
            events += "rollback"
            throw e
        }
    }
}
