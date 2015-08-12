/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.bus.api;

import org.hawkular.inventory.api.Action;
import org.hawkular.inventory.api.model.Resource;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class ResourceEvent extends InventoryEvent<Resource> {

    private Resource object;

    public ResourceEvent() {
    }

    public ResourceEvent(Action.Enumerated action, Resource object) {
        super(action);
        this.object = object;
    }

    @Override
    public Resource getObject() {
        return object;
    }

    @Override
    public void setObject(Resource object) {
        this.object = object;
    }
}