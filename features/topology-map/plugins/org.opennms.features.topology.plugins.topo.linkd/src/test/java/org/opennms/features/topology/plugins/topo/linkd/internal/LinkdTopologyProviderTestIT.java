/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.topology.plugins.topo.linkd.internal;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.enlinkd.generator.TopologyGenerator;
import org.opennms.enlinkd.generator.TopologyPersister;
import org.opennms.enlinkd.generator.TopologySettings;
import org.opennms.features.topology.api.topo.Edge;
import org.opennms.features.topology.api.topo.EdgeRef;
import org.opennms.features.topology.api.topo.Vertex;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.netmgt.dao.api.GenericPersistenceAccessor;
import org.opennms.netmgt.enlinkd.CdpOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.IsisOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.LldpOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.NodesOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.OspfOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.persistence.api.TopologyEntityCache;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyException;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-commonConfigs.xml",
        "classpath:/META-INF/opennms/applicationContext-LinkdTopologyProviderTestIT.xml"
})
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase
public class LinkdTopologyProviderTestIT {

    private final static Logger LOG = LoggerFactory.getLogger(LinkdTopologyProviderTestIT.class);

    @Autowired
    GenericPersistenceAccessor genericPersistenceAccessor;

    @Autowired
    LinkdTopologyProvider linkdTopologyProvider;

    @Autowired
    TopologyEntityCache entityCache;
    
    @Autowired
    NodesOnmsTopologyUpdater nodesOnmsTopologyUpdater;

    @Autowired
    CdpOnmsTopologyUpdater cdpOnmsTopologyUpdater;

    @Autowired
    IsisOnmsTopologyUpdater isisOnmsTopologyUpdater;
    
    @Autowired
    LldpOnmsTopologyUpdater lldpOnmsTopologyUpdater;
    
    @Autowired
    OspfOnmsTopologyUpdater ospfOnmsTopologyUpdater;
    
    private TopologyGenerator generator;

    @BeforeTransaction
    public void setUp() {
        nodesOnmsTopologyUpdater.register();
        cdpOnmsTopologyUpdater.register();
        isisOnmsTopologyUpdater.register();
        lldpOnmsTopologyUpdater.register();
        ospfOnmsTopologyUpdater.register();
        
        TopologyGenerator.ProgressCallback progressCallback = new TopologyGenerator.ProgressCallback(LOG::info);
        TopologyPersister persister = new TopologyPersister(genericPersistenceAccessor, progressCallback);
        generator = TopologyGenerator.builder()
                .persister(persister)
                .progressCallback(progressCallback).build();
    }

    @Test
    @Transactional
    public void testCdp()throws Exception {
        test(TopologyGenerator.Protocol.cdp);
    }

    @Test
    @Transactional
    public void testLldp() throws Exception {
        test(TopologyGenerator.Protocol.lldp);
    }

    @Test
    @Transactional
    public void testIsis() throws Exception {
        test(TopologyGenerator.Protocol.isis);
    }

    @Test
    @Transactional
    public void testOspf() throws Exception {
        test(TopologyGenerator.Protocol.ospf);
    }

    private void test(TopologyGenerator.Protocol protocol) throws OnmsTopologyException{
        testAmounts(protocol);
        testLinkingBetweenNodes(protocol);
    }

    private void testAmounts(TopologyGenerator.Protocol protocol) throws OnmsTopologyException {

        TopologySettings settings = TopologySettings.builder()
                .protocol(protocol)
                .build();

        // 1.) Generate topology and verify that the TopologyProvider finds it:
        generator.generateTopology(settings);
        verifyAmounts(settings);

        // 2.) Delete the topology. The TopologyProvider should still find it due to the cache:
        generator.deleteTopology();
        verifyAmounts(settings);

        // 3.) Invalidate cache - nothing should be found:
        entityCache.refresh();
        refresh();
        assertEquals(0, linkdTopologyProvider.getVerticesWithoutGroups().size());
    }

    private void refresh() throws OnmsTopologyException {
        nodesOnmsTopologyUpdater.setTopology(nodesOnmsTopologyUpdater.buildTopology());
        cdpOnmsTopologyUpdater.setTopology(cdpOnmsTopologyUpdater.buildTopology());
        isisOnmsTopologyUpdater.setTopology(isisOnmsTopologyUpdater.buildTopology());
        lldpOnmsTopologyUpdater.setTopology(lldpOnmsTopologyUpdater.buildTopology());
        ospfOnmsTopologyUpdater.setTopology(ospfOnmsTopologyUpdater.buildTopology());
        linkdTopologyProvider.refresh();
    }
    private void verifyAmounts(TopologySettings settings) throws OnmsTopologyException {
        refresh();
        List<Vertex> vertices = linkdTopologyProvider.getVerticesWithoutGroups();

        // Check amount nodes
        assertEquals(settings.getAmountNodes(), vertices.size());

        // Check amount edges
        Map<VertexRef, Set<EdgeRef>> edgeIds = linkdTopologyProvider.getEdgeIdsForVertices(vertices.toArray(new Vertex[vertices.size()]));
        Set<EdgeRef> allEdges = new HashSet<>();
        edgeIds.values().forEach(allEdges::addAll);
        // 2 links form one edge:
        int expectedAmountOfEdges = settings.getAmountLinks() / 2;
        assertEquals(expectedAmountOfEdges, linkdTopologyProvider.getEdgeTotalCount());
        assertEquals(expectedAmountOfEdges, allEdges.size());
    }

    private void generateTopologyAndRefreshCaches(TopologySettings settings) throws OnmsTopologyException{
        generator.generateTopology(settings);
        entityCache.refresh();
        
        refresh();
    }

    /**
     * Generates a ring topology and verifies that each Vertex is connected to it's neighbors.
     * @throws OnmsTopologyException 
     */
    private void testLinkingBetweenNodes(TopologyGenerator.Protocol protocol) throws OnmsTopologyException {

        // 1.) Generate Topology
        TopologySettings settings = TopologySettings.builder()
                .protocol(protocol)
                .amountNodes(10) // use 10 so that the label names remain in the single digits => makes sorting easier
                .amountLinks(20) // one edge is composed of 2 links
                .topology(TopologyGenerator.Topology.ring) // deterministic behaviour: each node is connected to its neighbors
                .build();
        generateTopologyAndRefreshCaches(settings);
        assertEquals(settings.getAmountNodes(), linkdTopologyProvider.getVerticesWithoutGroups().size());

        // 2.) sort the nodes by it's label name.
        List<Vertex> vertices = linkdTopologyProvider.getVerticesWithoutGroups();
        Vertex[] verticesArray = vertices.toArray(new Vertex[vertices.size()]);
        Arrays.sort(verticesArray, Comparator.comparing(Vertex::getLabel).thenComparing(Vertex::getNodeID));
        vertices = Arrays.asList(verticesArray);

        // 3.) test the linking between each node and its next neighbor
        for(int i = 0; i < vertices.size(); i++){
            VertexRef left = vertices.get(i);
            VertexRef right = vertices.get(nextIndexInList(i, vertices.size()-1));
            verifyLinkingBetweenNodes(left, right);
        }
    }

    private void verifyLinkingBetweenNodes(VertexRef left, VertexRef right) {

        // 1.) get the EdgeRef that connects the 2 vertices
        List<EdgeRef> leftRefs = Arrays.asList(this.linkdTopologyProvider.getEdgeIdsForVertex(left));
        List<EdgeRef>  rightRefs = Arrays.asList(this.linkdTopologyProvider.getEdgeIdsForVertex(right));
        Set<EdgeRef> intersection = intersect(leftRefs, rightRefs);
        assertEquals(1, intersection.size());
        EdgeRef ref = intersection.iterator().next();

        // 2.) get the Edge and check if it really connects the 2 Vertices
        Edge edge = this.linkdTopologyProvider.getEdge(ref);
        // we don't know the direction it is connected so we have to test both ways:
        assertTrue(
                (edge.getSource().getVertex().equals(left) || edge.getSource().getVertex().equals(right)) // source side
                        && (edge.getTarget().getVertex().equals(left) || edge.getTarget().getVertex().equals(right)) // target side
                        && !edge.getSource().getVertex().equals(edge.getTarget().getVertex())); // make sure it doesn't connect the same node
    }

    /**
     * Gives back the intersection between the 2 collections, as in:
     * - only elements that are contained in both collections will be retained
     * - double elements are removed
     */
    private <E> Set<E> intersect(final Collection<E> left, final Collection<E> right){
        Set<E> set = new HashSet<>(left);
        set.retainAll(new HashSet<>(right));
        return set;
    }

    private int nextIndexInList(int current, int lastIndexInList) {
        if (current == lastIndexInList) {
            return 0;
        }
        return ++current;
    }

}
