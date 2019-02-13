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

package org.opennms.enlinkd.generator.protocol;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.enlinkd.generator.TopologyContext;
import org.opennms.enlinkd.generator.TopologyGenerator;
import org.opennms.enlinkd.generator.TopologySettings;
import org.opennms.enlinkd.generator.topology.LinkedPairGenerator;
import org.opennms.enlinkd.generator.topology.PairGenerator;
import org.opennms.enlinkd.generator.topology.RandomConnectedPairGenerator;
import org.opennms.enlinkd.generator.topology.UndirectedPairGenerator;
import org.opennms.enlinkd.generator.util.InetAddressGenerator;
import org.opennms.enlinkd.generator.util.RandomUtil;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.topologies.service.api.OnmsTopology;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyEdge;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyMessage;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyPort;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyProtocol;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyUpdater;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyVertex;

public abstract class Protocol<Element> {

    final TopologySettings topologySettings;
    final TopologyContext context;
    private final RandomUtil random = new RandomUtil();
    
    // For updating the topology dao
    private final Set<OnmsTopologyPort> topologyPorts = new LinkedHashSet<>();
    private final Set<OnmsTopologyEdge> topologyEdges = new LinkedHashSet<>();
    private final Map<Integer, OnmsTopologyVertex> topologyVertexMap = new LinkedHashMap<>();

    public Protocol(TopologySettings topologySettings, TopologyContext context) {
        this.topologySettings = topologySettings;
        this.context = context;
    }

    public void createAndPersistNetwork() {
        this.context.currentProgress("%nCreating %s %s topology with %s Nodes, %s Elements, %s Links, %s SnmpInterfaces, %s IpInterfaces:",
                topologySettings.getTopology(),
                this.getProtocol(),
                topologySettings.getAmountNodes(),
                topologySettings.getAmountElements(),
                topologySettings.getAmountElements(),
                topologySettings.getAmountSnmpInterfaces(),
                topologySettings.getAmountIpInterfaces());
        OnmsCategory category = createCategory();
        context.getTopologyPersister().persist(category);
        List<OnmsNode> nodes = createNodes(topologySettings.getAmountNodes(), category);
        context.getTopologyPersister().persist(nodes);
        List<OnmsSnmpInterface> snmpInterfaces = createSnmpInterfaces(nodes);
        context.getTopologyPersister().persist(snmpInterfaces);
        List<OnmsIpInterface> ipInterfaces = createIpInterfaces(snmpInterfaces);
        context.getTopologyPersister().persist(ipInterfaces);
        
        // Mapping for topology update via the OnmsTopologyDao
        topologyVertexMap.putAll(getVerticesFromNodes(nodes));
        topologyPorts.addAll(getPortsFromInterfaces(snmpInterfaces, ipInterfaces, topologyVertexMap));

        // Edges will be added in this call
        createAndPersistProtocolSpecificEntities(nodes);
        updateTopology();
    }

    /**
     * Finds a port for a given node if one exists. The first port found is returned.
     * 
     * @param nodeId the node Id
     * @return an optional containing the port or empty if none could be found
     */
    final Optional<OnmsTopologyPort> getPortForNode(Integer nodeId) {
        OnmsTopologyVertex onmsTopologyVertex = topologyVertexMap.get(Objects.requireNonNull(nodeId));

        if (onmsTopologyVertex != null) {
            return topologyPorts.stream()
                    .filter(port -> port.getVertex() == onmsTopologyVertex)
                    .findFirst();
        }

        return Optional.empty();
    }

    /**
     * Maps a list of nodes to topology vertices.
     * 
     * @param nodes the nodes to map
     * @return a map of node Id to vertex
     */
    private Map<Integer, OnmsTopologyVertex> getVerticesFromNodes(List<OnmsNode> nodes) {
        return nodes.stream()
                .map(node -> {
                    OnmsTopologyVertex newVertex = OnmsTopologyVertex.create(node.getNodeId(), node.getLabel(), "", "");
                    newVertex.setNodeid(node.getId());
                    return newVertex;
                })
                .collect(Collectors.toMap(OnmsTopologyVertex::getNodeid, vertex -> vertex));
    }

    /**
     * Maps snmp & IP interfaces to topology ports.
     * 
     * @param snmpInterfaces the list of snmp interfaces
     * @param ipInterfaces the list of IP interfaces
     * @param vertices the existing vertices
     * @return a list of the mapped topology ports
     */
    private List<OnmsTopologyPort> getPortsFromInterfaces(List<OnmsSnmpInterface> snmpInterfaces,
                                                          List<OnmsIpInterface> ipInterfaces, Map<Integer,
            OnmsTopologyVertex> vertices) {
        List<OnmsTopologyPort> ports = snmpInterfaces.stream()
                .map(snmpInterface -> {
                    OnmsTopologyVertex vertex = vertices.get(snmpInterface.getNodeId());
                    if (vertex != null) {
                        OnmsTopologyPort onmsTopologyPort =
                                OnmsTopologyPort.create(snmpInterface.getId().toString(), vertex,
                                        snmpInterface.getIfIndex());
                        onmsTopologyPort.setIfindex(snmpInterface.getIfIndex());
                        onmsTopologyPort.setIfname(snmpInterface.getIfName());
                        return onmsTopologyPort;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ports.addAll(ipInterfaces.stream()
                .map(ipInterface -> {
                    OnmsTopologyVertex vertex = vertices.get(ipInterface.getNodeId());
                    if (vertex != null) {
                        OnmsTopologyPort onmsTopologyPort =
                                OnmsTopologyPort.create(ipInterface.getId().toString(), vertex,
                                        ipInterface.getIfIndex());
                        onmsTopologyPort.setIfindex(ipInterface.getIfIndex());
                        onmsTopologyPort.setAddr(ipInterface.getIpAddress().toString());
                        return onmsTopologyPort;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );

        return ports;
    }
    
    void addEdge(OnmsTopologyPort sourcePort, OnmsTopologyPort targetPort) {
        topologyEdges.add(OnmsTopologyEdge.create(String.format("%s-%s", sourcePort.getId(), targetPort.getId()),
                sourcePort, targetPort));
    }

    /**
     * Sends all of the vertices, ports and edges created one by one to the topology DAO as updates.
     */
    private void updateTopology() {
        OnmsTopologyUpdater updater = new TopologyUpdater(new HashSet<>(topologyVertexMap.values()), topologyEdges);
        topologySettings.getOnmsTopologyDao().register(updater);

        try {
            // Send all the vertices as updates
            for (OnmsTopologyVertex topologyVertex : topologyVertexMap.values()) {
                topologySettings.getOnmsTopologyDao().update(updater, OnmsTopologyMessage.update(topologyVertex,
                        updater.getProtocol()));
            }

            // Send all the ports as updates
            for (OnmsTopologyPort topologyPort : topologyPorts) {
                topologySettings.getOnmsTopologyDao().update(updater, OnmsTopologyMessage.update(topologyPort,
                        updater.getProtocol()));
            }

            // Send all the edges as updates
            for (OnmsTopologyEdge topologyEdge : topologyEdges) {
                topologySettings.getOnmsTopologyDao().update(updater, OnmsTopologyMessage.update(topologyEdge,
                        updater.getProtocol()));
            }
        } finally {
            // Unregister so we don't tie up the 1 updater reference we are allowed to register for this generator
            topologySettings.getOnmsTopologyDao().unregister(updater);
        }
    }
    
    protected abstract void createAndPersistProtocolSpecificEntities(List<OnmsNode> nodes);

    protected abstract TopologyGenerator.Protocol getProtocol();

    private OnmsCategory createCategory() {
        OnmsCategory category = new OnmsCategory();
        category.setName(TopologyGenerator.CATEGORY_NAME);
        return category;
    }

    private OnmsMonitoringLocation createMonitoringLocation() {
        OnmsMonitoringLocation location = new OnmsMonitoringLocation();
        location.setLocationName("Default");
        location.setMonitoringArea("localhost");
        return location;
    }

    protected List<OnmsNode> createNodes(int amountNodes, OnmsCategory category) {
        OnmsMonitoringLocation location = createMonitoringLocation();
        ArrayList<OnmsNode> nodes = new ArrayList<>();
        for (int i = 0; i < amountNodes; i++) {
            nodes.add(createNode(i, location, category));
        }
        return nodes;
    }

    protected OnmsNode createNode(int count, OnmsMonitoringLocation location, OnmsCategory category) {
        OnmsNode node = new OnmsNode();
        node.setId(count);
        node.setLabel("Node" + count);
        node.setLocation(location);
        node.addCategory(category);
        node.setType(OnmsNode.NodeType.ACTIVE);
        return node;
    }

    protected List<OnmsSnmpInterface> createSnmpInterfaces(List<OnmsNode> nodes) {
        ArrayList<OnmsSnmpInterface> interfaces = new ArrayList<>();
        for (int i = 0; i < topologySettings.getAmountSnmpInterfaces(); i++) {
            interfaces.add(createSnmpInterface(i, random.getRandom(nodes)));
        }
        return interfaces;
    }

    private OnmsSnmpInterface createSnmpInterface(int ifIndex, OnmsNode node) {
        OnmsSnmpInterface onmsSnmpInterface = new OnmsSnmpInterface();
        onmsSnmpInterface.setId((node.getId() * topologySettings.getAmountSnmpInterfaces()) + ifIndex);
        onmsSnmpInterface.setNode(node);
        onmsSnmpInterface.setIfIndex(ifIndex);
        onmsSnmpInterface.setIfType(4);
        onmsSnmpInterface.setIfSpeed(5L);
        onmsSnmpInterface.setIfAdminStatus(6);
        onmsSnmpInterface.setIfOperStatus(7);
        onmsSnmpInterface.setLastCapsdPoll(new Date());
        onmsSnmpInterface.setLastSnmpPoll(new Date());

        return onmsSnmpInterface;
    }

    protected List<OnmsIpInterface> createIpInterfaces(List<OnmsSnmpInterface> snmps) {
        ArrayList<OnmsIpInterface> interfaces = new ArrayList<>();
        InetAddressGenerator inetGenerator = new InetAddressGenerator();
        for (int i = 0; i < topologySettings.getAmountIpInterfaces(); i++) {
            interfaces.add(createIpInterface(random.getRandom(snmps), inetGenerator.next()));
        }
        return interfaces;
    }

    private OnmsIpInterface createIpInterface(OnmsSnmpInterface snmp, InetAddress inetAddress) {
        OnmsIpInterface ip = new OnmsIpInterface();
        ip.setId(snmp.getId());
        ip.setSnmpInterface(snmp);
        ip.setIpLastCapsdPoll(new Date());
        ip.setNode(snmp.getNode());
        ip.setIpAddress(inetAddress);
        return ip;
    }

    protected <E> PairGenerator<E> createPairGenerator(List<E> elements) {
        if (TopologyGenerator.Topology.complete == topologySettings.getTopology()) {
            return new UndirectedPairGenerator<>(elements);
        } else if (TopologyGenerator.Topology.ring == topologySettings.getTopology()) {
            return new LinkedPairGenerator<>(elements);
        } else if (TopologyGenerator.Topology.random == topologySettings.getTopology()) {
            return new RandomConnectedPairGenerator<>(elements);
        } else {
            throw new IllegalArgumentException("unknown topology: " + topologySettings.getTopology());
        }
    }

    /**
     * A {@link OnmsTopologyUpdater} used solely for the generator to send updates to the
     * {@link org.opennms.netmgt.topologies.service.api.OnmsTopologyDao}.
     */
    public class TopologyUpdater implements OnmsTopologyUpdater {
        private static final String GENERATOR_PROTOCOL = "GENERATOR";
        private final OnmsTopology onmsTopology;

        public TopologyUpdater(Set<OnmsTopologyVertex> vertices, Set<OnmsTopologyEdge> edges) {
            onmsTopology = new OnmsTopology();
            onmsTopology.setVertices(vertices);
            onmsTopology.setEdges(edges);
        }

        @Override
        public OnmsTopology getTopology() {
            return onmsTopology;
        }

        @Override
        public OnmsTopologyProtocol getProtocol() {
            return OnmsTopologyProtocol.create(GENERATOR_PROTOCOL);
        }

        @Override
        public String getName() {
            return GENERATOR_PROTOCOL;
        }
    }
}
