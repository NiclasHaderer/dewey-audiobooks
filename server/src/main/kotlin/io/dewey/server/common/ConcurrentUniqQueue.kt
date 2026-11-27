package io.dewey.server.common

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class ConcurrentUniqQueue<T>(
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val covers: (wider: T, narrower: T) -> Boolean = { _, _ -> false },
) {
    private class Entry<T>(
        val item: T,
        val notBefore: ComparableTimeMark,
    )

    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val entries = ArrayDeque<Entry<T>>()

    val size: Int
        get() = lock.withLock { entries.size }

    fun isEmpty(): Boolean = lock.withLock { entries.isEmpty() }

    fun now(): ComparableTimeMark = timeSource.markNow()

    fun add(
        item: T,
        notBefore: ComparableTimeMark = now(),
    ) {
        lock.withLock {
            // Only the wider entry may evict the narrower one: it covers every descendant, while a
            // descendant would drop the rest of the wider entry's subtree.
            // The merged entry keeps the earliest time, so an item that keeps being added is not postponed forever.
            val covering = entries.firstOrNull { covers(it.item, item) }
            if (covering != null) {
                entries.remove(covering)
                entries.addLast(Entry(covering.item, minOf(covering.notBefore, notBefore)))
            } else {
                val absorbed = entries.filter { it.item == item || covers(item, it.item) }
                entries.removeAll(absorbed)
                entries.addLast(Entry(item, absorbed.fold(notBefore) { earliest, it -> minOf(earliest, it.notBefore) }))
            }
            notEmpty.signalAll()
        }
    }

    fun pop(): T? = lock.withLock { takeDue() }

    fun poll(): T? =
        lock.withLock {
            var remaining = 10.milliseconds.inWholeNanoseconds
            while (remaining > 0) {
                takeDue()?.let { return@withLock it }
                val untilDue = entries.minOfOrNull { -it.notBefore.elapsedNow() }
                val wait = (untilDue?.inWholeNanoseconds ?: remaining).coerceIn(1, remaining)
                remaining -= wait - notEmpty.awaitNanos(wait)
            }
            takeDue()
        }

    fun removeAll(predicate: (T) -> Boolean) = lock.withLock { entries.removeAll { predicate(it.item) } }

    fun drain(): List<T> = lock.withLock { entries.map { it.item }.also { entries.clear() } }

    private fun takeDue(): T? {
        val due = entries.firstOrNull { it.notBefore.hasPassedNow() } ?: return null
        entries.remove(due)
        return due.item
    }
}
