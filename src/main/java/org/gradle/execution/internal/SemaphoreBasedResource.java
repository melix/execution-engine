/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.execution.internal;

import java.util.concurrent.Semaphore;

public class SemaphoreBasedResource extends AbstractResourceLock {
    private final Semaphore lock;
    private final Object condition = new Object();

    public SemaphoreBasedResource(int permits) {
        lock = new Semaphore(permits);
    }

    @Override
    public boolean tryLock() {
        return lock.tryAcquire();
    }

    @Override
    public boolean isLocked() {
        return lock.availablePermits()==0;
    }

    @Override
    public void unlock() {
        lock.release();
        synchronized (condition) {
            condition.notifyAll();
            super.unlock();
        }
    }

    @Override
    public void await() {
        synchronized (condition) {
            try {
                condition.wait();
            } catch (InterruptedException e) {
                Exceptions.sneakyThrow(e);
            }
        }
    }

    @Override
    public String toString() {
        return lock.toString();
    }
}
