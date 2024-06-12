package org.uc.dei.mei.framework.algorithm;

import org.helper.Grafo;
import org.helper.LinkT;
import org.helper.Node;
import org.onosproject.core.HybridLogicalClockService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.host.HostService;
import org.slf4j.Logger;

import java.math.BigInteger;
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

    /*
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
*/

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
/* 
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

*/
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
}
