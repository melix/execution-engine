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

import org.gradle.execution.Action;
import org.gradle.execution.CoordinationService;
import org.gradle.execution.ResourceLock;
import org.gradle.execution.ResourcesUnderLock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DefaultCoordinationService implements CoordinationService {
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final Set<ResourceLock> resourcesInUse = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void withResourceLock(final ResourceLock lock, final Action<? super ResourcesUnderLock> action) {
        List<ResourceLock> lockedResources = new ArrayList<>();
        lock.visit(this::deadlockDetection);
        lock.visit(lockedResources::add);
        while (!lock.tryLock()) {
            lock.await();
        }
        resourcesInUse.add(lock);
        Future<?> future = executorService.submit(() -> {
            ResourcesUnderLock rul = new DefaultResourcesUnderLock(lockedResources.toArray(new ResourceLock[0]));
            action.execute(rul);
        });
        try {
            future.get();
        } catch (InterruptedException e) {
            Exceptions.sneakyThrow(e);
        } catch (ExecutionException e) {
            Exceptions.sneakyThrow(e.getCause());
        } finally {
            lockedResources.remove(lock);
            lock.unlock();
        }
    }

    private void deadlockDetection(final ResourceLock lock) {
        if (resourcesInUse.contains(lock) && lock.isLocked()) {
            throw new IllegalStateException("Deadlock detected: trying to lock a resource which is locked in outer scope.");
        }
    }
}
