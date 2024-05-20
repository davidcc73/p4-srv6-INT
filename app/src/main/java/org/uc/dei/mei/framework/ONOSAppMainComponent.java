/*
 * Copyright 2021-present Open Networking Foundation
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
package org.uc.dei.mei.framework;


import org.jgrapht.Graph;
import org.jgrapht.alg.flow.mincost.MinimumCostFlowProblem;
import org.jgrapht.alg.interfaces.MinimumCostFlowAlgorithm;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import org.jgrapht.alg.flow.mincost.CapacityScalingMinimumCostFlow;

import org.onlab.packet.EthType;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.core.GroupId;
import org.onosproject.net.*;
import org.onosproject.net.behaviour.MeterQuery;
import org.onosproject.net.flow.*;
import org.onosproject.net.config.basics.BasicLinkConfig;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.group.*;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.meter.*;
import org.onosproject.net.meter.Meter.Unit;
import org.onosproject.net.pi.runtime.PiMeterCellConfig;
import org.onosproject.net.topology.HopCountLinkWeigher;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.Topology;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.onosproject.net.device.DeviceService;
import org.onosproject.net.topology.TopologyService;

//time stamp
//import org.onosproject.store.service.LogicalClockService;
import org.onosproject.core.HybridLogicalClockService;

import java.util.ArrayList;
import java.util.List;

@Component(immediate = true,
        service = {SomeInterface.class}
)

public class ONOSAppMainComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topoService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HybridLogicalClockService hybridLogicalClockService;










    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Activate
    protected void activate(){
        log.info("Started Framework App - rik");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped Framework App - rik");
    }


    @Override
    public String getTopologyDevices() {

        Iterable<Device> devices = deviceService.getAvailableDevices();

        for (Device d : devices) {
            System.out.println(d.toString());
        }
        return "worksDevices";
    }

    @Override
    public String getTopologyElements() {

        Topology topo = topoService.currentTopology();

        System.out.println("Current Epoch & Unix Timestamp:"+  hybridLogicalClockService.timeNow().logicalTime() );
        System.out.println("topology:"+topo.toString() );
        System.out.println("Notes:\n" +
                "computeCost:\n" +
                "Returns the time, specified in system nanos of how long the topology took to compute.\n" +
                "creationTime:\n" +
                "Returns the time, specified in system millis of when the topology became available.");
        System.out.println("cluster of current topology: "+ topoService.getClusters(topo).toString() );



        return "worksAuto";
    }

    @Override
    public String getTopologyHosts() {

        Iterable<Host> hosts = hostService.getHosts();
        System.out.println("Host Count: "+hostService.getHostCount());

        for (Host h : hosts) {
            System.out.println(h.toString() );
            //ConnectPoint cc = h.location();
            //System.out.println(cc);
        }
        return "worksHost";
    }

    @Override
    public String getTopologyLinks() {

        //links with "active" state between devices (included (a to b) link and (b to a) link repetition)
        Iterable<Link> linksActive = linkService.getActiveLinks();
        //links between devices (included (a to b) link and (b to a) link repetition)
        Iterable<Link> links = linkService.getLinks();


        System.out.println("Link Count "+ linkService.getLinkCount());
        for (Link l : links) {
            System.out.println("Link:" + l.toString());

            //remove
            LinkKey lk = LinkKey.linkKey(l);
            BasicLinkConfig bb = new BasicLinkConfig(lk);
            System.out.println("BW?:"+bb.type() + "LK??:"+lk.toString());
        }

        //-------PORTS
        Iterable<Device> devices = deviceService.getAvailableDevices();

        //Parse info
        for (Device d : devices) {

            List<Port> ports = deviceService.getPorts(d.id());

            for (Port p : ports) {

                PortStatistics portstatistics = deviceService.getStatisticsForPort(d.id(), p.number());

                //filter out port.type=="LOCAL" ports
                if (portstatistics == null) {
                    continue;
                }

                String deviceUri = null;

                Long bytesReceived = portstatistics.bytesReceived();
                Long bytesSent = portstatistics.bytesSent();
                Long portAliveTimeSec = portstatistics.durationSec();
                Long packetsReceived = portstatistics.packetsReceived();
                Long packetsSent = portstatistics.packetsSent();


                Long dropCountRx = portstatistics.packetsRxDropped();
                Long dropCountTx = portstatistics.packetsTxDropped();
                Long errorCountRx = portstatistics.packetsRxErrors();
                Long errorCountTx = portstatistics.packetsTxErrors();

                Long portNumber = p.number().toLong();
                deviceUri = d.id().uri().toString();

                System.out.println(p.toString());

                System.out.print("Packets Received:"+ packetsReceived);
                System.out.println(" & Packets Sent:"+packetsSent);
                System.out.print("Bytes Received:"+ bytesReceived);
                System.out.println(" & Bytes Sent:"+bytesSent);

                System.out.print("Packets RX Dropped :"+ dropCountRx );
                System.out.println(" & TX Dropped:"+ dropCountTx );
                System.out.print("Packets RX Error:"+ errorCountRx );
                System.out.println(" & TX Error:"+ errorCountTx );

                System.out.println("Durantion:"+portAliveTimeSec);
                System.out.println();
            }




        }


        return "worksLink";
    }

    @Override
    public String getTopologyFlow() {

        Iterable<Device> devices = deviceService.getAvailableDevices();
        for (Device d : devices) {
            System.out.println("-----Type:"+d.type()+" ID:"+d.id());
            System.out.println("Flow rule count:"+ flowRuleService.getFlowRuleCount());
            System.out.println("Flow:"+ flowRuleService.getFlowEntries(d.id()).toString() );

            for (FlowEntry fr : flowRuleService.getFlowEntries(d.id()) ) {
               // System.out.println("Flow H Timeout:"+ fr.hardTimeout() );
                //System.out.println("Flow Timeout:"+ fr.timeout() );
                //flows dos ping tem timeout : 10 + HardTimeout: 0
            }

            System.out.println("\n");
        }

        return "worksFlow";
    }

    public void testeJGraph(){



        /*Graph<String, DefaultEdge> ggg = new SimpleGraph<>(DefaultEdge.class);

        String v1 = "v1";
        String v2 = "v2";
        String v3 = "v3";
        String v4 = "v4";

        // add the vertices
        ggg.addVertex(v1);
        ggg.addVertex(v2);
        ggg.addVertex(v3);
        ggg.addVertex(v4);

        // add edges to create a circuit
        ggg.addEdge(v1, v2);
        ggg.addEdge(v2, v3);
        ggg.addEdge(v3, v4);
        ggg.addEdge(v4, v1);


        // note undirected edges are printed as: {<v1>,<v2>}
        System.out.println("-- toString output");
        System.out.println(ggg.toString());
        System.out.println();*/



    }

    @Override
    public String testar() {

        /*byte[] byt = {(byte) 1};
        GroupKey gKey = new DefaultGroupKey(byt);
        DefaultApplicationId appId = new DefaultApplicationId((short)  2, "fillername_henrique");


        groupService.removeGroup(DeviceId.deviceId("of:0000000000000001"),gKey, appId);*/

        log.info(groupService.getGroups(DeviceId.deviceId("of:0000000000000001")).toString());
        log.info(groupService.getGroups(DeviceId.deviceId("of:000000000000000b")).toString());
        log.info(groupService.getGroups(DeviceId.deviceId("of:0000000000000016")).toString());


        return "testar";

    }

    public String testar1() {
        groupService.purgeGroupEntries();


        //  os.system(
        //        'sudo ovs-ofctl -O OpenFlow13  add-group s1 group_id=1001,type=select,bucket=weight:1,output:101')
        DeviceId dID =  DeviceId.deviceId("of:0000000000000001");
        //group Buckets
        TrafficTreatment.Builder trafficTreatBuild = DefaultTrafficTreatment.builder();
        trafficTreatBuild.setOutput(PortNumber.portNumber(101));
        TrafficTreatment treat = trafficTreatBuild.build();
        GroupBucket bucket = DefaultGroupBucket.createSelectGroupBucket(treat, (short) 1);

        List<GroupBucket> buckets = new ArrayList<>();
        buckets.add(bucket);

        GroupBuckets gBucket = new GroupBuckets(buckets);

        // GroupKey appCookie, Integer groupId, ApplicationId appId)

        //app cokie
        byte[] byt = {(byte) 1};
        GroupKey gKey = new DefaultGroupKey(byt);

        //group ID
        int gID = 1001;

        //App ID
        DefaultApplicationId appId = new DefaultApplicationId((short)  2, "fillername_henrique");

        GroupDescription groupDescription = new DefaultGroupDescription(dID, GroupDescription.Type.SELECT, gBucket, gKey, gID, appId);
        groupService.addGroup(groupDescription);


        //Print Groups
        log.info(groupService.getGroups(dID).toString());

        //os.system('sudo ovs-ofctl -O OpenFlow13  add-flow s1 in_port=1002,dl_type=0x800,actions=group:1001')
        addRule1();

        return "testar";
    }

    public void addRule1(){

        DefaultFlowRule.Builder frbuilder = new DefaultFlowRule.Builder();

        //device ID
        frbuilder.forDevice(DeviceId.deviceId("of:0000000000000001"));

        //App Id
        DefaultApplicationId appId = new DefaultApplicationId((short)  2, "fillername_henrique");
        frbuilder.fromApp(appId);

        //Timeout
        frbuilder.makePermanent();

        //Priority
        frbuilder.withPriority(45000);


        //Mach / Selector
        TrafficSelector.Builder trafficSelectorBuild = DefaultTrafficSelector.builder();
        trafficSelectorBuild.matchInPort(PortNumber.portNumber("1002"));
        trafficSelectorBuild.matchEthType(EthType.EtherType.IPV4.ethType().toShort());

        TrafficSelector selector = trafficSelectorBuild.build();
        frbuilder.withSelector(selector);



        //Action
        TrafficTreatment.Builder trafficTreatBuild = DefaultTrafficTreatment.builder();
        trafficTreatBuild.group(GroupId.valueOf(1));

        TrafficTreatment treat = trafficTreatBuild.build();
        frbuilder.withTreatment(treat);

        //ADD RULE
        FlowRule fr = frbuilder.build();
        log.info("Added in sw1 'ooooooo1' the rule:"+fr.toString() );
        flowRuleService.applyFlowRules(fr);


    }

    public String testar2() {
        groupService.purgeGroupEntries();

        //os.system(
        //'sudo ovs-ofctl -O OpenFlow13  add-group s1 group_id=1001,type=select,bucket=weight:9,output:101,bucket=weight:10,output:102')
        //os.system('sudo ovs-ofctl -O OpenFlow13  add-flow s1 in_port=1002,dl_type=0x800,actions=group:1001')
        //os.system(


        DeviceId dID =  DeviceId.deviceId("of:000000000000000b");
        //group Buckets
        TrafficTreatment.Builder trafficTreatBuild = DefaultTrafficTreatment.builder();
        trafficTreatBuild.setOutput(PortNumber.portNumber(101));
        TrafficTreatment treat1 = trafficTreatBuild.build();

        TrafficTreatment.Builder trafficTreatBuild2 = DefaultTrafficTreatment.builder();
        trafficTreatBuild2.setOutput(PortNumber.portNumber(102));
        TrafficTreatment treat2 = trafficTreatBuild2.build();


        GroupBucket bucket1 = DefaultGroupBucket.createSelectGroupBucket(treat1, (short) 9);
        GroupBucket bucket2 = DefaultGroupBucket.createSelectGroupBucket(treat2, (short) 10);

        List<GroupBucket> buckets = new ArrayList<>();
        buckets.add(bucket1);
        buckets.add(bucket2);

        GroupBuckets gBucket = new GroupBuckets(buckets);


        //app cokie
        byte[] byt = {(byte) 1};
        GroupKey gKey1 = new DefaultGroupKey(byt);

        //group ID
        int gID1 = 1001;

        //App ID
        DefaultApplicationId appId1 = new DefaultApplicationId((short)  2, "fillername_henrique");

        GroupDescription groupDescription = new DefaultGroupDescription(dID, GroupDescription.Type.SELECT, gBucket, gKey1, gID1, appId1);
        groupService.addGroup(groupDescription);


        //Print Groups
        log.info(groupService.getGroups(dID).toString());


        addRule_2("of:000000000000000b");

        //-----------------------------------------
        //-----------------------------------------
        //s2
        //-----------------------------------------
        //-----------------------------------------


        //        'sudo ovs-ofctl -O OpenFlow13  add-group s2 group_id=1001,type=select,bucket=output:101')
        //os.system('sudo ovs-ofctl -O OpenFlow13  add-flow s2 in_port=1002,dl_type=0x800,actions=group:1001')


        DeviceId dID_2 =  DeviceId.deviceId("of:0000000000000016");
        //group Buckets
        TrafficTreatment.Builder trafficTreatBuild_2 = DefaultTrafficTreatment.builder();
        trafficTreatBuild_2.setOutput(PortNumber.portNumber(101));
        TrafficTreatment treat_2 = trafficTreatBuild_2.build();


        GroupBucket bucket_2 = DefaultGroupBucket.createSelectGroupBucket(treat_2, (short) 1);

        List<GroupBucket> buckets_2 = new ArrayList<>();
        buckets_2.add(bucket_2);

        GroupBuckets gBucket_2 = new GroupBuckets(buckets_2);

        //app cokie
        GroupKey gKey_2 = new DefaultGroupKey(byt);

        //group ID
        int gID_2 = 1001;

        //App ID
        DefaultApplicationId appId_2 = new DefaultApplicationId((short)  2, "fillername_henrique");



        GroupDescription groupDescription_2 = new DefaultGroupDescription(dID_2, GroupDescription.Type.SELECT, gBucket_2, gKey_2, gID_2, appId_2);
        groupService.addGroup(groupDescription_2);


        //Print Groups
        log.info(groupService.getGroups(dID_2).toString());




        //os.system('sudo ovs-ofctl -O OpenFlow13  add-flow s1 in_port=1002,dl_type=0x800,actions=group:1001')
        addRule_2("of:0000000000000016");

        return "testar";
    }

    public void addRule_2(String strDevice){

        DefaultFlowRule.Builder frbuilder = new DefaultFlowRule.Builder();

        //device ID
        frbuilder.forDevice(DeviceId.deviceId(strDevice));

        //App Id
        DefaultApplicationId appId = new DefaultApplicationId((short)  2, "fillername_henrique");
        frbuilder.fromApp(appId);

        //Timeout
        frbuilder.makePermanent();

        //Priority
        frbuilder.withPriority(45000);


        //Mach / Selector
        TrafficSelector.Builder trafficSelectorBuild = DefaultTrafficSelector.builder();
        trafficSelectorBuild.matchInPort(PortNumber.portNumber("1002"));
        trafficSelectorBuild.matchEthType(EthType.EtherType.IPV4.ethType().toShort());

        TrafficSelector selector = trafficSelectorBuild.build();
        frbuilder.withSelector(selector);



        //Action
        TrafficTreatment.Builder trafficTreatBuild = DefaultTrafficTreatment.builder();
        trafficTreatBuild.group(GroupId.valueOf(1001));

        TrafficTreatment treat = trafficTreatBuild.build();
        frbuilder.withTreatment(treat);

        //ADD RULE
        FlowRule fr = frbuilder.build();
        log.info("Added in SW2 "+strDevice+" the rule:"+fr.toString() );
        flowRuleService.applyFlowRules(fr);


    }

}


