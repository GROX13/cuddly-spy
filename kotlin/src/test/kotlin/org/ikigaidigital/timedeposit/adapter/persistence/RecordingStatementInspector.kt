package org.ikigaidigital.timedeposit.adapter.persistence

import org.hibernate.resource.jdbc.spi.StatementInspector
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records every SQL statement Hibernate prepares, so tests can count queries and read the SQL.
 * Registered through `hibernate.session_factory.statement_inspector`, which instantiates it by class
 * name, hence the shared list.
 */
class RecordingStatementInspector : StatementInspector {

    override fun inspect(sql: String): String = sql.also { statements += it }

    companion object {
        val statements: MutableList<String> = CopyOnWriteArrayList()
    }
}
