package io.dewey.server.common

import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ConcurrentUniqQueueTest {
    private fun p(of: String): Path = Path.of(of)

    private fun pathQueue() =
        ConcurrentUniqQueue<Path> { wider, narrower -> narrower != wider && narrower.startsWith(wider) }

    @Test
    fun `adding a parent drops the children already queued`() {
        val queue = pathQueue()
        queue.add(p("/lib/Author/Book/one.mp3"))
        queue.add(p("/lib/Author/Book/two.mp3"))

        queue.add(p("/lib/Author"))

        assertEquals(listOf(p("/lib/Author")), queue.drain())
    }

    @Test
    fun `a child under a queued parent keeps the parent as the entry`() {
        val queue = pathQueue()
        queue.add(p("/lib/Author"))

        queue.add(p("/lib/Author/Book/one.mp3"))

        assertEquals(listOf(p("/lib/Author")), queue.drain())
    }

    @Test
    fun `a child moves the parent covering it to the back`() {
        val queue = pathQueue()
        queue.add(p("/lib/A"))
        queue.add(p("/lib/B"))

        queue.add(p("/lib/A/Book/one.mp3"))

        assertEquals(listOf(p("/lib/B"), p("/lib/A")), queue.drain())
    }

    @Test
    fun `the widest path wins whatever order it arrives in`() {
        val queue = pathQueue()
        queue.add(p("/lib/A/Book/one.mp3"))
        queue.add(p("/lib/A"))
        queue.add(p("/lib/A/Book/two.mp3"))

        assertEquals(listOf(p("/lib/A")), queue.drain())
    }

    @Test
    fun `adding the same path twice leaves one entry`() {
        val queue = pathQueue()
        queue.add(p("/lib/Author/Book"))

        queue.add(p("/lib/Author/Book"))

        assertEquals(1, queue.size)
        assertEquals(listOf(p("/lib/Author/Book")), queue.drain())
    }

    @Test
    fun `re-adding a path moves it behind the entries queued since`() {
        val queue = pathQueue()
        queue.add(p("/lib/A"))
        queue.add(p("/lib/B"))

        queue.add(p("/lib/A"))

        assertEquals(listOf(p("/lib/B"), p("/lib/A")), queue.drain())
    }

    @Test
    fun `siblings coexist`() {
        val queue = pathQueue()
        queue.add(p("/lib/A/one.mp3"))
        queue.add(p("/lib/B/two.mp3"))

        assertEquals(listOf(p("/lib/A/one.mp3"), p("/lib/B/two.mp3")), queue.drain())
    }

    @Test
    fun `a name that merely starts with another is not a child`() {
        val queue = pathQueue()
        queue.add(p("/lib/Dune 2/one.mp3"))

        queue.add(p("/lib/Dune"))

        assertEquals(listOf(p("/lib/Dune 2/one.mp3"), p("/lib/Dune")), queue.drain())
    }

    @Test
    fun `a deeper parent only drops its own subtree`() {
        val queue = pathQueue()
        queue.add(p("/lib/A/Book/one.mp3"))
        queue.add(p("/lib/B/Book/two.mp3"))

        queue.add(p("/lib/A"))

        assertEquals(listOf(p("/lib/B/Book/two.mp3"), p("/lib/A")), queue.drain())
    }

    @Test
    fun `pop returns entries oldest first`() {
        val queue = pathQueue()
        queue.add(p("/lib/A"))
        queue.add(p("/lib/B"))

        assertEquals(p("/lib/A"), queue.pop())
        assertEquals(p("/lib/B"), queue.pop())
        assertEquals(null, queue.pop())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `drain empties the queue`() {
        val queue = pathQueue()
        queue.add(p("/lib/A"))

        queue.drain()

        assertTrue(queue.isEmpty())
        assertEquals(emptyList(), queue.drain())
    }

    @Test
    fun `concurrent adds keep the no-containment invariant and lose nothing`() {
        val queue = pathQueue()
        val threads = 8
        val perThread = 500
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)

        repeat(threads) { t ->
            pool.submit {
                start.await()
                repeat(perThread) { i -> queue.add(p("/lib/t$t/book$i/file.mp3")) }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

        val drained = queue.drain()
        assertEquals(threads * perThread, drained.size, "every distinct path must survive")
        assertEquals(drained.size, drained.toSet().size, "no duplicates")
        for (a in drained) {
            for (b in drained) {
                if (a !== b) assertFalse(a.startsWith(b), "$a is below $b")
            }
        }
    }

    @Test
    fun `overlapping concurrent adds never leave a nested pair`() {
        val queue = pathQueue()
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)

        repeat(3) { t ->
            pool.submit {
                start.await()
                repeat(500) { i -> queue.add(p("/lib/book$i/file$t.mp3")) }
            }
        }
        pool.submit {
            start.await()
            repeat(200) { queue.add(p("/lib")) }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

        val drained = queue.drain()
        assertTrue(drained.isNotEmpty())
        for (entry in drained) {
            for (other in drained) {
                if (entry !== other) assertFalse(entry.startsWith(other), "$entry is below $other")
            }
        }
    }

    @Test
    fun `poll blocks instead of spinning while the queue is empty`() {
        val queue = pathQueue()
        val taken = AtomicReference<Path?>()
        val consumer = Thread { while (taken.get() == null) taken.set(queue.poll()) }.also { it.start() }

        // A spinning consumer would sit in RUNNABLE; a correctly parked one reports TIMED_WAITING
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (consumer.state != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.sleep(5)
        assertEquals(Thread.State.TIMED_WAITING, consumer.state)
        assertEquals(null, taken.get())

        queue.add(p("/lib/A"))
        consumer.join(5_000)

        assertEquals(p("/lib/A"), taken.get())
    }

    @Test
    fun `a pool of consumers drains everything exactly once and then exits`() {
        val queue = pathQueue()
        val consumed = ConcurrentLinkedQueue<Path>()
        // Filled before the first consumer starts: a gap in production is indistinguishable from a dry queue,
        // and a consumer that hits one retires and leaves the rest for whoever is still running
        val produced = (0 until 300).map { p("/lib/book$it/file.mp3") }
        produced.forEach { queue.add(it) }

        val workers =
            List(4) {
                Thread {
                    while (true) consumed.add(queue.poll() ?: break)
                }.also { it.start() }
            }

        // Nothing signals the end, so the consumers retire on poll's own timeout
        workers.forEach { it.join(10_000) }

        assertTrue(workers.none { it.isAlive }, "every consumer must exit once the queue runs dry")
        assertEquals(produced.toSet(), consumed.toSet())
        assertEquals(produced.size, consumed.size, "no entry may be handed out twice")
    }

    @Test
    fun `a predicate that covers nothing still dedupes and moves to the back`() {
        val queue = ConcurrentUniqQueue<Path> { _, _ -> false }
        queue.add(p("/lib/A/one.mp3"))
        queue.add(p("/lib/A/two.mp3"))

        queue.add(p("/lib/A/one.mp3"))

        assertEquals(listOf(p("/lib/A/two.mp3"), p("/lib/A/one.mp3")), queue.drain())
    }

    @Test
    fun `a predicate that covers nothing keeps children alongside their parent`() {
        val queue = ConcurrentUniqQueue<Path> { _, _ -> false }
        queue.add(p("/lib/A/one.mp3"))

        queue.add(p("/lib/A"))

        assertEquals(listOf(p("/lib/A/one.mp3"), p("/lib/A")), queue.drain())
    }

    @Test
    fun `an entry is not handed out before its time`() {
        val clock = TestTimeSource()
        val queue = ConcurrentUniqQueue<Path>(clock)
        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.seconds)

        assertEquals(null, queue.pop())
        assertEquals(1, queue.size, "a waiting entry is still queued")

        clock += 1.seconds
        assertEquals(p("/lib/A/one.mp3"), queue.pop())
    }

    @Test
    fun `a due entry is handed out past one that is still waiting`() {
        val queue = ConcurrentUniqQueue<Path>(TestTimeSource())
        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.seconds)
        queue.add(p("/lib/B/two.mp3"))

        assertEquals(p("/lib/B/two.mp3"), queue.pop())
        assertEquals(null, queue.pop())
    }

    @Test
    fun `re-adding a waiting entry keeps its earlier time and one entry`() {
        val clock = TestTimeSource()
        val queue = ConcurrentUniqQueue<Path>(clock)
        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.seconds)
        clock += 500.milliseconds

        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.seconds)

        assertEquals(1, queue.size)
        assertEquals(null, queue.pop())
        clock += 500.milliseconds
        assertEquals(p("/lib/A/one.mp3"), queue.pop(), "a later add must not postpone the entry")
    }

    @Test
    fun `re-adding a waiting entry with an earlier time makes it due sooner`() {
        val queue = ConcurrentUniqQueue<Path>(TestTimeSource())
        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.seconds)

        queue.add(p("/lib/A/one.mp3"))

        assertEquals(p("/lib/A/one.mp3"), queue.pop())
    }

    @Test
    fun `a covering folder absorbs waiting files and takes the earliest time`() {
        val clock = TestTimeSource()
        val queue =
            ConcurrentUniqQueue<Path>(clock) { wider, narrower -> narrower != wider && narrower.startsWith(wider) }
        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.seconds)
        queue.add(p("/lib/A/two.mp3"), queue.now() + 2.seconds)

        queue.add(p("/lib/A"), queue.now() + 3.seconds)

        assertEquals(1, queue.size)
        assertEquals(null, queue.pop())
        clock += 1.seconds
        assertEquals(p("/lib/A"), queue.pop())
    }

    @Test
    fun `a waiting file under a queued folder does not postpone it`() {
        val queue =
            ConcurrentUniqQueue<Path>(TestTimeSource()) { wider, narrower ->
                narrower != wider && narrower.startsWith(wider)
            }
        queue.add(p("/lib/A"))

        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.seconds)

        assertEquals(p("/lib/A"), queue.pop())
    }

    @Test
    fun `poll waits instead of handing out an entry that is not due`() {
        val queue = pathQueue()
        queue.add(p("/lib/A/one.mp3"), queue.now() + 1.hours)
        val taken = AtomicReference<Path?>()
        val consumer = Thread { while (taken.get() == null) taken.set(queue.poll()) }.also { it.start() }

        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (consumer.state != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.sleep(5)
        assertEquals(Thread.State.TIMED_WAITING, consumer.state)
        assertEquals(null, taken.get())

        queue.add(p("/lib/B/two.mp3"))
        consumer.join(5_000)

        assertEquals(p("/lib/B/two.mp3"), taken.get())
    }

    @Test
    fun `removeAll evicts only the matching entries`() {
        val queue = pathQueue()
        queue.add(p("/lib/A/one.mp3"))
        queue.add(p("/lib/B/two.mp3"))
        queue.add(p("/lib/A/sub/three.mp3"))

        queue.removeAll { it.startsWith(p("/lib/A")) }

        assertEquals(listOf(p("/lib/B/two.mp3")), queue.drain())
    }
}
