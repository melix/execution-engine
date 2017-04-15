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
import java.util.List;

public class CompositeLock implements ResourceLock {
    private final ResourceLock[] locks;
    private final Object condition = new Object();

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
    public void visit(final ResourceLockVisitor visitor) {
        visitor.visit(this);
        for (ResourceLock lock : locks) {
            lock.visit(visitor);
        }
    }
}
