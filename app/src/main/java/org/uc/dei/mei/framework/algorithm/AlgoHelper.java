package org.uc.dei.mei.framework.algorithm;

import org.apache.felix.utils.collections.StringArrayMap;
import org.apache.http.util.Asserts;
import org.helper.Grafo;
import org.helper.LinkT;
import org.helper.Node;
import org.onlab.packet.MacAddress;
import org.onosproject.core.HybridLogicalClockService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.host.HostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uc.dei.mei.framework.flowrule.FlowRuleInterface;

import java.math.BigInteger;
import java.sql.*;
import java.util.*;

import static org.onosproject.cli.AbstractShellCommand.get;

public class AlgoHelper {

    private Logger log;
    private DeviceService deviceService;
    private LinkService linkService;
    private HostService hostService;
    private FlowRuleService flowRuleService;
    private HybridLogicalClockService hybridLogicalClockService;


    public Grafo trackingGrafo;

    protected String currentStrSupply;
    protected String currentStrDemand;

    protected String currentHS;
    protected String currentHD;



    protected int defPriorityDB = 16;
    protected short defAppIDDB = 24;
    protected short defTimeoutDB = 10;//20;
    protected int bw_wired_mbsDB = 1000;
    protected int bw_wireless_mbsDB = 54;
    protected String default_fw_algorithm= "3-obj-fairness";

    //Mbs
    public HashMap<String,Double> datarates;
    private double datarate_video = 1.5;//1500;//1.5;
    private double datarate_voice = 0.0244;//24.4; //0.0244;
    private double datarate_BUD = 12.288;//12288;//(1500KB*1024*8)/1000/1000;//52.0;

    //Energies
    //machine_Type:connectionType:service_datarate:energy_value
    protected HashMap<String,HashMap<String,HashMap<String,Double>>> energy_table;


    //intra, inter
    protected String experimental = "intra";


    protected boolean noeBoll = true;


    protected HashMap<String, ArrayList<String>> pathsHistory;



    /*protected double TCP_buff_bits = 1536000 * 8.0;
    protected double TCP_num = 10.0;

    //delay in seconds
    protected double delay_video_tole = 0.036;
    protected double delay_voice_tole = 0.020;
    //tamanho servico/nwlink <=> (n*l)/bw
    protected double delay_BUD_wired_tole = ((((TCP_buff_bits * TCP_num) / bw_wired_mbsDB) / 1000) / 1000); //+-122ms
    protected double delay_BUD_wireless_tole = ((((TCP_buff_bits * TCP_num) / bw_wireless_mbsDB) / 1000) / 1000); //+-2275ms
    // 1000-> 0.01536s (15.3ms)
    // 54-> 0.2844s (284.4ms)*/


    //protected double atarate_BUD_wired = 12.288;//12288;//(1500*1024*8)/1000/1000;//52.0;
    //protected double atarate_BUD_wireless = 12.288;//12288;//51.4;


    public AlgoHelper(Logger log, DeviceService deviceService, LinkService linkService, HostService hostService, FlowRuleService flowRuleService, HybridLogicalClockService hybridLogicalClockService/*, DataSource dataSource*/) {
        this.log = log;
        this.deviceService = deviceService;
        this.linkService = linkService;
        this.hostService = hostService;
        this.flowRuleService = flowRuleService;
        this.hybridLogicalClockService = hybridLogicalClockService;


        datarates = new HashMap<String,Double>();
        datarates.put("video",datarate_video);
        datarates.put("voice",datarate_voice);
        datarates.put("BUD",datarate_BUD);

        //updateEnergyTable();
        //getDBGeneral_conf();

        log.info("Noe bool:"+noeBoll);

        pathsHistory = new HashMap<String, ArrayList<String>>();
        pathsHistory.put("3-obj-fairness", new ArrayList<String>());
        pathsHistory.put("onos-k-short", new ArrayList<String>());
        //pathsHistory.get("3-fair").add();



        System.out.println("Create personal tracking graph");
        trackingGrafo = setPersonalTrackingGraph(6, 6, 3);



    }

    protected double[] getPacketSent_Received(LinkT linkT) {
        double[] packets = {0.0, 0.0};


        Device devSrc = deviceService.getDevice(DeviceId.deviceId(linkT.getNodeSrc().getNodeUri()));
        Device devDst = deviceService.getDevice(DeviceId.deviceId(linkT.getNodeDst().getNodeUri()));

        Set<Link> setLinks = linkService.getDeviceEgressLinks(devSrc.id());

        PortNumber portSrc = null;
        PortNumber portDst = null;

        for (Link ll : setLinks) {

            /*log.info("ll.dst():  "+ll.dst());
            log.info("ll.dst().deviceid():  "+ll.dst().deviceId());
            log.info("devDst.id()  :"+ devDst.id());
            */
            if (ll.dst().deviceId().equals(devDst.id())) {
                portSrc = ll.src().port();
                portDst = ll.dst().port();
                break;
            }
        }

        PortStatistics portstatisticsSrc = deviceService.getStatisticsForPort(devSrc.id(), portSrc);
        PortStatistics portstatisticsDst = deviceService.getStatisticsForPort(devDst.id(), portDst);

        packets[0] = portstatisticsSrc.packetsSent();
        packets[1] = portstatisticsDst.packetsReceived();
        System.out.println("node " + devSrc.id() + " Port:" + portSrc + " sent: " + packets[0]);
        System.out.println("node " + devDst.id() + " Port:" + portDst + " received: " + packets[1]);


        return packets;
    }

    protected double[] getSumSent_SumReceived(DeviceId deviceId) {
        double[] packets = {0.0, 0.0};


        List<PortStatistics> statlist = deviceService.getPortStatistics(deviceId);
        for ( PortStatistics pstat: statlist) {
            packets[0] = packets[0] + pstat.packetsSent();
            packets[1] = packets[1] + pstat.packetsReceived();
        }


        System.out.println("node " + deviceId + " Sent SUM:     " + packets[0]);
        System.out.println("node " + deviceId + " Received SUM: " + packets[1]);


        return packets;
    }


    //6 6 #1
    protected Grafo setPersonalTrackingGraph(int numClusters, int numSW, int numHosts) {

        Grafo gg = new Grafo();

        //-----------Nodes


        //SW
        int swIndex = 1;
        for (; swIndex <= numClusters * numSW; swIndex++) {

            String nodeName = "sw" + swIndex;
            String hexa = Integer.toHexString(swIndex);
            int num = 16 - hexa.length();
            String nodeURI = "of:" + String.format("%1$" + num + "s", "").replace(' ', '0') + Integer.toHexString(swIndex);


            String machineType = "";
            String nodeType = "sw";
            if (swIndex % 2 == 0) {
                //is pair
                machineType = "Cubi";
            } else {
                machineType = "PI";
            }

            //1,2,3,4,5,6
            int clusterIndex = ((swIndex - 1) / numSW) + 1;

            Node nn = new Node(nodeName, nodeURI, machineType, nodeType, clusterIndex);
            gg.getNodes().put(nn.getNodeName(), nn);

        }

        //SWCC1
        String nodeName1 = "swcc" + swIndex;
        String hexa1 = Integer.toHexString(swIndex);
        int num1 = 16 - hexa1.length();
        String nodeURI1 = "of:" + String.format("%1$" + num1 + "s", "").replace(' ', '0') + Integer.toHexString(swIndex);

        String machineType1 = "PI";
        String nodeType1 = "swcc";

        Node nn1 = new Node(nodeName1, nodeURI1, machineType1, nodeType1, -1);
        gg.getNodes().put(nn1.getNodeName(), nn1);
        swIndex++;



        //SWCC2
        String nodeName2 = "swcc" + swIndex;
        String hexa2 = Integer.toHexString(swIndex);
        int num2 = 16 - hexa2.length();
        String nodeURI2 = "of:" + String.format("%1$" + num2 + "s", "").replace(' ', '0') + Integer.toHexString(swIndex);

        String machineType2 = "PI";
        String nodeType2 = "swcc";

        Node nn2 = new Node(nodeName2, nodeURI2, machineType2, nodeType2, -1);
        gg.getNodes().put(nn2.getNodeName(), nn2);
        swIndex++;




        //SWC
        for (int i = 0; i < numClusters; i++) {

            String nodeNameSWC = "swc" + (swIndex + i);
            String hexaSWC = Integer.toHexString(swIndex + i);
            int numSWC = 16 - hexaSWC.length();
            String nodeURISWC = "of:" + String.format("%1$" + numSWC + "s", "").replace(' ', '0') + Integer.toHexString(swIndex + i);


            String machineTypeSWC = "PI";
            String nodeTypeSWC = "swc";

            Node nnSWC = new Node(nodeNameSWC, nodeURISWC, machineTypeSWC, nodeTypeSWC, i + 1);
            gg.getNodes().put(nnSWC.getNodeName(), nnSWC);
        }


        //-----------Links

        for (Node node : gg.getNodes().values()) {

            //SW
            if (node.getTypeNode().equals("sw")) {

                for (Node node2 : gg.getNodes().values()) {

                    if (node2.getTypeNode().equals("swc") && node.getClusterIndex() == node2.getClusterIndex()) {
                        double bwTotalMbts = bw_wired_mbsDB;
                        double bwAvbleMbts = bwTotalMbts;
                        double lossProb = -1;//0.1;


                        Node nodeSrc = node;
                        Node nodeDst = node2;
                        String linkType = "wired";

                        LinkT ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
                        ll.setLossProb(calcLoss(ll, 0));

                        node.getLinks().put(node2.getNodeName(), ll);

                    } else if (node2.getTypeNode().equals("sw") && !node.getNodeName().equals(node2.getNodeName()) && node.getClusterIndex() == node2.getClusterIndex()) {

                        //Mesh connections

                        double bwTotalMbts = bw_wireless_mbsDB;
                        double bwAvbleMbts = bwTotalMbts;
                        double lossProb = -1;//1.2;

                        Node nodeSrc = node;
                        Node nodeDst = node2;
                        String linkType = "wireless";

                        LinkT ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
                        ll.setLossProb(calcLoss(ll,0));

                        node.getLinks().put(node2.getNodeName(), ll);
                    }


                }


                //SWC
            } else if (node.getTypeNode().equals("swc")) {

                for (Node node2 : gg.getNodes().values()) {

                    if (node2.getTypeNode().equals("swcc")) {

                        double bwTotalMbts = bw_wired_mbsDB;
                        double bwAvbleMbts = bwTotalMbts;
                        double lossProb = 0.1;

                        Node nodeSrc = node;
                        Node nodeDst = node2;
                        String linkType = "wired";

                        LinkT ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);

                        node.getLinks().put(node2.getNodeName(), ll);

                    } else if (node2.getTypeNode().equals("sw") && node.getClusterIndex() == node2.getClusterIndex()) {

                        double bwTotalMbts = bw_wired_mbsDB;
                        double bwAvbleMbts = bwTotalMbts;
                        double lossProb = -1;//0.1;

                        Node nodeSrc = node;
                        Node nodeDst = node2;
                        String linkType = "wired";

                        LinkT ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
                        ll.setLossProb(calcLoss(ll,0));

                        node.getLinks().put(node2.getNodeName(), ll);
                    }


                }

                //SWCC
            } else if (node.getTypeNode().equals("swcc")) {

                for (Node node2 : gg.getNodes().values()) {

                    if (node2.getTypeNode().equals("swc")) {


                        double bwTotalMbts = bw_wired_mbsDB;
                        double bwAvbleMbts = bwTotalMbts;
                        double lossProb = -1;//0.1;

                        Node nodeSrc = node;
                        Node nodeDst = node2;
                        String linkType = "wired";

                        LinkT ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
                        ll.setLossProb(calcLoss(ll,0));

                        node.getLinks().put(node2.getNodeName(), ll);
                    }


                }


            } else {
                System.out.println("ERRROR making personal links- Henrique");
            }
        }

        return gg;
    }


    protected Grafo setPersonalTrackingGraphDemo(int numClusters, int numSW, int numHosts) {

        Grafo gg = new Grafo();

        //-----------Nodes


        //SW
        int swIndex = 1;
        for (; swIndex <= 4; swIndex++) {

            String nodeName = "sw" + swIndex;
            String hexa = Integer.toHexString(swIndex);
            int num = 16 - hexa.length();
            String nodeURI = "of:" + String.format("%1$" + num + "s", "").replace(' ', '0') + Integer.toHexString(swIndex);


            String machineType = "";
            String nodeType = "sw";

            machineType = "PI";


            Node nn = new Node(nodeName, nodeURI, machineType, nodeType, 1);
            gg.getNodes().put(nn.getNodeName(), nn);

        }




        //-----------Links


        double bwTotalMbts = bw_wired_mbsDB;
        double bwAvbleMbts = bwTotalMbts;
        double lossProb = -1;//0.1;


        Node nodeSrc = gg.getNodes().get("sw1");
        Node nodeDst = gg.getNodes().get("sw2");
        String linkType = "wired";

        LinkT ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeSrc.getLinks().put(nodeDst.getNodeName(), ll);

        ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeDst, nodeSrc, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeDst.getLinks().put(nodeSrc.getNodeName(), ll);



        nodeSrc = gg.getNodes().get("sw1");
        nodeDst = gg.getNodes().get("sw3");
        linkType = "wired";

        ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeSrc.getLinks().put(nodeDst.getNodeName(), ll);

        ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeDst, nodeSrc, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeDst.getLinks().put(nodeSrc.getNodeName(), ll);



        nodeSrc = gg.getNodes().get("sw2");
        nodeDst = gg.getNodes().get("sw4");
        linkType = "wired";

        ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeSrc.getLinks().put(nodeDst.getNodeName(), ll);

        ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeDst, nodeSrc, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeDst.getLinks().put(nodeSrc.getNodeName(), ll);



        nodeSrc = gg.getNodes().get("sw3");
        nodeDst = gg.getNodes().get("sw4");
        linkType = "wired";

        ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeSrc, nodeDst, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeSrc.getLinks().put(nodeDst.getNodeName(), ll);

        ll = new LinkT(bwTotalMbts, bwAvbleMbts, lossProb, nodeDst, nodeSrc, linkType);
        ll.setLossProb(calcLoss(ll, 0));
        nodeDst.getLinks().put(nodeSrc.getNodeName(), ll);


        return gg;
    }





    protected double calcCost(LinkT linkT, String service_str) {

        String connectionString;

        double datarate_service;
        double delay_service;

        double delay_video;
        double delay_voice;
        double delay_BUD;

        /*if (linkT.getLinkType().equals("wired")) {
            datarate_BUD = datarate_BUD_wired;
            delay_BUD = delay_BUD_wired;
        } else {
            datarate_BUD = datarate_BUD_wireless;
            delay_BUD = delay_BUD_wireless;
        }

        if (service_str.equals("voice")) {
            delay_service = delay_voice;
            datarate_service = datarate_voice;
        } else if (service_str.equals("video")) {
            delay_service = delay_video;
            datarate_service = datarate_video;
        } else {
            datarate_service = datarate_BUD;
            delay_service = delay_BUD;
        }*/

        double cpuPI = 0.6191;
        double cpuCubi = 1.037;
        double CPU_m_S,CPU_m_D;
        if(linkT.getNodeSrc().getTypeMachine().equals("PI")){
            CPU_m_S = cpuPI;
        }else{
            CPU_m_S = cpuPI;
        }
        if(linkT.getNodeSrc().getTypeMachine().equals("PI")){
            CPU_m_D = cpuCubi;
        }else{
            CPU_m_D = cpuCubi;
        }





        datarate_service = datarates.get(service_str);


        delay_service = datarate_service/linkT.getBwAvbleMbts();

        delay_video = datarates.get("video")/linkT.getBwAvbleMbts();
        delay_voice = datarates.get("voice")/linkT.getBwAvbleMbts();
        delay_BUD = datarates.get("BUD")/linkT.getBwAvbleMbts();



        if(linkT.getLinkType().equals("wired")){
            connectionString ="energy_wired_idle";
        }else{
            connectionString ="energy_wireless_idle";
        }

        //-------MIN-----------
        double min_res = 0.0;
        double min_delay = 0.0;
        double min_energia = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),service_str, connectionString )+
                             getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(),service_str, connectionString );



        //--------MAX-----------
        double max_res;
        double max_delay;

        double max_energia;
        double vid_ener;
        double voi_ener;
        double bud_ener;


        if (linkT.getLinkType().equals("wired")) {
            vid_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),"video", "energy_wired_u" )+
                    getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), "video", "energy_wired_d" );

            voi_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),"voice", "energy_wired_u" )+
                    getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), "voice", "energy_wired_d" );

            bud_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),"BUD", "energy_wired_u" )+
                    getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), "BUD", "energy_wired_d" );
        } else {
            vid_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(), "video", "energy_wireless_u") +
                    getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), "video", "energy_wireless_d");

            voi_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(), "voice", "energy_wireless_u") +
                    getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), "voice", "energy_wireless_d");

            bud_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(), "BUD", "energy_wireless_u") +
                    getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), "BUD", "energy_wireless_d");
        }


        max_res = 3 * linkT.getLossProb();
        if (max_res == 0.0) { //o x_s da loss pode ser 0, o maximo e que tenho que garantir que nunca e 0 -> 0.1% (um valor qualquer chegava pk no fim vai ficar (0/algo)= 0)
            max_res = 3 * 0.1;
        }
        max_delay = delay_video + delay_voice + delay_BUD;
        max_energia = vid_ener + voi_ener + bud_ener + min_energia + (3* (CPU_m_S + CPU_m_D));


        //--------X_SERVICE -----------
        double x_res = linkT.getLossProb();
        double x_delay = delay_service;
        double x_energia;

        double src_ener;
        double dst_ener;
        if (linkT.getLinkType().equals("wired")) {
            src_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(), service_str, "energy_wired_u" );
            dst_ener = getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), service_str, "energy_wired_d" );
        } else {
            src_ener = getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(), service_str, "energy_wireless_u" );
            dst_ener = getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(), service_str, "energy_wireless_d" );
        }
        x_energia = src_ener + dst_ener + min_energia + CPU_m_S + CPU_m_D;


        //PESO--------------
        double peso_res = (x_res - min_res) / (max_res - min_res);
        double peso_delay = (x_delay - min_delay) / (max_delay - min_delay);
        /*double peso_delay = 1.0;
        if(x_delay < 1.0) {
            peso_delay = x_delay;
        }*/


        double peso_energia = (x_energia - min_energia) / (max_energia - min_energia);


        //if(linkT.getEdgeToString().equals("(swc39 : swcc37)") || linkT.getEdgeToString().equals("(swc39 : swcc38)")){

        //if(linkT.getEdgeToString().equals("(swc41 : sw13)") || linkT.getEdgeToString().equals("(swc41 : sw14)")
        //        || linkT.getEdgeToString().equals("(sw14 : sw13)") || linkT.getEdgeToString().equals("(sw13 : sw14)")){
        //    log.info(".-.-.-.");
        //    log.info(linkT.getEdgeToString()+" > Pe:"+peso_energia+" Pd: "+peso_delay+" Pl:"+peso_res+" ");
        //}



        if( (peso_res > 1.0) || (peso_res < 0.0) ) {
            try {
                throw new Exception("Peso resseliancia fora de range");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if( (peso_delay > 1.0) || (peso_delay < 0.0) ) {
            try {
                throw new Exception("Peso Delay fora de range");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if( (peso_energia > 1.0) || (peso_energia < 0.0) ) {
            try {
                throw new Exception("Peso Energia fora de range");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        return Math.max(peso_res, Math.max(peso_delay, peso_energia));

    }

    protected double calcLossOLD(LinkT linkT) {

        double[] packets;
        packets = getPacketSent_Received(linkT);
        double packetSent = packets[0], packetReceived = packets[1];

        double diff = packetSent - packetReceived;

        //To avoid prblems with duplicage packets, e.g.
        if (diff < 0.0) {
            diff = 0.0;
        }

        double loss = (diff) / packetSent;

        //devolve um valor percentual
        return loss * 100;

    }


    protected  double ajustLLDPPlus(DeviceId dedviceUri ){

        long pckTableLldp;

        Iterable<FlowEntry> fearray = flowRuleService.getFlowEntries( dedviceUri );
        for (FlowEntry fe : fearray) {
            if(fe.selector().getCriterion(Criterion.Type.ETH_TYPE).toString().equals("ETH_TYPE:lldp") ){
                pckTableLldp = fe.packets();
                float numPort = 13;
                float numHost = 7;
                float numSw = numPort - numHost;

                //float percSW = numSw/numPort;

                int packetsLLDPSent_by_Sw = (int) (pckTableLldp * (numPort/numSw));
                System.out.println("adding: ( "+packetsLLDPSent_by_Sw+" - "+pckTableLldp+" )*2 = "+ 2*(packetsLLDPSent_by_Sw - pckTableLldp) );
                //somar o numero de pacotes lldp que os hosts teriam respondia
                return (packetsLLDPSent_by_Sw - pckTableLldp)*2;

            }
        }
        log.info("ERRRRROR ADJUST LLDP -+-+--+-++-+\n-+-+-+-+-+-\n+-++-\n");
        return 0.0;
    }


    protected double calcLoss(LinkT linkT, double adjust) {
        //rec - sent de um sw


        double[] packetsSrc;
        packetsSrc = getSumSent_SumReceived( DeviceId.deviceId( linkT.getNodeSrc().getNodeUri() ));
        //receivedBySW# -  sentBySW#
        double diffSrc = packetsSrc[1] - packetsSrc[0];


        double[] packetsDst;
        packetsDst = getSumSent_SumReceived( DeviceId.deviceId( linkT.getNodeDst().getNodeUri() ));
        //receivedBySW# -  sentBySW#
        double diffDst = packetsDst[1] - packetsDst[0];

        System.out.println("node "+linkT.getNodeSrc().getNodeUri()+" diffSrc  " + diffSrc );
        System.out.println("node "+linkT.getNodeDst().getNodeUri()+" diffDst  " + diffDst );



        if(linkT.getNodeSrc().getTypeNode().equals("sw")){
            //diffSrc+= ajustLLDPPlus( DeviceId.deviceId( linkT.getNodeSrc().getNodeUri()) );
            diffSrc+=adjust;
            System.out.println("node "+linkT.getNodeSrc().getNodeUri()+" current ADJUSTED diffSrc:  " + diffSrc );
        }

        if(linkT.getNodeDst().getTypeNode().equals("sw")){
            //diffDst+= ajustLLDPPlus( DeviceId.deviceId( linkT.getNodeDst().getNodeUri()) );
            diffDst+=adjust;
            System.out.println("node "+linkT.getNodeDst().getNodeUri()+" current ADJUSTED diffDst:  " + diffDst );

        }


        System.out.println("");


        //To avoid prblems with duplicage packets, e.g.
        if (diffSrc < 0.0) {
            diffSrc = 0.0;
        }

        //To avoid prblems with duplicage packets, e.g.
        if (diffDst < 0.0) {
            diffDst = 0.0;
        }



        double lossSrc = (diffSrc) / packetsSrc[1];
        double lossDST = (diffDst) / packetsDst[1];

        if(packetsSrc[1] == 0.0){
            lossSrc = 0.0;
        }

        if(packetsDst[1] == 0.0){
            lossDST = 0.0;
        }

        System.out.println("node "+linkT.getNodeSrc().getNodeUri()+" lossSrc  " + lossSrc );
        System.out.println("node "+linkT.getNodeDst().getNodeUri()+" lossDST  " + lossDST );
        System.out.println("");



        //media
        double loss = (lossSrc + lossDST)/2;

        //devolve um valor percentual
        return loss * 100;
        //return 0.0;

    }

    protected void updateLoss(double adjust){
        for ( Node nn : trackingGrafo.getNodes().values()) {
            for (LinkT ll : nn.getLinks().values()) {

                ll.setLossProb(calcLoss(ll, adjust));

            }
        }
    }




    public ArrayList<String> convert_solutionONOS_arrayList(Path solutionpath){
        ArrayList<String> solution = new ArrayList<String>();

        //eg. {(sw1 : swc38)=1.0, (swc41 : sw20)=1.0, (swcc37 : swc41)=1.0, (swc38 : swcc37)=1.0}
        for ( Link ll: solutionpath.links()) {
            String srcCustom = convert_SwUri_StrCustom(ll.src().deviceId().toString());
            String dstCustom = convert_SwUri_StrCustom(ll.dst().deviceId().toString());

            String strLink = "("+srcCustom+" : "+dstCustom+")=1.0";
            solution.add(strLink);
        }


        return solution;
    }

    public String convert_SwUri_StrCustom(String swURI) {
        //of:0000 00000000001f -> sw31
        String swURIParsed = swURI.substring(7);
        BigInteger value = new BigInteger(swURIParsed, 16);

        String strCustom = "sw" + value.toString();


        //TODO ver se mais tarde da para arranjar
        //Pesimo COdigo......
        //arranja o facto de que assw tem um prefixo diferente
        if(trackingGrafo.getNodes().get(strCustom) == null){
            strCustom = "swc" + value.toString();
        }

        if(trackingGrafo.getNodes().get(strCustom) == null){
            strCustom = "swcc" + value.toString();
        }

        return strCustom;
    }



    public double getEnergyConsumptionFtable(String machineType, String service, String connectionType){
        //machine_Type:connectionType:service_datarate:energy_value
        double energy = this.energy_table.get(machineType).get(connectionType).get(service);
        return energy;
    }

    protected void updateEnergyTable(){
        //machine_Type:connectionType:service_datarate:energy_value
        this.energy_table = new HashMap<String,HashMap<String,HashMap<String,Double>>>();


        ArrayList<String> machineTypes = new ArrayList<String>();
        machineTypes.add("PI");
        machineTypes.add("Cubi");


        ArrayList<String> connectionTypes = new ArrayList<String>();
        connectionTypes.add("energy_wired_d");
        connectionTypes.add("energy_wired_u");
        connectionTypes.add("energy_wireless_d");
        connectionTypes.add("energy_wireless_u");
        connectionTypes.add("energy_wired_idle");
        connectionTypes.add("energy_wireless_idle");


        ArrayList<String> serviceTypes = new ArrayList<String>();
        for ( String service : datarates.keySet()) {
            serviceTypes.add(service);
        }
        //machineTypes.add("video");
        //machineTypes.add("voice");
        //machineTypes.add("BUD");




        for ( String machineType: machineTypes ) {
            energy_table.put(machineType,new HashMap<String,HashMap<String,Double>>());

            for ( String connectionType: connectionTypes ) {
                energy_table.get(machineType).put(connectionType,new HashMap<String,Double>());

                for ( String serviceType: serviceTypes ) {
                    //double energy = getDBEnergyConsumption(machineType, datarates.get(serviceType), connectionType);
                    //energy_table.get(machineType).get(connectionType).put(serviceType,energy);

                }
            }
        }


    }

    //'PI' e.g , video, 'energy_wired_u'
/*    public double getDBEnergyConsumption(String machineType, double datarate, String connectionType){



        String updateDatarate = "update energy set datarate="+datarate+" where name='"+connectionType+"' and machine_name='"+machineType+"'";
        String readEnergy = "select energy_value from energy where name='"+connectionType+"' and machine_name='"+machineType+"'";

        double energy = 0.0;
        try(Connection con = dataSource.getConnection();
            PreparedStatement prepStmtupdate = con.prepareStatement(updateDatarate);
            PreparedStatement prepStmtRead = con.prepareStatement(readEnergy)){

            con.setAutoCommit(false);

            prepStmtupdate.executeUpdate(); //executeQuery();
            ResultSet rs = prepStmtRead.executeQuery();


            if(rs.next() == false){
                throw new Exception();
            }

            energy = rs.getDouble(1);
            //log.info("energy_value: "+energy);

            //commit the transaction if everytihg is fine
            //con.commit();
            //con.setAutoCommit(true);

        } catch (SQLException throwables) {
            log.info("ERROR reading table energy configurations from DataBase. "+ getClass());
            throwables.printStackTrace();
        } catch (Exception e) {
            log.info("ERROR, energy configuration table is empty, using default values for port speed");
        }

        return energy;
    }


    protected String getDBTrafficService(String macSrc, String macDst) {

        String readTableString = "select service_conf_name from service_host_relation where mac_src='"+macSrc+"' and mac_dst='"+macDst+"'";
        try(Connection con = dataSource.getConnection();
            PreparedStatement prepStmt = con.prepareStatement(readTableString)){

            ResultSet rs = prepStmt.executeQuery();

            if(rs.next() == false){
                throw new Exception();
            }

            //0 : idk what it is
            //1 : service_conf_name,
            return rs.getString(1);

        } catch (SQLException throwables) {
            log.info("ERROR reading table service_host_relation from DataBase. "+getClass());
            throwables.printStackTrace();
            return null;
        } catch (Exception e) {
            log.info("ERROR, service_host_relation table is empty/ OR there is no service for this");
            return null;
        }








        //Intra
        /*if (swSrc.equals("sw1") && swDst.equals("sw4")) {
            service = "video";
        } else if (swSrc.equals("sw2") && swDst.equals("sw5")) {
            service = "voice";
        } else if ( (swSrc.equals("sw3") && swDst.equals("sw6") ) || swSrc.equals("sw6") && swDst.equals("sw3") ) {
            service = "BUD";
        }

        //Inter
        Node nodeSrc = trackingGrafo.getNodes().get(swSrc);
        Node nodeDst = trackingGrafo.getNodes().get(swDst);

        if (nodeSrc.getClusterIndex() == 1 && nodeDst.getClusterIndex() == 4) {
            service = "video";
        } else if (nodeSrc.getClusterIndex() == 2 && nodeDst.getClusterIndex() == 5) {
            service = "voice";
        } else if (nodeSrc.getClusterIndex() == 3 && nodeDst.getClusterIndex() == 6) {
            service = "BUD";
        }*/

/*
    }
*/
    protected HashMap<String, Double> cleanSolutionMap(ArrayList<String> solutionMap) {
        HashMap<String,Double> cleanMap = new HashMap<>();

        //solutionMap = [(sw1 : sw4)=0.0,(sw1 : sw5)=0.0,etc]


        for ( String str : solutionMap ) {

            String[] result = str.split("=");

            String edge = result[0];
            String cost = result[1];

            if(cost.equals("0.0")){
                continue;
            }


            //if the edge is being used in the path-> put it in the parsed solutionMap
            cleanMap.put(edge,Double.parseDouble(cost));
        }

        return cleanMap;
    }


    protected void installRulesPath(Map<String,Double> cleanSolutionMap, PortNumber inPort, String strCustomCurrentSw, String strCustomLastSw, String srcMac, String dstMac, ArrayList<FlowRule> newFlowRuleEntry ){
        //eg. {(sw1 : swc38)=1.0, (swc41 : sw20)=1.0, (swcc37 : swc41)=1.0, (swc38 : swcc37)=1.0}

        //FIND NEXT SW
        String strCustomNextSw = null;

        for ( String str: cleanSolutionMap.keySet()) {
            String[] result = str.split(" : ");

            String strCustom1 = result[0].replace("(","");
            String strCustom2 = result[1].replace(")","");

            if(strCustom1.equals(strCustomCurrentSw)){
                strCustomNextSw = strCustom2;
                break;
            }

        }

        //FIND PORTS
        Device devCurrent = deviceService.getDevice(DeviceId.deviceId(trackingGrafo.getNodes().get(strCustomCurrentSw).getNodeUri()));
        Device devNext = deviceService.getDevice(DeviceId.deviceId(trackingGrafo.getNodes().get(strCustomNextSw).getNodeUri()));

        Set<Link> setLinks = linkService.getDeviceEgressLinks(devCurrent.id());

        PortNumber portSrc = null;
        PortNumber portDst = null;

        for (Link ll : setLinks) {
            if (ll.dst().deviceId().equals(devNext.id())) {
                portSrc = ll.src().port();
                portDst = ll.dst().port();
                break;
            }
        }



        //INSTALL RULES
        FlowRuleInterface flowService = get(FlowRuleInterface.class);
        FlowRule fr = flowService.addFlowMAC(devCurrent.id().uri().toString(), defAppIDDB, defTimeoutDB, defPriorityDB, (int) inPort.toLong(), (int) portSrc.toLong(), srcMac,dstMac);
        //build a list of every FR of this path
        newFlowRuleEntry.add(fr);

        //check if this this the last sw of the path
        if(strCustomNextSw.equals(strCustomLastSw)){
            //next -> sw20
            //INSTALL LAST RULE (sw->targetHost)
            MacAddress addr = MacAddress.valueOf(dstMac);
            Set<Host> hostSet =  hostService.getHostsByMac(addr);

            if(hostSet.size() != 1){
                throw new ExceptionInInitializerError();
            }
            PortNumber portHost = hostSet.iterator().next().location().port();
            fr = flowService.addFlowMAC(devNext.id().uri().toString(), defAppIDDB, defTimeoutDB, defPriorityDB, (int) portDst.toLong(), (int) portHost.toLong(), srcMac, dstMac);

            //build a list of every FR of this path
            newFlowRuleEntry.add(fr);
            //keeps track of every flow rule that is associated with this path
            trackingGrafo.getFlowrulesInstalled().add(newFlowRuleEntry);

            log.info("LAst:"+fr);

        }else{
            //Intall rules in the next SW
            installRulesPath(cleanSolutionMap, portDst, strCustomNextSw,  strCustomLastSw,  srcMac,  dstMac, newFlowRuleEntry);
        }



    }

/*
    protected void getDBService_conf(){

        datarates = new HashMap<String,Double>();

        String readTableString = "select * from service_conf";
        try(Connection con = dataSource.getConnection();
            PreparedStatement prepStmt = con.prepareStatement(readTableString)){

            ResultSet rs = prepStmt.executeQuery();

            while(rs.next()){
                //(video:1.5;...)
                datarates.put(rs.getString(1),Double.parseDouble(rs.getString(2)) );
            }

        } catch (SQLException throwables) {
            log.info("ERROR reading table Service configurations from DataBase. "+getClass());
            throwables.printStackTrace();
        } catch (Exception e) {
            log.info("ERROR, Service configuration table is empty, ");
        }

    }

    protected void getDBGeneral_conf(){

        String readTableString = "select * from general_conf";
        try(Connection con = dataSource.getConnection();
            PreparedStatement prepStmt = con.prepareStatement(readTableString)){

            ResultSet rs = prepStmt.executeQuery();

            if(rs.next() == false){
                throw new Exception();
            }

            //0 : idk what it is
            //1 : pkey, we will not use
            //2 : wired_bw
            this.bw_wired_mbsDB = Integer.parseInt(rs.getString(2));
            //3 : wireless_bw
            this.bw_wireless_mbsDB = Integer.parseInt(rs.getString(3));
            //4 : flowrule_priority
            this.defPriorityDB = Integer.parseInt(rs.getString(4));
            //5 : flowrule_timeout
            this.defTimeoutDB = Short.parseShort(rs.getString(5));
            //6 : flowrule_appid
            this.defAppIDDB = Short.parseShort(rs.getString(6));
            //7 : default_fw_algo
            this.default_fw_algorithm = rs.getString(7);

        } catch (SQLException throwables) {
            log.info("ERROR reading table configurations from DataBase. "+getClass());
            throwables.printStackTrace();
        } catch (Exception e) {
            log.info("ERROR, configuration table is empty, using default values for port speed");
        }

    }

*/
    /*Related with BW of links in the tracking graph*/
    protected void takesBW(Map<String,Double> cleanSolutionMap, String serviceStr ){

        //eg. {(sw1 : swc38)=1.0, (swc41 : sw20)=1.0, (swcc37 : swc41)=1.0, (swc38 : swcc37)=1.0}
        //e.g {(sw1 : sw4)=1.0}

        for ( String edgeToString : cleanSolutionMap.keySet() ) {
            String[] result = edgeToString.split( " : ");
            String nodeSrcSWName = result[0].replace("(","");
            String nodeDstSWName = result[1].replace(")","");
            LinkT link = trackingGrafo.getNodes().get(nodeSrcSWName).getLinks().get(nodeDstSWName);

            double datarate = datarates.get(serviceStr);

            log.info("nodeSrcSWName:"+nodeSrcSWName+" nodeDstSWName:"+nodeDstSWName);

            double avb = link.getBwAvbleMbts();
            //Retira BW do link (pkesta a ser usada no servico)
            link.setBwAvbleMbts(link.getBwAvbleMbts() - datarate);
            log.info("Reserve BW: "+datarate+ " in Link :"+link.getEdgeToString()+"  From: "+avb+" , to Current BW:"+link.getBwAvbleMbts());
        }

    }

    protected void replenishesBW(ArrayList<FlowRule> listFR) {
        //antes de correr o algoritmo a proxima vez tenho de repor a bw nos links que deixaram de ser usados


        String srcCritStr = listFR.get(0).selector().getCriterion(Criterion.Type.ETH_SRC).toString();
        String dstCritStr = listFR.get(0).selector().getCriterion(Criterion.Type.ETH_DST).toString();

        srcCritStr = srcCritStr.replace("ETH_SRC:","");
        dstCritStr = dstCritStr.replace("ETH_DST:","");


        String serviceStr = null; //getDBTrafficService(srcCritStr, dstCritStr);
        double datarate_service = datarates.get(serviceStr);

        log.info("trackingGrafo.getFlowrulesInstalled().toString(): "+trackingGrafo.getFlowrulesInstalled().toString());

        //arranjar forma de percorrer os sw#-sw# no tracking graph

        for (int i=0; i < listFR.size()-1; i++){

            //e.g {(sw1 : sw3)=1.0, (sw3 : sw4)=1.0}

            String swCustomSrc = convert_SwUri_StrCustom(listFR.get(i).deviceId().toString());
            String swCustomDst = convert_SwUri_StrCustom(listFR.get(i+1).deviceId().toString());

            log.info("swCustomSrc : "+swCustomSrc);
            log.info("swCustomDst : "+swCustomDst);



            double bwAtual = trackingGrafo.getNodes().get(swCustomSrc).getLinks().get(swCustomDst).getBwAvbleMbts();
            LinkT link = trackingGrafo.getNodes().get(swCustomSrc).getLinks().get(swCustomDst);

            trackingGrafo.getNodes().get(swCustomSrc).getLinks().get(swCustomDst).setBwAvbleMbts(bwAtual+datarate_service);
            log.info("Replenishes BW: "+datarate_service+ "in Link :"+link.getEdgeToString()+" From "+bwAtual+", to Current BW:"+link.getBwAvbleMbts());

        }

    }

    protected synchronized void checkForOldRules( ) {

        boolean boolOldRules;

        //go throw the flow rules that are taking up BW in links
        ListIterator<ArrayList<FlowRule>> iter = trackingGrafo.getFlowrulesInstalled().listIterator();
        while(iter.hasNext()) {
            boolOldRules = true;

            ArrayList<FlowRule> listFR = iter.next();

            DeviceId sampleDeviceID = listFR.get(0).deviceId();
            //ETH_DST:AA:BB:CC:DD:00:06 e.g.
            Criterion srcCrit = listFR.get(0).selector().getCriterion(Criterion.Type.ETH_SRC);
            Criterion dstCrit = listFR.get(0).selector().getCriterion(Criterion.Type.ETH_DST);



            //seach in the device fot the flow rule that matches this information
            for (FlowEntry setFE : flowRuleService.getFlowEntries(sampleDeviceID)) {

                Criterion srcDevCrit = setFE.selector().getCriterion(Criterion.Type.ETH_SRC);
                Criterion dstDevCrit = setFE.selector().getCriterion(Criterion.Type.ETH_DST);

                if ((srcDevCrit != null && dstDevCrit != null) && (srcDevCrit.equals(srcCrit) && dstDevCrit.equals(dstCrit))) {
                    //We found the FR installed
                    //SO THIS flow rule is not old / Is still in use
                    boolOldRules = false;
                    break;
                }

            }



            if(boolOldRules == true){
                //We didn't found any flowrules still in the sw, so the rules were old and ONOS removed them
                //We must update the BW of the affected links
                replenishesBW(listFR);
                iter.remove();
            }

        }
    }

}
