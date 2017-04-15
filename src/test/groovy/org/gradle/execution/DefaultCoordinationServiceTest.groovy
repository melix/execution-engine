package org.gradle.execution

import org.gradle.execution.internal.CompositeLock
import org.gradle.execution.internal.DefaultCoordinationService
import org.gradle.execution.internal.SimpleResourceLock
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class DefaultCoordinationServiceTest extends Specification {
    def service = new DefaultCoordinationService()

    def "can lock resource"() {
        def resource = new SimpleResourceLock()
        Instant instant1, instant2
        when:
        async {
            start {
                service.withResourceLock(resource) {
                    instant1 = instant()
                }
            }
            start {
                service.withResourceLock(resource) {
                    instant2 = instant()
                }
            }
        }

        then:
        noExceptionThrown()
        instant2.after instant1
    }

    def "can acquire a composite lock"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()
        def composite = new CompositeLock(
                lock1,
                lock2
        )
        Instant instant1, instant2
        when:
        async {
            start {
                service.withResourceLock(composite) {
                    instant1 = instant()
                }
            }
            start {
                service.withResourceLock(composite) {
                    instant2 = instant()
                }
            }
        }

        then:
        noExceptionThrown()
        instant2.after instant1
    }

    def "cannot release a lock we don't own"() {
        def lock1 = new SimpleResourceLock()
        def lock2 = new SimpleResourceLock()
        when:
        async {
            start {
                service.withResourceLock(lock1) {
                    it.release(lock2) {

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

                            }
                        }
                    }
                }
            }
        }

        then:
        noExceptionThrown()
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
            sleep 100
            threads << Thread.start {
                try {
                    run.run()
                } catch (e) {
                    failure = e
                }
            }
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