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
package org.hawkular.inventory.api;

import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Tenant;

/**
 * This is a wrapper class to hold various interfaces defining available functionality on tenants.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Tenants {

    private Tenants() {

    }

    private interface BrowserBase<AccessResourceTypes, AccessMetricTypes, AccessEnvs, AccessFeeds> {
        /**
         * @return resources types in the tenant(s)
         */
        AccessResourceTypes feedlessResourceTypes();

        /**
         * @return metric types in the tenant(s)
         */
        AccessMetricTypes feedlessMetricTypes();

        /**
         * @return environments in the tenant(s)
         */
        AccessEnvs environments();

        MetricTypes.Read allMetricTypes();

        ResourceTypes.Read allResourceTypes();

        AccessFeeds feeds();
    }

    /**
     * Interface for accessing a single tenant in a writable manner.
     */
    public interface Single extends ResolvableToSingleWithRelationships<Tenant, Tenant.Update>,
            BrowserBase<ResourceTypes.ReadWrite, MetricTypes.ReadWrite, Environments.ReadWrite, Feeds.ReadWrite> {
    }

    /**
     * Interface for traversing over a set of tenants.
     *
     * <p>Note that traversing over a set of entities enables only read-only access. If you need to use any of the
     * modification methods, you first need to resolve the traversal to a single entity (using the
     * {@link ReadInterface#get(Object)} method).
     */
    public interface Multiple extends ResolvableToManyWithRelationships<Tenant>,
            BrowserBase<ResourceTypes.ReadContained, MetricTypes.ReadContained, Environments.ReadContained,
                    Feeds.ReadContained> {
    }

    /**
     * Provides readonly access to tenants.
     */
    public interface ReadContained extends ReadInterface<Single, Multiple, String> {}

    public interface Read extends ReadInterface<Single, Multiple, Path> {}

    /**
     * Provides methods for read-write access to tenants.
     */
    public interface ReadWrite extends ReadWriteInterface<Tenant.Update, Tenant.Blueprint, Single, Multiple, String> {}
}
