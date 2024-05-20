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
package org.uc.dei.mei.framework.algorithm;


import org.helper.*;
import org.jgrapht.Graph;
import org.jgrapht.alg.flow.mincost.CapacityScalingMinimumCostFlow;
import org.jgrapht.alg.flow.mincost.MinimumCostFlowProblem;
import org.jgrapht.alg.interfaces.MinimumCostFlowAlgorithm;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.onlab.graph.Edge;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.core.GroupId;
import org.onosproject.core.HybridLogicalClockService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uc.dei.mei.framework.onospath.PathInterface;

import javax.xml.stream.Location;
import java.util.*;
import java.util.function.Function;

import static org.onosproject.cli.AbstractShellCommand.get;

@Component(immediate = true,
        service = {AlgorithmInterface.class}
)

public class ONOSAppAlgorithm implements AlgorithmInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private AlgoHelper algoHelper;
    private Object mutex;
    private Long lastUpdateLoss;

    private CustomPacketProcessor processor = new CustomPacketProcessor();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HybridLogicalClockService hybridLogicalClockService;


    @Activate
    protected void activate() {
        algoHelper = new AlgoHelper(log, deviceService, linkService, hostService, flowRuleService, hybridLogicalClockService);
        packetService.addProcessor(processor, PacketProcessor.director(3));
        mutex = new Object();
        log.info("Started Algorithm Component - rik");
        lastUpdateLoss = hybridLogicalClockService.timeNow().logicalTime();
    }



    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped Algorithm Component - rik");
    }


    public void getAlgos(){
        System.out.println("'3-obj-fairness' : Min-cost-flow Greedy Heuristic that consider fairness regarding " +
                "energy comsumption, delay and loss probability;");
        System.out.println("'onos-k-short' : K-shortest path that minimizes energy cost;");
    }


    public String pullDBConf(){
        //algoHelper.getDBGeneral_conf();
        //algoHelper.updateEnergyTable();
        //algoHelper.getDBService_conf();
        //return "Updated the framework with the more recent configurations";
        return "nothing done";
    }

    public String seePullDBConf(){
        System.out.println("prior: "+algoHelper.defPriorityDB);
        System.out.println("AppID: "+algoHelper.defAppIDDB);
        System.out.println("timeout: "+algoHelper.defTimeoutDB);
        System.out.println("wired bw:"+algoHelper.bw_wired_mbsDB);
        System.out.println("wireless:"+algoHelper.bw_wireless_mbsDB);
        System.out.println("algorithm: "+algoHelper.default_fw_algorithm);
        System.out.println("video: "+algoHelper.datarates.get("video"));
        System.out.println("voice: "+algoHelper.datarates.get("voice"));
        System.out.println("BUD: "+algoHelper.datarates.get("BUD"));
        System.out.println("experimental: "+algoHelper.experimental);
        System.out.println("noeBoll: "+algoHelper.noeBoll);
        return "seePulled";
    }


    @Override
    public void hardUpdateLoss() {

        double adjust = algoHelper.ajustLLDPPlus(DeviceId.deviceId("of:0000000000000001") );
        algoHelper.updateLoss(adjust);
    }
    @Override
    public String getStoredLoss() {

        String str = "\n";
        for (Node nn : algoHelper.trackingGrafo.getNodes().values()) {
            for (LinkT lt : nn.getLinks().values()) {
                str = str + lt.getEdgeToString() + " - loss:" + lt.getLossProb() + "\n";
            }
        }
        return str;
    }




    @Override
    public void setNoe(boolean noee){
        if(algoHelper.noeBoll == true){
            algoHelper.noeBoll = false;
        }else{
            algoHelper.noeBoll = true;
        }

    }
    @Override
    public boolean getNoe(){
        return algoHelper.noeBoll;
    }




    @Override
    public void addProcess(){
        packetService.addProcessor(processor, PacketProcessor.director(3));
        log.info("added");

    }
    @Override
    public void removeProcess(){
        packetService.removeProcessor( processor);
        log.info("removed");
    }



    @Override
    public void resetFPathHistoy(){
        algoHelper.pathsHistory.put("3-obj-fairness", new ArrayList<String>());
    }
    @Override
    public void resetOPathHistory(){
        algoHelper.pathsHistory.put("onos-k-short", new ArrayList<String>());
    }

    @Override
    public ArrayList<String> getPHistF(){
        return algoHelper.pathsHistory.get("3-obj-fairness");
    }
    @Override
    public ArrayList<String> getPHistO(){
        return algoHelper.pathsHistory.get("onos-k-short");

    }


    @Override
    public String getAllTotalBW() {

         String str = "\n";
         for (Node nn : algoHelper.trackingGrafo.getNodes().values()) {
             for (LinkT lt : nn.getLinks().values()) {
                 str = str + lt.getEdgeToString() + " - Total BW:" + lt.getBwTotalMbts() + "\n";
             }
         }
         return str;
     }

     @Override
     public void setLinkLossG(String src, String dst, String value){
        //(sw24 : sw22)
        String edgeStrIn = "("+src+" : "+dst+")";

        for (Node nn : algoHelper.trackingGrafo.getNodes().values()) {
            for (LinkT lt : nn.getLinks().values()) {
                if(lt.getEdgeToString().equals(edgeStrIn)){
                    lt.setLossProb(Double.parseDouble(value) );
                    return;
                }
            }
        }
    }



    @Override
    public void getLinkLossDEBUG(String src, String dst){
        //(sw24 : sw22)
        String edgeStrIn = "("+src+" : "+dst+")";

        for (Node nn : algoHelper.trackingGrafo.getNodes().values()) {
            for (LinkT lt : nn.getLinks().values()) {
                if(lt.getEdgeToString().equals(edgeStrIn)){

                    double adjust = algoHelper.ajustLLDPPlus(DeviceId.deviceId("of:0000000000000001") );
                    System.out.println("loss link:"+ algoHelper.calcLoss(lt,adjust) );

                    return;
                }
            }
        }
    }






    @Override
    public ArrayList<String> getFairPath(String service_str){

        Graph<String, DefaultWeightedEdge> grafo = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        //Nodes
        for ( Node nn : algoHelper.trackingGrafo.getNodes().values()) {
            grafo.addVertex(nn.getNodeName());
        }

        //Links
        for ( Node nn : algoHelper.trackingGrafo.getNodes().values()) {
            for (LinkT ll : nn.getLinks().values()) {
                grafo.addEdge(ll.getNodeSrc().getNodeName(), ll.getNodeDst().getNodeName());

                double cost = algoHelper.calcCost(ll, service_str);
                grafo.setEdgeWeight(ll.getNodeSrc().getNodeName(), ll.getNodeDst().getNodeName() ,cost);
            }
        }



        Function<String, Integer> funcSupply = (str) -> {
            //service
            /*
             * video s1->s4
             * voice s2->s5
             * BUD   s3->s6
             * */

            if(str.equals(algoHelper.currentStrSupply)){
                return 1;
            }else if(str.equals(algoHelper.currentStrDemand)){
                return -1;
            }else{
                return 0;
            }

        };

        Function<DefaultWeightedEdge, Integer> funcCapUp = (x) -> {

            LinkT linkT = null;
            for ( Node nn: algoHelper.trackingGrafo.getNodes().values() ) {
                for (LinkT ll : nn.getLinks().values()) {
                    if(x.toString().equals(ll.getEdgeToString())){
                        //find the link in our data structure
                        linkT = ll;
                    }
                }
            }


            double datarate = algoHelper.datarates.get(service_str);


            if( (linkT.getBwAvbleMbts() - datarate) > 0 ){
                return 1;
            }else{
                return 0;
            }


        };

        Function<DefaultWeightedEdge, Integer> funcCapLw= (x) -> 0;


        MinimumCostFlowProblem mcp = new MinimumCostFlowProblem.MinimumCostFlowProblemImpl(grafo, funcSupply, funcCapUp, funcCapLw);


        CapacityScalingMinimumCostFlow csm = new CapacityScalingMinimumCostFlow();
        MinimumCostFlowAlgorithm.MinimumCostFlow mf = csm.getMinimumCostFlow(mcp);


        System.out.println(" Cost:"+mf.getCost());


        String[] result = csm.getFlowMap().toString().replace("}","").replace("{","").split(", ");
        ArrayList<String> stringList = new ArrayList<String>(Arrays.asList(result));

        return stringList;
    }


    @Override
    public ArrayList<String> getONOSShortPath(String service_str){

        PathInterface pathService = get(PathInterface.class);

        String SwSrcUri = algoHelper.trackingGrafo.getNodes().get(algoHelper.currentStrSupply).getNodeUri();
        String SwDstUri = algoHelper.trackingGrafo.getNodes().get(algoHelper.currentStrDemand).getNodeUri();

        Path solutionpath = pathService.getKAlgo(DeviceId.deviceId(SwSrcUri), DeviceId.deviceId(SwDstUri), service_str, algoHelper );
        //ScalarWeight{value=3.0}
        //[DefaultEdgeLink{src=AA:BB:CC:DD:00:06/None/0, dst=of:0000000000000006/2, type=EDGE, state=ACTIVE, expected=false}, DefaultLink{src=of:0000000000000006/5, dst=of:0000000000000003/7, type=DIRECT, state=ACTIVE, expected=false}, DefaultEdgeLink{src=of:0000000000000003/2, dst=AA:BB:CC:DD:00:03/None/0, type=EDGE, state=ACTIVE, expected=false}]
        log.info("Cost Solution Parth:"+solutionpath.weight());
        log.info("Links Solution Path:"+solutionpath.links());


        //EnergyLinkWeigherAlgo energyLinkWeigherAlgo = new EnergyLinkWeigherAlgo(service_str, dataSource, algoHelper);
        EnergyLinkWeigherAlgo energyLinkWeigherAlgo = new EnergyLinkWeigherAlgo(service_str, algoHelper);

        log.info("Datarate:"+energyLinkWeigherAlgo.getDatarate());
        log.info("Cost link 0:"+energyLinkWeigherAlgo.calcEnergyCost(solutionpath.links().get(0), true) );

        ArrayList<String> solutionParsed= algoHelper.convert_solutionONOS_arrayList(solutionpath);
        //eg. {(sw1 : swc38)=1.0, (swc41 : sw20)=1.0, (swcc37 : swc41)=1.0, (swc38 : swcc37)=1.0}
        //e.g {(sw1 : sw4)=1.0}

        return solutionParsed;
    }


    @Override
    public double getPacktDiffLink(String nodeNameSrc, String nodeNameDst){
        double[] packets;
        packets =  algoHelper.getPacketSent_Received(algoHelper.trackingGrafo.getNodes().get(nodeNameSrc).getLinks().get(nodeNameDst) );
        double sent = packets[0], received = packets[1];
        return sent - received;
    }


    @Override
    public double getPacktDiffSW(String nodeNameSrc, String nodeNameDst){
        double[] packets;
        packets =  algoHelper.getSumSent_SumReceived( DeviceId.deviceId( algoHelper.trackingGrafo.getNodes().get(nodeNameSrc).getNodeUri() )  );
        double sent = packets[0], received = packets[1];
        return received - sent;
    }




    private class CustomPacketProcessor implements PacketProcessor {


        /*
        * "Application with higher priority (larger number) gets the packet earlier."-> MAS VERIFIQUEI QUE: PacketProcessor.director(1) -> PacketProcessor.director(2)
        *  -> PacketProcessor.director(3)->4-> ETC
        * o process do Reactive forwarding is bloquing the detection of arp packets in this process -> faz  context.block();
        * */
        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            PortNumber inPort = pkt.receivedFrom().port();

            if (ethPkt == null) {
                return;
            }

            if(ethPkt.getEtherType() != Ethernet.TYPE_IPV4){
                //This processor only handles ipv4 packets
                return;
            }

            /*
            log.info("Packet In:" + pkt.toString());
            */

            //Get packet information
            if(ethPkt.getSourceMAC().toString().equals("ffffffffffff") || ethPkt.getDestinationMAC().toString().equals("ffffffffffff") ){
                //LEAVE if there is a problem with MAC
                log.info("BAD Mac, leaving custom processing");
                return;
            }

            Set<Host> hSrc = hostService.getHostsByMac(ethPkt.getSourceMAC());
            Set<Host> hDrc = hostService.getHostsByMac(ethPkt.getDestinationMAC());


            /*dl_src: aabbccdd000f
            dl_dst: aabbccdd0010
            nw_src: 10.0.0.15
            nw_dst: 10.0.0.16*/


           if (hSrc.size() != 1 && hDrc.size() != 1 ){
               throw new ExceptionInInitializerError();
           }

            MacAddress macSrc = hSrc.iterator().next().mac();
            MacAddress macDst = hDrc.iterator().next().mac();


            HostLocation locSrc = hSrc.iterator().next().location();
            HostLocation locDst = hDrc.iterator().next().location();


            //of:000001 -> sw1
            String strCustomSrc = algoHelper.convert_SwUri_StrCustom(locSrc.deviceId().uri().toString());
            String strCustomDst = algoHelper.convert_SwUri_StrCustom(locDst.deviceId().uri().toString());



            log.info("--------\n"+strCustomSrc+" ("+ethPkt.getSourceMAC()+")\n"+strCustomDst+"("+ethPkt.getDestinationMAC()+")\n..........\n");


            String serviceStr = null; //algoHelper.getDBTrafficService(macSrc.toString(), macDst.toString());

            //log.info("Service:"+serviceStr);
            if(serviceStr==null) {
                log.info("could identify Service, leaving custom processing");
                return;
            }


            //Checks if some Flowrules are old (and need to be removed) + if some BW can be given back to links
            algoHelper.checkForOldRules();



            //verifies if some prior packet wasn't already installed flow rules in the device
            synchronized (mutex) {

                //sw1 -> sw4
                algoHelper.currentStrSupply = strCustomSrc;
                algoHelper.currentStrDemand = strCustomDst;

                algoHelper.currentHS = macSrc.toString();
                algoHelper.currentHD = macDst.toString();


                //so atualiza a loss de x em x millisegundos
                //TODO
                if(false && algoHelper.noeBoll == false && ( (hybridLogicalClockService.timeNow().logicalTime() - lastUpdateLoss) > 2000) ){
                    //Update the loss values for each link
                    lastUpdateLoss = hybridLogicalClockService.timeNow().logicalTime();
                    log.info("-+-+-+-UPDATE LOSS");

                    //usar o valor dos pacotes do lldp do sw1 para ajustar todos
                    double adjsut = algoHelper.ajustLLDPPlus(DeviceId.deviceId("of:0000000000000001"));
                    algoHelper.updateLoss(adjsut);

                   /*
                    if(algoHelper.currentHS.equals("AA:BB:CC:DD:00:06") && algoHelper.currentHD.equals("AA:BB:CC:DD:00:0F") ){
                        setLinkLossG("sw2","swc39","0.0");
                        setLinkLossG("swc39","sw5","0.0");
                    }*/

                }

                //Gets the more recent DB values
                //COMMENTED TO SAVE TIME
                //algoHelper.getDBGeneral_conf();


                //COMMENTED TO SAVE TIME
                //algoHelper.getDBService_conf();


                //Gets the more recent DB values
                //COMMENTED TO SAVE TIME
                //algoHelper.updateEnergyTable();


                //Get active FW algorithm
                String active_algorithm = algoHelper.default_fw_algorithm;
                //log.info("active_algorithm: "+active_algorithm);

                //RUN the algorithm
                ArrayList<String> solutionMap = null;
                if(active_algorithm.equals("3-obj-fairness")){
                    solutionMap = getFairPath(serviceStr);
                }else if(active_algorithm.equals("onos-k-short")){
                    solutionMap = getONOSShortPath(serviceStr);
                }else{
                    solutionMap = getFairPath(serviceStr);
                }



                //Clean Map---
                Map<String,Double> cleanSolutionMap = algoHelper.cleanSolutionMap(solutionMap);
                //eg. {(sw1 : swc38)=1.0, (swc41 : sw20)=1.0, (swcc37 : swc41)=1.0, (swc38 : swcc37)=1.0}
                //e.g {(sw1 : sw4)=1.0}
                ////log.info(cleanSolutionMap.toString());



                boolean intalled = false;

                //Condition check
                for( ArrayList<FlowRule>  listFR : algoHelper.trackingGrafo.getFlowrulesInstalled() ){

                    //ETH_DST:AA:BB:CC:DD:00:06 e.g.
                    String srcCrit = listFR.get(0).selector().getCriterion(Criterion.Type.ETH_SRC).toString();
                    String dstCrit = listFR.get(0).selector().getCriterion(Criterion.Type.ETH_DST).toString();

                    //AA:BB:CC:DD:00:06 e.g
                    String srcPack = ethPkt.getSourceMAC().toString();
                    String dstPack =ethPkt.getDestinationMAC().toString();

                    //log.info("111 "+srcCrit.equals("ETH_DST:"+srcPack);
                    if(srcCrit.equals("ETH_SRC:"+srcPack) && dstCrit.equals("ETH_DST:"+dstPack)){
                        intalled = true;
                    }
                }


                //Action---
                if(intalled == false){

                    //Update the Current BW in the links utilised in the path
                    algoHelper.takesBW(cleanSolutionMap, serviceStr);

                    ArrayList<FlowRule> newFlowRuleEntry = new ArrayList<FlowRule>();

                    log.info("--------");
                    log.info(strCustomSrc);
                    log.info(strCustomDst);
                    log.info("-------");

                    Long time = hybridLogicalClockService.timeNow().logicalTime();
                    //records the path chosen
                    algoHelper.pathsHistory.get(active_algorithm).add( time.toString() +" - "+ cleanSolutionMap.toString());


                    //install the rules
                    algoHelper.installRulesPath(cleanSolutionMap, inPort, strCustomSrc, strCustomDst, ethPkt.getSourceMAC().toString(), ethPkt.getDestinationMAC().toString(), newFlowRuleEntry );

                }else{
                    ////log.info("useless Pack");
                }

                //para acalmar o exforco do ONOS aqui
                //Tentativa
                /*try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/

            }





        }
    }

}










































