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
package org.hawkular.inventory.lazy;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.lazy.spi.LazyInventoryBackend;
import org.hawkular.inventory.lazy.spi.SwitchElementType;

import static org.hawkular.inventory.api.filters.With.type;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
final class TraversalContext<BE, E extends AbstractElement<?, ?>> {
    protected final LazyInventory<BE> inventory;
    protected final QueryFragmentTree sourcePath;
    protected final QueryFragmentTree selectCandidates;
    protected final LazyInventoryBackend<BE> backend;
    protected final Class<E> entityClass;
    protected final Configuration configuration;

    TraversalContext(LazyInventory<BE> inventory, QueryFragmentTree sourcePath, QueryFragmentTree selectCandidates,
            LazyInventoryBackend<BE> backend,
            Class<E> entityClass, Configuration configuration) {
        this.inventory = inventory;
        this.sourcePath = sourcePath;
        this.selectCandidates = selectCandidates;
        this.backend = backend;
        this.entityClass = entityClass;
        this.configuration = configuration;
    }

    Builder<BE, E> proceed() {
        return new Builder<>(inventory, hop(), QueryFragmentTree.filter(), backend, entityClass, configuration);
    }

    <T extends Entity<?, ?>> Builder<BE, T> proceedTo(Relationships.WellKnown over, Class<T> entityType) {
        return new Builder<>(inventory, hop(), QueryFragmentTree.filter(), backend, entityType, configuration)
                .where(Related.by(over), type(entityType));
    }

    Builder<BE, Relationship> proceedToRelationships(Relationships.Direction direction) {
        return new Builder<>(inventory, hop(), QueryFragmentTree.filter()
                .with(new SwitchElementType(direction, false)), backend, Relationship.class, configuration);
    }

    <T extends Entity<?, ?>> Builder<BE, T> proceedFromRelationshipsTo(Relationships.Direction direction,
            Class<T> entityType) {
        return new Builder<>(inventory, hop().with(new SwitchElementType(direction, true)), QueryFragmentTree.filter(),
                backend, entityType, configuration).where(type(entityType));
    }

    QueryFragmentTree.SymmetricExtender select() {
        return sourcePath.extend().filter().with(selectCandidates);
    }

    QueryFragmentTree.SymmetricExtender hop() {
        return sourcePath.extend().path().with(selectCandidates);
    }

    TraversalContext<BE, E> replacePath(QueryFragmentTree path) {
        return new TraversalContext<>(inventory, path, QueryFragmentTree.empty(), backend, entityClass, configuration);
    }

    public static final class Builder<BE, E extends AbstractElement<?, ?>> {
        private final LazyInventory<BE> inventory;
        private final QueryFragmentTree.SymmetricExtender pathExtender;
        private final QueryFragmentTree.SymmetricExtender selectExtender;
        private final LazyInventoryBackend<BE> backend;
        private final Class<E> entityClass;
        private final Configuration configuration;

        public Builder(LazyInventory<BE> inventory, QueryFragmentTree.SymmetricExtender pathExtender,
                QueryFragmentTree.SymmetricExtender selectExtender, LazyInventoryBackend<BE> backend,
                Class<E> entityClass, Configuration configuration) {
            this.inventory = inventory;
            this.pathExtender = pathExtender;
            this.selectExtender = selectExtender;
            this.backend = backend;
            this.entityClass = entityClass;
            this.configuration = configuration;
        }

        public Builder<BE, E> where(Filter[][] filters) {
            selectExtender.filter().with(filters);
            return this;
        }

        public Builder<BE, E> where(Filter... filters) {
            selectExtender.filter().with(filters);
            return this;
        }

        TraversalContext<BE, E> get() {
            return new TraversalContext<>(inventory, pathExtender.get(), selectExtender.get(), backend, entityClass,
                    configuration);
        }

        <T extends AbstractElement<?, ?>> TraversalContext<BE, T> getting(Class<T> entityType) {
            return new TraversalContext<>(inventory, pathExtender.get(), selectExtender.get(), backend, entityType,
                    configuration);
        }
    }
}
