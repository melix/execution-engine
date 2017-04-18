package org.gradle.execution

import org.gradle.execution.internal.CompositeLock
import org.gradle.execution.internal.DefaultCoordinationService
import org.gradle.execution.internal.LockFree
import org.gradle.execution.internal.SemaphoreBasedResource
import org.gradle.execution.internal.SimpleResourceLock
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class DefaultCoordinationServiceTest extends Specification {
    def service = DefaultCoordinationService.withDynamicPoolSize()

    final AtomicInteger concurrent = new AtomicInteger()
    volatile int maxConcurrent

    private void measureConcurrentRequests(Closure cl) {
        concurrent.incrementAndGet()
        try {
            cl.call()
        } finally {
            synchronized (concurrent) {
                maxConcurrent = Math.max(maxConcurrent, concurrent.getAndDecrement())
            }
        }
    }

    void assertMaxConcurrentRequests(int max=1) {
        if (maxConcurrent>max) {
            throw new AssertionError("Expected a maximum number of concurrent requests of $max but was ${concurrent.get()}")
        }
        println "Max concurrent requests: ${maxConcurrent}"
    }

    def "can lock resource"() {
        def resource = new SimpleResourceLock()
        when:
        async {
            start {
                service.withResourceLock(resource) {
                    measureConcurrentRequests {
                        println 'Use lock from 1'
                        sleep 200
                    }
                }
            }
            start {
                service.withResourceLock(resource) {
                    measureConcurrentRequests {
                        println 'Use lock from 2'
                        sleep 200
                        concurrent.decrementAndGet()
                    }
                }
            }
        }

        then:
        noExceptionThrown()
        assertMaxConcurrentRequests()
    }

    def "can acquire a composite lock"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()
        def composite = new CompositeLock(
                lock1,
                lock2
        )

        when:
        async {
            start {
                service.withResourceLock(composite) {
                    measureConcurrentRequests { sleep 200 }
                }
            }
            start {
                service.withResourceLock(composite) {
                    measureConcurrentRequests { sleep 200 }
                }
            }
        }

        then:
        noExceptionThrown()
        assertMaxConcurrentRequests()
    }

    def "can acquire a lock-free lease"() {
        def resource = new LockFree()
        when:
        async {
            5.times { id ->
                start {
                    service.withResourceLock(resource) {
                        measureConcurrentRequests {
                            println "Use lock from $id"
                            sleep 1000
                        }
                    }
                }
            }
        }

        then:
        noExceptionThrown()
        assertMaxConcurrentRequests(5)
    }

    def "can have a max number of workers using a semaphore lock"() {
        def resource = new SemaphoreBasedResource(3)
        when:
        async {
            8.times { id ->
                start {
                    service.withResourceLock(resource) {
                        measureConcurrentRequests {
                            println "Use lock from $id"
                            sleep 1000
                        }
                    }
                }
            }
        }

        then:
        noExceptionThrown()
        assertMaxConcurrentRequests(3)
    }

    def "can release workers using a semaphore lock"() {
        def resource = new SemaphoreBasedResource(3)
        when:
        async {
            8.times { id ->
                start {
                    service.withResourceLock(resource) { lease ->
                        measureConcurrentRequests {
                            println "Use lock from $id"
                            lease.release(resource) {
                                sleep 1000
                            }
                            sleep 1000
                        }
                    }
                }
            }
        }

        then:
        noExceptionThrown()
        assertMaxConcurrentRequests(8)
    }



    def "cannot release a lock we don't own"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()
        when:
        async {
            start {
                service.withResourceLock(lock1) {
                    it.release(lock2) {
                        sleep 200
                    }
                }
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot release a resource that you don\'t own'
    }

    def "a different thread can use a released lock in a composite"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()

        Instant instant1, instant2, instant3

        when:
        async {
            start {
                println 'Start 1'
                service.withResourceLock(new CompositeLock(lock1, lock2)) {
                    println "Start composite"
                    instant1 = instant()
                    it.release(lock1) {
                        println "Released lock 1"
                        sleep 500
                        println "Re-acquire lock 1"
                    }
                    instant2 = instant()
                    sleep 50
                    println "Finish composite"
                }
            }
            start {
                println 'Start 2'
                service.withResourceLock(lock1) {
                    instant3 = instant()
                    println 'use lock 1'

                }
            }
        }

        then:
        noExceptionThrown()
        instant1.before instant2
        instant2.after instant3
    }

    @Ignore("Implementation needs rework")
    def "can detect a dead lock"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()

        when:
        async {
            start {
                println 'Start 1'
                service.withResourceLock(lock1) {
                    service.withResourceLock(lock2) {
                        service.withResourceLock(lock1) {

                        }
                    }
                }
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Deadlock detected: trying to lock a resource which is locked in outer scope.'
    }

    def "doesn't detect a dead lock when resource is actually freed"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()

        when:
        async {
            start {
                println 'Start 1'
                service.withResourceLock(lock1) {
                    it.release(lock1) {
                        service.withResourceLock(lock2) {
                            service.withResourceLock(lock1) {
                                sleep 200
                            }
                            sleep 200
                        }
                        sleep 200
                    }
                    sleep 200
                }
            }
        }

        then:
        noExceptionThrown()
    }

    def "can coordinate composite locks"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()
        def lock3 = new SimpleResourceLock()

        when:
        async {
            start {
                service.withResourceLock(new CompositeLock(lock1, lock2)) {
                    measureConcurrentRequests {
                        println "Using lock1 and lock2"
                        sleep 200
                    }
                }
            }
            start {
                service.withResourceLock(new CompositeLock(lock2, lock3)) {
                    measureConcurrentRequests {
                        println "Using lock2 and lock3"
                        sleep 200
                    }
                }
            }
            start {
                service.withResourceLock(new CompositeLock(lock1, lock3)) {
                    measureConcurrentRequests {
                        println "Using lock1 and lock 3"
                        sleep 200
                    }
                }
            }
        }

        then:
        noExceptionThrown()
        assertMaxConcurrentRequests()
    }

    public Instant instant() {
        new Instant()
    }

    private final static void async(@DelegatesTo(AsyncWork) Closure cl) {
        def async = new AsyncWork()
        cl.delegate = async
        cl()
        async.join()
    }

    private static class AsyncWork {
        final List<Thread> threads = new ArrayList<>()
        volatile Throwable failure

        public void start(Runnable run) {
            threads << Thread.start {
                try {
                    run.run()
                } catch (e) {
                    failure = e
                }
            }
            sleep 100
        }

        public void join() {
            threads*.join()
            if (failure) {
                throw failure
            }
        }
    }

    private static class Instant {
        private final static AtomicLong COUNTER = new AtomicLong()

        private final int instant = COUNTER.getAndIncrement()

        public boolean after(Instant o) {
            instant > o.instant
        }

        public boolean before(Instant o) {
            instant < o.instant
        }
    }
}