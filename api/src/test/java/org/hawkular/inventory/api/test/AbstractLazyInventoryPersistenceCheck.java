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
package org.hawkular.inventory.api.test;

import org.hawkular.inventory.api.Configuration;
import org.hawkular.inventory.api.EntityNotFoundException;
import org.hawkular.inventory.api.Feeds;
import org.hawkular.inventory.api.Metrics;
import org.hawkular.inventory.api.RelationNotFoundException;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.feeds.AcceptWithFallbackFeedIdStrategy;
import org.hawkular.inventory.api.feeds.RandomUUIDFeedIdStrategy;
import org.hawkular.inventory.api.filters.Defined;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.RelationWith;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.Environment;
import org.hawkular.inventory.api.model.Feed;
import org.hawkular.inventory.api.model.Metric;
import org.hawkular.inventory.api.model.MetricType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.Relationship;
import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResourceType;
import org.hawkular.inventory.api.model.Tenant;
import org.hawkular.inventory.api.paging.Order;
import org.hawkular.inventory.api.paging.Page;
import org.hawkular.inventory.api.paging.Pager;
import org.hawkular.inventory.lazy.LazyInventory;
import org.hawkular.inventory.lazy.PathFragment;
import org.hawkular.inventory.lazy.QueryFragmentTree;
import org.hawkular.inventory.lazy.spi.LazyInventoryBackend;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;
import static org.hawkular.inventory.api.Relationships.WellKnown.owns;
import static org.hawkular.inventory.api.filters.Related.by;
import static org.hawkular.inventory.api.filters.With.id;
import static org.hawkular.inventory.api.filters.With.type;

/**
 * @author Lukas Krejci
 * @since 0.0.6
 */
public abstract class AbstractLazyInventoryPersistenceCheck<E> {
    LazyInventory<E> inventory;

    protected abstract LazyInventory<E> instantiateNewInventory();

    protected abstract void destroyStorage() throws Exception;

    @Before
    public void setup() throws Exception {
        Properties ps = new Properties();
        try (FileInputStream f = new FileInputStream(System.getProperty("graph.config"))) {
            ps.load(f);
        }

        Configuration config = Configuration.builder().withFeedIdStrategy(
                new AcceptWithFallbackFeedIdStrategy(new RandomUUIDFeedIdStrategy()))
                .withConfiguration(ps)
                .build();

        inventory = instantiateNewInventory();
        inventory.initialize(config);

        try {
            inventory.tenants().delete("com.acme.tenant");
        } catch (Exception ignored) {
        }

        try {
            inventory.tenants().delete("com.example.tenant");
        } catch (Exception ignored) {
        }

        try {
            inventory.tenants().delete("perf0");
        } catch (Exception ignored) {
        }

        setupData();
    }

    private void setupData() throws Exception {
        //noinspection AssertWithSideEffects
        assert inventory.tenants()
                .create(Tenant.Blueprint.builder().withId("com.acme.tenant").withProperty("kachny", "moc").build())
                .entity().getId().equals("com.acme.tenant");
        assert inventory.tenants().get("com.acme.tenant").environments().create(new Environment.Blueprint("production"))
                .entity().getId().equals("production");
        assert inventory.tenants().get("com.acme.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("URL", "1.0")).entity().getId().equals("URL");
        assert inventory.tenants().get("com.acme.tenant").metricTypes()
                .create(new MetricType.Blueprint("ResponseTime", MetricUnit.MILLI_SECOND)).entity().getId()
                .equals("ResponseTime");

        inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL").metricTypes().associate("ResponseTime");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessMetrics()
                .create(new Metric.Blueprint("ResponseTime", "host1_ping_response")).entity().getId()
                .equals("host1_ping_response");
        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                .create(new Resource.Blueprint("host1", "URL")).entity()
                .getId().equals("host1");
        inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                .get("host1").metrics().associate("host1_ping_response");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds()
                .create(new Feed.Blueprint("feed1", null)).entity().getId().equals("feed1");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource1", "URL")).entity().getId()
                .equals("feedResource1");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource2", "URL")).entity().getId()
                .equals("feedResource2");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .resources().create(new Resource.Blueprint("feedResource3", "URL")).entity().getId()
                .equals("feedResource3");

        assert inventory.tenants().get("com.acme.tenant").environments().get("production").feeds().get("feed1")
                .metrics().create(new Metric.Blueprint("ResponseTime", "feedMetric1")).entity().getId()
                .equals("feedMetric1");

        assert inventory.tenants().create(new Tenant.Blueprint("com.example.tenant")).entity().getId()
                .equals("com.example.tenant");
        assert inventory.tenants().get("com.example.tenant").environments().create(new Environment.Blueprint("test"))
                .entity().getId().equals("test");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Kachna", "1.0")).entity().getId().equals("Kachna");
        assert inventory.tenants().get("com.example.tenant").resourceTypes()
                .create(new ResourceType.Blueprint("Playroom", "1.0")).entity().getId().equals("Playroom");
        assert inventory.tenants().get("com.example.tenant").metricTypes()
                .create(new MetricType.Blueprint("Size", MetricUnit.BYTE)).entity().getId().equals("Size");
        inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").metricTypes().associate("Size");

        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .create(new Metric.Blueprint("Size", "playroom1_size")).entity().getId().equals("playroom1_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessMetrics()
                .create(new Metric.Blueprint("Size", "playroom2_size")).entity().getId().equals("playroom2_size");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .create(new Resource.Blueprint("playroom1", "Playroom")).entity().getId().equals("playroom1");
        assert inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .create(new Resource.Blueprint("playroom2", "Playroom")).entity().getId().equals("playroom2");

        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom1").metrics().associate("playroom1_size");
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().associate("playroom2_size");

        // some ad-hoc relationships
        Environment test = inventory.tenants().get("com.example.tenant").environments().get("test").entity();
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .linkWith("yourMom", test, null);
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.incoming)
                .linkWith("IamYourFather", test, null);
    }

    private void teardownData() throws Exception {
        Tenant t = new Tenant("com.example.tenant");
        Environment e = new Environment(t.getId(), "test");
        MetricType sizeType = new MetricType(t.getId(), "Size");
        ResourceType playRoomType = new ResourceType(t.getId(), "Playroom", "1.0");
        ResourceType kachnaType = new ResourceType(t.getId(), "Kachna", "1.0");
        Resource playroom1 = new Resource(t.getId(), e.getId(), null, "playroom1", playRoomType);
        Resource playroom2 = new Resource(t.getId(), e.getId(), null, "playroom2", playRoomType);
        Metric playroom1Size = new Metric(t.getId(), e.getId(), null, "playroom1_size", sizeType);
        Metric playroom2Size = new Metric(t.getId(), e.getId(), null, "playroom2_size", sizeType);

        inventory.inspect(e).feedlessMetrics().delete(playroom2Size.getId());
        assertDoesNotExist(playroom2Size);
        assertExists(t, e, sizeType, playRoomType, kachnaType, playroom1, playroom2, playroom1Size);

        inventory.inspect(t).resourceTypes().delete(kachnaType.getId());
        assertDoesNotExist(kachnaType);
        assertExists(t, e, sizeType, playRoomType, playroom1, playroom2, playroom1Size);

        try {
            inventory.inspect(t).metricTypes().delete(sizeType.getId());
            Assert.fail("Deleting a metric type which references some metrics should not be possible.");
        } catch (IllegalArgumentException ignored) {
            //good
        }

        inventory.inspect(e).feedlessMetrics().delete(playroom1Size.getId());
        assertDoesNotExist(playroom1Size);
        assertExists(t, e, sizeType, playRoomType, playroom1, playroom2);

        inventory.inspect(t).metricTypes().delete(sizeType.getId());
        assertDoesNotExist(sizeType);
        assertExists(t, e, playRoomType, playroom1, playroom2);

        try {
            inventory.inspect(t).resourceTypes().delete(playRoomType.getId());
            Assert.fail("Deleting a resource type which references some resources should not be possible.");
        } catch (IllegalArgumentException ignored) {
            //good
        }

        inventory.inspect(e).feedlessResources().delete(playroom1.getId());
        assertDoesNotExist(playroom1);
        assertExists(t, e, sizeType, playRoomType, playroom2);

        inventory.tenants().delete(t.getId());
        assertDoesNotExist(t);
        assertDoesNotExist(e);
        assertDoesNotExist(playRoomType);
        assertDoesNotExist(playroom2);
    }

    private void assertDoesNotExist(Entity e) {
        try {
            inventory.inspect(e, ResolvableToSingle.class).entity();
            Assert.fail(e + " should have been deleted");
        } catch (EntityNotFoundException ignored) {
            //good
        }
    }

    private void assertExists(Entity e) {
        try {
            inventory.inspect(e, ResolvableToSingle.class);
        } catch (EntityNotFoundException ignored) {
            Assert.fail(e + " should have been present in the inventory.");
        }
    }

    private void assertExists(Entity... es) {
        Stream.of(es).forEach(this::assertExists);
    }

    @After
    public void teardown() throws Exception {
        try {
            teardownData();
        } finally {
            try {
                inventory.close();
            } finally {
                destroyStorage();
            }
        }
    }

    @Test
    public void testTenants() throws Exception {
        Function<String, Void> test = (id) -> {
            QueryFragmentTree query = QueryFragmentTree.empty().asBuilder()
                    .with(PathFragment.from(type(Tenant.class), With.id(id))).build();

            Page<E> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

            Assert.assertEquals(1, results.size());

            E tenant = results.get(0);

            String eid = inventory.getBackend().extractId(tenant);

            Assert.assertEquals(id, eid);

            return null;
        };

        test.apply("com.acme.tenant");
        test.apply("com.example.tenant");

        QueryFragmentTree query = QueryFragmentTree.empty().asBuilder()
                .with(PathFragment.from(type(Tenant.class))).build();

        Page<E> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testEntitiesByRelationships() throws Exception {
        Function<Integer, Function<Class<? extends Entity<?, ?>>, Function<String, Function<Integer, Function<Class<? extends Entity<?, ?>>,
                Function<ResolvableToMany<?>, Consumer<ResolvableToMany<?>>>>>>>>
                testHelper = (numberOfParents -> parentType -> edgeLabel -> numberOfKids -> childType ->
                multipleParents -> multipleChildren -> {
                    LazyInventoryBackend<?> backend = inventory.getBackend();

                    Page<?> parents = backend.query(QueryFragmentTree.filter().with(type(parentType)).get(),
                            Pager.unlimited(Order.unspecified()));

                    Page<?> children = backend.query(QueryFragmentTree.path().with(type(parentType),
                            by(edgeLabel), type(childType)).get(), Pager.unlimited(Order.unspecified()));

//                    GremlinPipeline<Graph, Vertex> q1 = new GremlinPipeline<Graph, Vertex>(graph)
//                            .V().has("__type", parentType).cast(Vertex.class);
//                    Iterator<Vertex> parentIterator = q1.iterator();
//
//                    GremlinPipeline<Graph, Vertex> q2 = new GremlinPipeline<Graph, Vertex>(graph)
//                            .V().has("__type", parentType).out(edgeLabel).has("__type", childType)
//                            .cast(Vertex.class);
//                    Iterator<Vertex> childIterator = q2.iterator();

                    Iterator<?> multipleParentIterator = multipleParents.entities().iterator();
                    Iterator<?> multipleChildrenIterator = multipleChildren.entities().iterator();

                    assert parents.size() == numberOfParents : "There must be exactly " + numberOfParents + " " +
                            parentType + "s " + "that have outgoing edge labeled with " + edgeLabel + ". Backend " +
                            "query returned only " + parents.size();

                    assert multipleParents.entities().size() == numberOfParents : "There must be exactly " + numberOfParents + " " +
                            parentType + "s that have outgoing edge labeled with " + edgeLabel + ". Tested API " +
                            "returned only " + multipleParents.entities().size();
//                    for (int i = 0; i < numberOfParents; i++) {
//                        assert parentIterator.hasNext() : "There must be exactly " + numberOfParents + " " +
//                                parentType + "s " + "that have outgoing edge labeled with " + edgeLabel + ". Gremlin " +
//                                "query returned only " + i;
//                        assert multipleParentIterator.hasNext() : "There must be exactly " + numberOfParents + " " +
//                                parentType + "s that have outgoing edge labeled with " + edgeLabel + ". Tested API " +
//                                "returned only " + i;
//                        parentIterator.next();
//                        multipleParentIterator.next();
//                    }
//                    assert !parentIterator.hasNext() : "There must be " + numberOfParents + " " + parentType +
//                            "s. Gremlin query returned more than " + numberOfParents;
//                    assert !multipleParentIterator.hasNext() : "There must be " + numberOfParents + " " + parentType +
//                            "s. Tested API returned more than " + numberOfParents;

                    assert children.size() == numberOfKids : "There must be exactly " + numberOfKids + " " + childType +
                            "s that are directly under " + parentType + " connected with " + edgeLabel +
                            ". Gremlin query returned only " + children.size();

                    assert multipleChildren.entities().size() == numberOfKids;
//
//                    for (int i = 0; i < numberOfKids; i++) {
//                        assert childIterator.hasNext() : "There must be exactly " + numberOfKids + " " + childType +
//                                "s that are directly under " + parentType + " connected with " + edgeLabel +
//                                ". Gremlin query returned only " + i;
//                        assert multipleChildrenIterator.hasNext();
//                        childIterator.next();
//                        multipleChildrenIterator.next();
//                    }
//                    assert !childIterator.hasNext() : "There must be exactly " + numberOfKids + " " + childType + "s";
//                    assert !multipleChildrenIterator.hasNext();
                });

        ResolvableToMany parents = inventory.tenants().getAll(by("contains"));
        ResolvableToMany kids = inventory.tenants().getAll().environments().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(2).apply(Environment.class).apply(parents).accept(kids);

        kids = inventory.tenants().getAll().resourceTypes().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(3).apply(ResourceType.class).apply(parents)
                .accept(kids);

        kids = inventory.tenants().getAll().metricTypes().getAll(Related.asTargetBy("contains"));
        testHelper.apply(2).apply(Tenant.class).apply("contains").apply(2).apply(MetricType.class).apply(parents)
                .accept(kids);

        parents = inventory.tenants().getAll().environments().getAll(by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().feedlessMetrics().getAll(
                Related.asTargetBy("contains"));
        testHelper.apply(2).apply(Environment.class).apply("contains").apply(3).apply(Metric.class).apply(parents).
                accept(kids);

        kids = inventory.tenants().getAll().environments().getAll().feedlessResources().getAll(
                Related.asTargetBy("contains"));
        testHelper.apply(2).apply(Environment.class).apply("contains").apply(3).apply(Resource.class).apply(parents).accept
                (kids);

        parents = inventory.tenants().getAll().environments().getAll(by("contains"));
        kids = inventory.tenants().getAll().environments().getAll().allMetrics().getAll(
                Related.asTargetBy("defines"));
        testHelper.apply(2).apply(MetricType.class).apply("defines").apply(4).apply(Metric.class).apply(parents)
                .accept(kids);
    }

    @Test
    public void testRelationshipServiceNamed1() throws Exception {
        Set<Relationship> contains = inventory.tenants().getAll().relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getId())
                && "URL".equals(rel.getTarget().getId()))
                : "Tenant 'com.acme.tenant' must contain ResourceType 'URL'.";
        assert contains.stream().anyMatch(rel -> "com.acme.tenant".equals(rel.getSource().getId())
                && "production".equals(rel.getTarget().getId()))
                : "Tenant 'com.acme.tenant' must contain Environment 'production'.";
        assert contains.stream().anyMatch(rel -> "com.example.tenant".equals(rel.getSource().getId())
                && "Size".equals(rel.getTarget().getId()))
                : "Tenant 'com.example.tenant' must contain MetricType 'Size'.";

        contains.forEach((r) -> {
            assert r.getId() != null;
        });
    }

    @Test
    public void testRelationshipServiceNamed2() throws Exception {
        Set<Relationship> contains = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships().named("contains").entities();
        assert contains.stream().anyMatch(rel -> "playroom1".equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom1'.";
        assert contains.stream().anyMatch(rel -> "playroom2".equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom2'.";
        assert contains.stream().anyMatch(rel -> "playroom2_size".equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom2_size'.";
        assert contains.stream().anyMatch(rel -> "playroom1_size".equals(rel.getTarget().getId()))
                : "Environment 'test' must contain 'playroom1_size'.";
        assert contains.stream().allMatch(rel -> !"production".equals(rel.getSource().getId()))
                : "Environment 'production' cant be the source of these relationships.";

        contains.forEach((r) -> {
            assert r.getId() != null;
        });
    }

    @Test
    public void testRelationshipServiceLinkedWith() throws Exception {
        Set<Relationship> rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .feedlessResources().get("playroom2").metrics().get("playroom2_size")
                .relationships(Relationships.Direction.outgoing).named("yourMom").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getTarget().getId()) : "Target of relationship 'yourMom' should " +
                "be the 'test' environment";

        rels = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.both)
                .named("IamYourFather").entities();
        assert rels != null && rels.size() == 1 : "There should be 1 relationship conforming the filters";
        assert "test".equals(rels.iterator().next().getSource().getId()) : "Source of relationship 'IamYourFather' " +
                "should be the 'test' environment";
    }

    @Test
    public void testRelationshipServiceLinkedWithAndDelete() throws Exception {
        Tenant tenant = inventory.tenants().get("com.example.tenant").entity();
        Relationship link = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .feedlessResources().get("host1").relationships(Relationships.Direction.incoming)
                .linkWith("crossTenantLink", tenant, null).entity();

        assert inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.outgoing)
                .named("crossTenantLink").entities().size() == 1 : "Relation 'crossTenantLink' was not found.";
        // delete the relationship
        inventory.tenants().get("com.example.tenant").relationships(/*defaults to outgoing*/).delete(link.getId());
        assert inventory.tenants().get("com.example.tenant").relationships()
                .named("crossTenantLink").entities().size() == 0 : "Relation 'crossTenantLink' was found.";

        // try deleting again
        try {
            inventory.tenants().get("com.example.tenant").relationships(/*defaults to outgoing*/).delete(link.getId());
            assert false : "It shouldn't be possible to delete the same relationship twice";
        } catch (RelationNotFoundException e) {
            // good
        }
    }

    @Test
    public void testRelationshipServiceUpdateRelationship1() throws Exception {
        final String someKey = "k3y";
        final String someValue = "v4lu3";
        Relationship rel1 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .named("yourMom").entities().iterator().next();
        assert null == rel1.getProperties().get(someKey) : "There should not be any property with key 'k3y'";

        Relationship rel2 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .named("yourMom").entities().iterator().next();
        assert rel1.getId().equals(rel2.getId()) && null == rel2.getProperties().get(someKey) : "There should not be" +
                " any property with key 'k3y'";

        // persist the change
        inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .update(rel1.getId(), Relationship.Update.builder().withProperty(someKey, someValue).build());

        Relationship rel3 = inventory.tenants().get("com.example.tenant").environments().get("test").feedlessResources()
                .get("playroom2").metrics().get("playroom2_size").relationships(Relationships.Direction.outgoing)
                .named("yourMom").entities().iterator().next();
        assert rel1.getId().equals(rel3.getId()) && someValue.equals(rel3.getProperties().get(someKey))
                : "There should be the property with key 'k3y' and value 'v4lu3'";
    }

    @Test
    public void testRelationshipServiceGetAllFilters() throws Exception {
        Set<Relationship> rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(Relationships.Direction.outgoing).getAll(RelationWith.name("contains")).entities();
        assert rels != null && rels.size() == 4 : "There should be 4 relationships conforming the filters";
        assert rels.stream().anyMatch(rel -> "playroom2_size".equals(rel.getTarget().getId()));
        assert rels.stream().anyMatch(rel -> "playroom1".equals(rel.getTarget().getId()));


        rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(Relationships.Direction.outgoing).getAll(RelationWith.name("contains"), RelationWith
                        .targetOfType(Metric.class)).entities();
        assert rels != null && rels.size() == 2 : "There should be 2 relationships conforming the filters";
        assert rels.stream().allMatch(rel -> Metric.class.equals(rel.getTarget().getClass())) : "The type of all the " +
                "targets should be the 'Metric'";


        rels = inventory.tenants().get("com.example.tenant").environments().get("test")
                .relationships(Relationships.Direction.incoming).getAll(RelationWith.name("contains")).entities();

        assert rels != null && rels.size() == 1 : "There should be just 1 relationship conforming the filters";
        assert "com.example.tenant".equals(rels.iterator().next().getSource().getId()) : "Tenant 'com.example" +
                ".tenant' was not found";


        rels = inventory.tenants().getAll().relationships().named
                (contains).environments().getAll().relationships().getAll(RelationWith
                .properties("label", "contains"), RelationWith.targetsOfTypes(Resource.class, Metric.class))
                .entities();
        assert rels != null && rels.size() == 6 : "There should be 6 relationships conforming the filters";
        assert rels.stream().allMatch(rel -> "test".equals(rel.getSource().getId())
                || "production".equals(rel.getSource().getId())) : "Source should be either 'test' or 'production'";
        assert rels.stream().allMatch(rel -> Resource.class.equals(rel.getTarget().getClass())
                || Metric.class.equals(rel.getTarget().getClass())) : "Target should be either a metric or a " +
                "resource";
    }

    @Test
    public void testRelationshipServiceGetAllFiltersWithSubsequentCalls() throws Exception {
        Metric metric = inventory.tenants().getAll().relationships().named
                (contains).environments().getAll().relationships().getAll(RelationWith
                .properties("label", "contains"), RelationWith.targetsOfTypes(Resource.class, Metric.class)).metrics
                ().get("playroom1_size").entity();
        assert "playroom1_size".equals(metric.getId()) : "Metric playroom1_size was not found using various relation " +
                "filters";

        try {
            inventory.tenants().getAll().relationships().named
                    (contains).environments().getAll().relationships().getAll(RelationWith
                    .properties("label", "contains"), RelationWith.targetsOfTypes(Resource.class)).metrics
                    ().get("playroom1_size").entity();
            assert false : "this code should not be reachable. There should be no metric reachable under " +
                    "'RelationWith.targetsOfTypes(Resource.class))' filter";
        } catch (EntityNotFoundException e) {
            // good
        }
    }

    @Test
    public void testRelationshipServiceCallChaining() throws Exception {
        MetricType metricType = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named("owns").metricTypes().get("Size").entity();// not empty
        assert "Size".equals(metricType.getId()) : "ResourceType[Playroom] -owns-> MetricType[Size] was not found";

        try {
            inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships()
                    .named("contains").metricTypes().get("Size").entity();
            assert false : "There is no such an entity satisfying the query, this code shouldn't be reachable";
        } catch (EntityNotFoundException e) {
            // good
        }

        Set<Resource> resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom")
                .relationships().named
                        ("defines").resources().getAll().entities();
        assert resources.stream().allMatch(res -> "playroom1".equals(res.getId()) || "playroom2".equals(res.getId()))
                : "ResourceType[Playroom] -defines-> resources called playroom1 and playroom2";

        resources = inventory.tenants().get("com.example.tenant").resourceTypes().get("Playroom").relationships().named
                ("owns").resources().getAll().entities(); // empty
        assert resources.isEmpty()
                : "No resources should be found under the relationship called owns from resource type";
    }

@Test
public void testEnvironments() throws Exception {
    BiFunction<String, String, Void> test = (tenantId, id) -> {
        QueryFragmentTree q = QueryFragmentTree.empty().asBuilder()
                .with(PathFragment.from(type(Tenant.class), With.id(tenantId), by(contains),
                        type(Environment.class), With.id(id))).build();


        Page<E> envs = inventory.getBackend().query(q, Pager.unlimited(Order.unspecified()));

        Assert.assertEquals(1, envs.size());

        //query, we should get the same results
        Environment env = inventory.tenants().get(tenantId).environments().get(id).entity();
        Assert.assertEquals(id, env.getId());

        env = inventory.getBackend().convert(envs.get(0), Environment.class);
        Assert.assertEquals(id, env.getId());

        return null;
    };

    test.apply("com.acme.tenant", "production");
    test.apply("com.example.tenant", "test");

    QueryFragmentTree q = QueryFragmentTree.empty().asBuilder()
            .with(PathFragment.from(type(Environment.class))).build();

    Assert.assertEquals(2, inventory.getBackend().query(q, Pager.unlimited(Order.unspecified())).size());
}

    @Test
    public void testResourceTypes() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            QueryFragmentTree query = QueryFragmentTree.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(ResourceType.class), id(id)).get();

            Page<?> results = inventory.getBackend().query(query, Pager.unlimited(Order.unspecified()));

            assert !results.isEmpty();

            ResourceType rt = inventory.tenants().get(tenantId).resourceTypes().get(id).entity();
            assert rt.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL");
        test.apply("com.example.tenant", "Kachna");
        test.apply("com.example.tenant", "Playroom");

        QueryFragmentTree query = QueryFragmentTree.path().with(type(ResourceType.class)).get();
        assert 3 == inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).size();
    }

    @Test
    public void testMetricDefinitions() throws Exception {
        BiFunction<String, String, Void> test = (tenantId, id) -> {

            QueryFragmentTree query = QueryFragmentTree.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(MetricType.class), id(id)).get();

            assert !inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).isEmpty();

            MetricType md = inventory.tenants().get(tenantId).metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "ResponseTime");
        test.apply("com.example.tenant", "Size");

        QueryFragmentTree query = QueryFragmentTree.path().with(type(MetricType.class)).get();
        assert 2 == inventory.getBackend().query(query, Pager.unlimited(Order.unspecified())).size();
    }

    @Test
    public void testMetricTypesLinkedToResourceTypes() throws Exception {
        TriFunction<String, String, String, Void> test = (tenantId, resourceTypeId, id) -> {

            QueryFragmentTree q = QueryFragmentTree.path().with(type(Tenant.class), id(tenantId),
                    by(contains), type(ResourceType.class), id(resourceTypeId), by(owns),
                    type(MetricType.class), id(id)).get();

            assert !inventory.getBackend().query(q, Pager.unlimited(Order.unspecified())).isEmpty();

            MetricType md = inventory.tenants().get(tenantId).resourceTypes().get(resourceTypeId)
                    .metricTypes().get(id).entity();
            assert md.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "URL", "ResponseTime");
        test.apply("com.example.tenant", "Playroom", "Size");
    }

    @Test
    public void testMetrics() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, metricDefId, id) -> {

            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessMetrics()
                    .getAll(Defined.by(new MetricType(tenantId, metricDefId)), With.id(id)).entities().iterator()
                    .next();
            assert m.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "ResponseTime", "host1_ping_response");
        test.apply("com.example.tenant", "test", "Size", "playroom1_size");
        test.apply("com.example.tenant", "test", "Size", "playroom2_size");

        Assert.assertEquals(4, inventory.getBackend().query(QueryFragmentTree.path().with(type(Metric.class)).get(),
                Pager.unlimited(Order.unspecified())).size());
    }

    @Test
    public void testResources() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceTypeId, id) -> {
            Resource r = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources()
                    .getAll(Defined.by(new ResourceType(tenantId, resourceTypeId, "1.0")), With.id(id)).entities()
                    .iterator().next();
            assert r.getId().equals(id);

            return null;
        };

        test.apply("com.acme.tenant", "production", "URL", "host1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom1");
        test.apply("com.example.tenant", "test", "Playroom", "playroom2");


        Assert.assertEquals(6, inventory.getBackend().query(QueryFragmentTree.path().with(type(Resource.class)).get(),
                Pager.unlimited(Order.unspecified())).size());
    }

    @Test
    public void testAssociateMetricWithResource() throws Exception {
        TetraFunction<String, String, String, String, Void> test = (tenantId, environmentId, resourceId, metricId) -> {
            Metric m = inventory.tenants().get(tenantId).environments().get(environmentId).feedlessResources()
                    .get(resourceId).metrics().get(metricId).entity();
            assert metricId.equals(m.getId());

            return null;
        };

        test.apply("com.acme.tenant", "production", "host1", "host1_ping_response");
        test.apply("com.example.tenant", "test", "playroom1", "playroom1_size");
        test.apply("com.example.tenant", "test", "playroom2", "playroom2_size");
    }

    @Test
    public void queryMultipleTenants() throws Exception {
        Set<Tenant> tenants = inventory.tenants().getAll().entities();

        assert tenants.size() == 2;
    }

    @Test
    public void queryMultipleEnvironments() throws Exception {
        Set<Environment> environments = inventory.tenants().getAll().environments().getAll().entities();

        assert environments.size() == 2;
    }

    @Test
    public void queryMultipleResourceTypes() throws Exception {
        Set<ResourceType> types = inventory.tenants().getAll().resourceTypes().getAll().entities();
        assert types.size() == 3;
    }

    @Test
    public void queryMultipleMetricDefs() throws Exception {
        Set<MetricType> types = inventory.tenants().getAll().metricTypes().getAll().entities();
        assert types.size() == 2;
    }

    @Test
    public void queryMultipleResources() throws Exception {
        Set<Resource> rs = inventory.tenants().getAll().environments().getAll().feedlessResources().getAll().entities();
        assert rs.size() == 3;
    }

    @Test
    public void queryMultipleMetrics() throws Exception {
        Set<Metric> ms = inventory.tenants().getAll().environments().getAll().feedlessMetrics().getAll().entities();
        assert ms.size() == 3;
    }

    @Test
    public void testNoTwoFeedsWithSameID() throws Exception {
        Feeds.ReadWrite feeds = inventory.tenants().get("com.acme.tenant").environments().get("production")
                .feeds();

        Feed f1 = feeds.create(new Feed.Blueprint("feed", null)).entity();
        Feed f2 = feeds.create(new Feed.Blueprint("feed", null)).entity();

        assert f1.getId().equals("feed");
        assert !f1.getId().equals(f2.getId());
    }

    @Test
    public void testNoTwoEquivalentEntitiesOnTheSamePath() throws Exception {
        try {
            inventory.tenants().create(new Tenant.Blueprint("com.acme.tenant"));
            Assert.fail("Creating tenant with existing ID should fail");
        } catch (Exception e) {
            //good
        }

        try {
            inventory.tenants().get("com.acme.tenant").environments().create(new Environment.Blueprint("production"));
            Assert.fail("Creating environment with existing ID should fail");
        } catch (Exception e) {
            //good
        }

        try {
            inventory.tenants().get("com.acme.tenant").environments().get("production").feedlessResources()
                    .create(new Resource.Blueprint("host1", "URL"));
            Assert.fail("Creating resource with existing ID should fail");
        } catch (Exception e) {
            //good
        }
    }

    @Test
    public void testContainsLoopsImpossible() throws Exception {
        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.outgoing)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Self-loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.incoming)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Self-loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").environments().get("test")
                    .relationships(Relationships.Direction.outgoing)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.incoming)
                    .linkWith("contains", new Environment("com.example.tenant", "test"), null);

            Assert.fail("Loops in contains should be disallowed");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testContainsDiamondsImpossible() throws Exception {
        try {
            inventory.tenants().get("com.example.tenant").relationships(Relationships.Direction.outgoing)
                    .linkWith("contains", new ResourceType("com.acme.tenant", "URL", "1.0"), null);

            Assert.fail("Entity cannot be contained in 2 or more others");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            inventory.tenants().get("com.acme.tenant").resourceTypes().get("URL")
                    .relationships(Relationships.Direction.incoming)
                    .linkWith("contains", new Tenant("com.example.tenant"), null);

            Assert.fail("Entity cannot be contained in 2 or more others");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testPropertiesCreated() throws Exception {
        Tenant t = inventory.tenants().get("com.acme.tenant").entity();

        Assert.assertEquals(1, t.getProperties().size());
        Assert.assertEquals("moc", t.getProperties().get("kachny"));
    }

    @Test
    public void testPropertiesUpdatedOnEntities() throws Exception {

        inventory.tenants().update("com.acme.tenant", Tenant.Update.builder().withProperty("ducks", "many")
                .withProperty("hammer", "nails").build());

        Tenant t = inventory.tenants().get("com.acme.tenant").entity();

        Assert.assertEquals(2, t.getProperties().size());
        Assert.assertEquals("many", t.getProperties().get("ducks"));
        Assert.assertEquals("nails", t.getProperties().get("hammer"));

        //reset the change we made back...
        inventory.tenants().update("com.acme.tenant", Tenant.Update.builder().withProperty("kachny", "moc").build());
        testPropertiesCreated();
    }

    @Test
    public void testPropertiesUpdatedOnRelationships() throws Exception {

        Relationship r = inventory.tenants().get("com.acme.tenant").relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        inventory.tenants().get("com.acme.tenant").relationships().update(r.getId(),
                Relationship.Update.builder().withProperty("ducks", "many").withProperty("hammer", "nails").build());

        r = inventory.tenants().get("com.acme.tenant").relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        Assert.assertEquals(2, r.getProperties().size());
        Assert.assertEquals("many", r.getProperties().get("ducks"));
        Assert.assertEquals("nails", r.getProperties().get("hammer"));

        //reset the change we made back...
        inventory.tenants().get("com.acme.tenant").relationships().update(r.getId(), new Relationship.Update(null));

        r = inventory.tenants().get("com.acme.tenant").relationships()
                .getAll(RelationWith.name("contains")).entities().iterator().next();

        Assert.assertEquals(0, r.getProperties().size());
    }

    @Test
    public void testPaging() throws Exception {
        //the page is not modifiable but we'll need to modify this later on in the tests
        List<Metric> allResults = new ArrayList<>(inventory.tenants().getAll().environments().getAll().feedlessMetrics()
                .getAll().entities(Pager.unlimited(Order.by("id", Order.Direction.DESCENDING))));

        assert allResults.size() == 3;

        Pager firstPage = new Pager(0, 1, Order.by("id", Order.Direction.DESCENDING));

        Metrics.Multiple metrics = inventory.tenants().getAll().environments().getAll().feedlessMetrics().getAll();

        Page<Metric> ms = metrics.entities(firstPage);
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert ms.get(0).equals(allResults.get(0));

        ms = metrics.entities(firstPage.nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert ms.get(0).equals(allResults.get(1));

        ms = metrics.entities(firstPage.nextPage().nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert ms.get(0).equals(allResults.get(2));

        ms = metrics.entities(firstPage.nextPage().nextPage().nextPage());
        assert ms.getTotalSize() == 3;
        assert ms.size() == 0;

        //try the same with an unspecified order
        //the reason for checking this explicitly is that the order pipe implicitly loads
        //all elements before sending them on in the pipeline to the range filter.
        //If the order is not present, the elements might not be all loaded before the range
        //is overflown. The total still needs to match even in that case.
        firstPage = new Pager(0, 1, Order.unspecified());

        ms = metrics.entities(firstPage);
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(ms.get(0)); //i.e. we check that the result is in all results and remove it from there
        //so that subsequent checks for the same thing cannot get confused by the
        //existence of this metric in all the results.

        ms = metrics.entities(firstPage.nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(ms.get(0));

        ms = metrics.entities(firstPage.nextPage().nextPage());
        assert ms.size() == 1;
        assert ms.getTotalSize() == 3;
        assert allResults.remove(ms.get(0));

        ms = metrics.entities(firstPage.nextPage().nextPage().nextPage());
        assert ms.getTotalSize() == 3;
        assert ms.size() == 0;
    }

    @Test
    public void testGettingResourcesFromFeedsUsingEnvironments() throws Exception {
        Set<Resource> rs = inventory.tenants().get("com.acme.tenant").environments().get("production").allResources()
                .getAll().entities();

        Assert.assertTrue(rs.stream().anyMatch((r) -> "host1".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource1".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource2".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedResource3".equals(r.getId())));
    }

    @Test
    public void testGettingMetricsFromFeedsUsingEnvironments() throws Exception {
        Set<Metric> rs = inventory.tenants().get("com.acme.tenant").environments().get("production").allMetrics()
                .getAll().entities();

        Assert.assertTrue(rs.stream().anyMatch((r) -> "host1_ping_response".equals(r.getId())));
        Assert.assertTrue(rs.stream().anyMatch((r) -> "feedMetric1".equals(r.getId())));
    }

    @Test
    public void testAllPathsMentionedInExceptions() throws Exception {
        try {
            inventory.tenants().get("non-tenant").environments().get("non-env").allResources().getAll().metrics()
                    .get("m").entity();
            Assert.fail("Fetching non-existant entity should have failed");
        } catch (EntityNotFoundException e) {
            Filter[][] paths = e.getFilters();
            Assert.assertEquals(2, paths.length);
            Assert.assertArrayEquals(Filter.by(type(Tenant.class), id("non-tenant"), by(contains),
                    type(Environment.class), id("non-env"), by(contains), type(Resource.class),
                    by(owns), type(Metric.class), id("m")).get(), paths[0]);
            Assert.assertArrayEquals(Filter.by(type(Tenant.class), id("non-tenant"), by(contains),
                    type(Environment.class), id("non-env"), by(contains), type(Feed.class),
                    by(contains), type(Resource.class), by(owns), type(Metric.class),
                    id("m")).get(), paths[1]);
        }
    }

    private interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    private interface TetraFunction<T, U, V, W, R> {
        R apply(T t, U u, V v, W w);
    }
}
