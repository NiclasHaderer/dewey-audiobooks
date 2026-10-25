package io.dewey.server.events

import io.dewey.models.ChangeEvent
import io.dewey.models.ChangeType
import io.dewey.models.ChangedEntity
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.mp.KoinPlatform
import kotlin.uuid.Uuid

private val recordedKey = Key<RecordedChanges>()

context(_: Transaction)
fun recordChange(
    entity: ChangedEntity,
    type: ChangeType,
    id: Uuid,
    libraryId: Uuid,
) {
    val transaction = TransactionManager.current()
    transaction
        .getOrCreate(recordedKey) { RecordedChanges().also(transaction::registerInterceptor) }
        .add(ChangeEvent(entity, type, id, libraryId))
}

private class RecordedChanges : StatementInterceptor {
    private val changes = LinkedHashMap<Pair<ChangedEntity, Uuid>, ChangeEvent>()

    fun add(event: ChangeEvent) {
        val key = event.entity to event.id
        val merged = merge(changes[key]?.type, event.type)
        if (merged == null) {
            changes.remove(key)
        } else {
            changes[key] = event.copy(type = merged)
        }
    }

    private fun merge(
        previous: ChangeType?,
        next: ChangeType,
    ): ChangeType? =
        when (previous) {
            ChangeType.CREATED if next == ChangeType.DELETED -> null
            ChangeType.CREATED -> ChangeType.CREATED
            ChangeType.DELETED if next == ChangeType.CREATED -> ChangeType.UPDATED
            else -> next
        }

    override fun afterCommit(transaction: Transaction) {
        if (changes.isNotEmpty()) KoinPlatform.getKoin().get<ChangeEvents>().publish(changes.values.toList())
    }
}
