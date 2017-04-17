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

import org.gradle.execution.ResourceLock;
import org.gradle.execution.ResourceLockVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompositeLock implements ResourceLock, ResourceLock.LockListener {
    private final ResourceLock[] locks;
    private final Object condition = new Object();
    private Set<LockListener> listeners = Collections.synchronizedSet(new HashSet<>());

    public CompositeLock(ResourceLock... locks) {
        this.locks = locks;
    }


    @Override
    public synchronized boolean tryLock() {
        List<ResourceLock> locked = new ArrayList<>();
        for (ResourceLock lock : locks) {
            if (lock.tryLock()) {
                locked.add(lock);
            } else {
                for (ResourceLock resourceLock : locked) {
                    resourceLock.unlock();
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public synchronized boolean isLocked() {
        for (ResourceLock lock : locks) {
            if (lock.isLocked()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void unlock() {
        for (ResourceLock lock : locks) {
            lock.unlock();
        }
        synchronized (condition) {
            condition.notifyAll();
        }
        for (LockListener listener : listeners) {
            listener.onUnlock(this);
        }
    }

    @Override
    public void await() {
        synchronized (condition) {
            try {
                for (ResourceLock lock : locks) {
                    lock.addListener(this);
                }
                condition.wait();
            } catch (InterruptedException e) {
                Exceptions.sneakyThrow(e);
            } finally {
                for (ResourceLock lock : locks) {
                    lock.removeListener(this);
                }
            }
        }
    }

    @Override
    public void visit(final ResourceLockVisitor visitor) {
        visitor.visit(this);
        for (ResourceLock lock : locks) {
            lock.visit(visitor);
        }
    }

    @Override
    public void addListener(final LockListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(final LockListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onUnlock(final ResourceLock unlockedResource) {
        synchronized (condition) {
            condition.notifyAll();
        }
    }
}
