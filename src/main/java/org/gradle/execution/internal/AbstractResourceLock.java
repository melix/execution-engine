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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractResourceLock implements ResourceLock {
    private Set<LockListener> listeners = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void unlock() {
        for (LockListener listener : listeners) {
            listener.onUnlock(this);
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
    public void visit(final ResourceLockVisitor visitor) {
        visitor.visit(this);
    }

}
