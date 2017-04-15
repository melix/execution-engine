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
import org.gradle.execution.ResourceLock;
import org.gradle.execution.ResourcesUnderLock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultResourcesUnderLock implements ResourcesUnderLock {
    private final List<ResourceLock> lockedResources;

    public DefaultResourcesUnderLock(final ResourceLock... lockedResources) {
        this.lockedResources = Arrays.asList(lockedResources);
    }

    private DefaultResourcesUnderLock(final List<ResourceLock> resources) {
        this.lockedResources = resources;
    }

    @Override
    public void release(final ResourceLock resource, final Action<? super ResourcesUnderLock> action) {
        if (!lockedResources.contains(resource)) {
            throw new IllegalStateException("Cannot release a resource that you don't own");
        }
        resource.unlock();
        try {
            List<ResourceLock> locked = new ArrayList<>(lockedResources.size()-1);
            for (ResourceLock lockedResource : lockedResources) {
                if (!lockedResource.equals(resource)) {
                    locked.add(lockedResource);
                }
            }
            ResourcesUnderLock rul = new DefaultResourcesUnderLock(locked);
            action.execute(rul);
        } finally {
            while (!resource.tryLock()) {
                resource.await();
            }
        }
    }

    @Override
    public String toString() {
        return lockedResources.toString();
    }
}
