package io.dewey.server.schedules

import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.AuthorTable
import io.dewey.server.database.tables.BookTable
import io.dewey.server.database.tables.SeriesTable
import io.dewey.server.repositories.AuthorRepository
import io.dewey.server.repositories.BookRepository
import io.dewey.server.repositories.SeriesRepository
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

enum class MatchableEntity {
    BOOK,
    AUTHOR,
    SERIES,
}

data class AutoMatchRequest(
    val entity: MatchableEntity,
    val id: Uuid,
    val libraryId: Uuid,
)

private val SYSTEM_USER = Uuid.NIL

private const val WORKERS = 2

class AutoMatcher : KoinComponent {
    private val bookRepository: BookRepository by inject()
    private val authorRepository: AuthorRepository by inject()
    private val seriesRepository: SeriesRepository by inject()

    private val log = logger {}
    private val queue = LinkedBlockingQueue<AutoMatchRequest>()
    private val running = AtomicBoolean(false)
    private var workers: List<Thread> = emptyList()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        workers =
            (1..WORKERS).map { index ->
                Thread(::work, "auto-match-$index").also {
                    it.isDaemon = true
                    it.start()
                }
            }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        workers.forEach { it.interrupt() }
        workers.forEach { it.join(1000) }
        workers = emptyList()
        queue.clear()
    }

    // The workers read through their own connection, which cannot see the new row before the commit
    context(_: Transaction)
    fun matchOnCommit(request: AutoMatchRequest) {
        TransactionManager.current().registerInterceptor(
            object : StatementInterceptor {
                override fun afterCommit(transaction: Transaction) {
                    if (running.get()) queue.add(request)
                }
            },
        )
    }

    private fun work() {
        while (running.get()) {
            val request =
                try {
                    queue.poll(200, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    return
                } ?: continue
            try {
                match(request)
            } catch (throwable: Throwable) {
                log.error(throwable) { "Could not auto match $request" }
            }
        }
    }

    private fun match(request: AutoMatchRequest) {
        if (transaction { alreadyMatched(request) }) return
        try {
            when (request.entity) {
                MatchableEntity.BOOK -> bookRepository.autoMatch(SYSTEM_USER, request.id, request.libraryId)
                MatchableEntity.AUTHOR -> authorRepository.autoMatch(SYSTEM_USER, request.id, request.libraryId)
                MatchableEntity.SERIES -> seriesRepository.autoMatch(SYSTEM_USER, request.id, request.libraryId)
            }
        } catch (e: ErrorResponse) {
            if (e.status != HttpStatusCode.NotFound) throw e
            log.debug { "No match for ${request.entity} ${request.id}: ${e.error}" }
        }
    }

    context(_: Transaction)
    private fun alreadyMatched(request: AutoMatchRequest): Boolean {
        val (table, provider) =
            when (request.entity) {
                MatchableEntity.BOOK -> BookTable to BookTable.provider
                MatchableEntity.AUTHOR -> AuthorTable to AuthorTable.provider
                MatchableEntity.SERIES -> SeriesTable to SeriesTable.provider
            }
        return table
            .select(provider)
            .where { table.id eq request.id }
            .firstOrNull()
            ?.get(provider) != null
    }
}
