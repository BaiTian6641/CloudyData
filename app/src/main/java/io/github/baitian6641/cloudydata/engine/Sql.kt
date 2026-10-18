package io.github.baitian6641.cloudydata.engine

object Sql {
    /** A trusted internal path/value as a SQL string literal. */
    fun string(value: String): String = "'" + value.replace("'", "''") + "'"

    /** An internal field identifier, never user-authored SQL. */
    fun identifier(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
