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
package org.hawkular.inventory.base;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.incorporates;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

import org.hawkular.inventory.api.EntityAlreadyExistsException;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Environments;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.Path;
import org.hawkular.inventory.api.model.Resource;

/**
 * @author Lukas Krejci
 * @since 0.1.0
 */
public final class BaseEnvironments {

    private BaseEnvironments() {

    }

    public static class ReadWrite<BE>
            extends Mutator<BE, Environment, Environment.Blueprint, Environment.Update, String>
            implements Environments.ReadWrite {

        public ReadWrite(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        protected String getProposedId(Environment.Blueprint blueprint) {
            return blueprint.getId();
        }

        @Override
        protected EntityAndPendingNotifications<Environment> wireUpNewEntity(BE entity, Environment.Blueprint blueprint,
                CanonicalPath parentPath, BE parent) {
            return new EntityAndPendingNotifications<>(new Environment(blueprint.getName(),
                    parentPath.extend(Environment.class, context.backend.extractId(entity)).get(),
                    blueprint.getProperties()));
        }

        @Override
        public void copy(String sourceEnvironmentId, String targetEnvironmentId) {
            //TODO implement
            throw new UnsupportedOperationException();
        }

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }

        @Override
        public Environments.Single create(Environment.Blueprint blueprint) throws EntityAlreadyExistsException {
            return new Single<>(context.replacePath(doCreate(blueprint)));
        }
    }

    public static class ReadContained<BE> extends Traversal<BE, Environment> implements Environments.ReadContained {

        public ReadContained(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Environments.Single get(String id) throws EntityNotFoundException {
            return new Single<>(context.proceed().where(id(id)).get());
        }
    }

    public static class Read<BE> extends Traversal<BE, Environment> implements Environments.Read {

        public Read(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Environments.Multiple getAll(Filter[][] filters) {
            return new Multiple<>(context.proceed().whereAll(filters).get());
        }

        @Override
        public Environments.Single get(Path id) throws EntityNotFoundException {
            return new Single<>(context.proceedTo(id));
        }
    }

    public static class Single<BE> extends SingleEntityFetcher<BE, Environment, Environment.Update>
            implements Environments.Single {

        public Single(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.ReadAssociate feeds() {
            return new BaseFeeds.ReadAssociate<>(context.proceedTo(incorporates, Feed.class).get());
        }

        @Override
        public Resources.ReadWrite feedlessResources() {
            return new BaseResources.ReadWrite<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.ReadWrite feedlessMetrics() {
            return new BaseMetrics.ReadWrite<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public Resources.Read allResources() {
            return new BaseResources.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(Resource.class)},
                    {by(incorporates), type(Feed.class), by(contains), type(Resource.class)}
            }).getting(Resource.class));
        }

        @Override
        public Metrics.Read allMetrics() {
            return new BaseMetrics.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(Metric.class)},
                    {by(incorporates), type(Feed.class), by(contains), type(Metric.class)}
            }).getting(Metric.class));
        }
    }

    public static class Multiple<BE> extends MultipleEntityFetcher<BE, Environment, Environment.Update>
            implements Environments.Multiple {

        public Multiple(TraversalContext<BE, Environment> context) {
            super(context);
        }

        @Override
        public Feeds.Read feeds() {
            return new BaseFeeds.Read<>(context.proceedTo(incorporates, Feed.class).get());
        }

        @Override
        public Resources.ReadContained feedlessResources() {
            return new BaseResources.ReadContained<>(context.proceedTo(contains, Resource.class).get());
        }

        @Override
        public Metrics.ReadContained feedlessMetrics() {
            return new BaseMetrics.ReadContained<>(context.proceedTo(contains, Metric.class).get());
        }

        @Override
        public Resources.Read allResources() {
            return new BaseResources.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(Resource.class)},
                    {by(incorporates), type(Feed.class), by(contains), type(Resource.class)}
            }).getting(Resource.class));
        }

        @Override
        public Metrics.Read allMetrics() {
            return new BaseMetrics.Read<>(context.proceed().hop(new Filter[][]{
                    {by(contains), type(Metric.class)},
                    {by(incorporates), type(Feed.class), by(contains), type(Metric.class)}
            }).getting(Metric.class));
        }
    }
}
