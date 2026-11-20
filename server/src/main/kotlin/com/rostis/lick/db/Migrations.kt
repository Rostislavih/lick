package com.rostis.lick.db

import org.flywaydb.core.Flyway
import javax.sql.DataSource

/** Накатывает недостающие файлы из resources/db/migration (раздел 7.3). */
object Migrations {
    /** @return сколько миграций применено при этом запуске. */
    fun run(dataSource: DataSource): Int =
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
            .migrationsExecuted
}
