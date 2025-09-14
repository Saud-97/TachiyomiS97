package eu.kanade.tachiyomi.data.database

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.util.Date

val dateAdapter =
    object : ColumnAdapter<Date, Long> {
        override fun decode(databaseValue: Long): Date = Date(databaseValue)

        override fun encode(value: Date): Long = value.time
    }

private const val listOfStringsSeparator = ", "
val listOfStringsAdapter =
    object : ColumnAdapter<List<String>, String> {
        override fun decode(databaseValue: String) =
            if (databaseValue.isEmpty()) {
                emptyList()
            } else {
                databaseValue.split(listOfStringsSeparator)
            }

        override fun encode(value: List<String>) = value.joinToString(separator = listOfStringsSeparator)
    }

val updateStrategyAdapter =
    object : ColumnAdapter<UpdateStrategy, Int> {
        private val enumValues by lazy { UpdateStrategy.entries }

        override fun decode(databaseValue: Int): UpdateStrategy = enumValues.getOrElse(databaseValue) { UpdateStrategy.ALWAYS_UPDATE }

        override fun encode(value: UpdateStrategy): Int = value.ordinal
    }

interface ColumnAdapter<T : Any, S> {
    /**
     * @return [databaseValue] decoded as type [T].
     */
    fun decode(databaseValue: S): T

    /**
     * @return [value] encoded as database type [S].
     */
    fun encode(value: T): S
}
