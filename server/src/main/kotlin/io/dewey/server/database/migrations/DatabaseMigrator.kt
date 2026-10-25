package io.dewey.server.database.migrations

import io.github.classgraph.ClassGraph
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

private data class DatabaseVersion(
    val version: Int,
    val name: String,
    private val migration: Migration,
) {
    private val log = logger {}

    override fun toString(): String = "Version $version '$name'"

    fun migrate() {
        try {
            transaction {
                log.info { "Applying migration ${this@DatabaseVersion}" }
                migration.migrate()
                SchemaTrackerTable.insert {
                    it[appliedAt] = Clock.System.now()
                    it[version] = this@DatabaseVersion.version
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Error while applying migration ${this@DatabaseVersion}" }
            throw e
        }
    }
}

class DatabaseMigrator {
    private val log = logger {}
    private val classNameMatcher = "(\\d+)_(.*)".toRegex()
    private val packageName: String = "io.dewey.server.database.migrations.history"

    private val databaseVersions: List<DatabaseVersion> by lazy {
        val versions =
            ClassGraph()
                .acceptPackages(packageName)
                .enableClassInfo()
                .scan()
                .use { scan ->
                    scan.getSubclasses(Migration::class.java).loadClasses(Migration::class.java)
                }.map {
                    val versionMatch =
                        classNameMatcher.matchEntire(it.simpleName)
                            ?: error("Migration class ${it.name} does not match the '<version>_<name>' pattern")
                    val version = versionMatch.groupValues[1].toInt()
                    val name = versionMatch.groupValues[2]
                    DatabaseVersion(version, name, it.getDeclaredConstructor().newInstance())
                }.sortedBy { it.version }

        val duplicates = versions.groupBy { it.version }.filterValues { it.size > 1 }.keys
        check(duplicates.isEmpty()) { "Duplicate migration versions: $duplicates" }
        check(versions.isNotEmpty()) { "No migrations found in package $packageName" }
        versions
    }

    private val latestAppliedVersion by lazy {
        transaction {
            SchemaTrackerTable
                .selectAll()
                .orderBy(SchemaTrackerTable.version to SortOrder.DESC)
                .firstOrNull()
                ?.get(SchemaTrackerTable.version) ?: -1
        }
    }

    fun migrateDatabase() {
        transaction { SchemaUtils.create(SchemaTrackerTable) }
        migrateTo(latestAppliedVersion)
    }

    private fun migrateTo(latestDbVersion: Int) {
        val latestMigrationVersion = databaseVersions.last().version
        if (latestDbVersion > latestMigrationVersion) {
            log.error {
                "Database version $latestDbVersion is newer than the latest known migration $latestMigrationVersion"
            }
            throw IllegalStateException(
                "The database is at version $latestDbVersion, but this Dewey build only knows migrations up to " +
                    "$latestMigrationVersion. It was probably used by a newer Dewey version. " +
                    "Downgrading is not supported, refusing to start.",
            )
        }

        if (latestDbVersion == latestMigrationVersion) {
            log.info { "Database is up to date" }
            return
        }

        databaseVersions.forEach {
            if (it.version <= latestDbVersion) {
                log.info { "Skipping $it, because it was already applied" }
            } else {
                it.migrate()
            }
        }
    }
}
