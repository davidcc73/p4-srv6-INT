//https://groups.google.com/a/onosproject.org/g/onos-dev/c/38IL1y3-TmY
package org.helper;

import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onosproject.net.Link;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.TopologyEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uc.dei.mei.framework.algorithm.AlgoHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public final class EnergyLinkWeigherAlgo implements LinkWeigher {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private String serviceStr;
    //private DataSource dataSource;
    private double datarate;
    private AlgoHelper algohelper;

    //public EnergyLinkWeigherAlgo(String serviceStr, DataSource dataSource, AlgoHelper algohelper) {
    public EnergyLinkWeigherAlgo(String serviceStr, AlgoHelper algohelper) {
        super();
        this.serviceStr = serviceStr;
        //this.dataSource = dataSource;
        this.datarate = algohelper.datarates.get(serviceStr);
        this.algohelper = algohelper;
    }

    @Override
    public Weight getInitialWeight() {
        return ScalarWeight.toWeight(0.0);
    }

    @Override
    public Weight getNonViableWeight() {
        return ScalarWeight.NON_VIABLE_WEIGHT;
    }

    @Override
    public Weight weight(TopologyEdge edge) {

        Link link = edge.link();

        double cost = calcEnergyCost(link, false);

        return ScalarWeight.toWeight(cost);
    }




    public double calcEnergyCost(Link link, Boolean logBool){
        double x_energia,src_ener,dst_ener, min_energia;


        String srcNameCustom = algohelper.convert_SwUri_StrCustom(link.src().deviceId().toString());
        String dstNameCustom = algohelper.convert_SwUri_StrCustom(link.dst().deviceId().toString());

        LinkT linkT = algohelper.trackingGrafo.getNodes().get(srcNameCustom).getLinks().get(dstNameCustom);

        log.info("link: "+link);
        log.info("linkT: "+linkT);
        log.info("srcNameCustom: "+srcNameCustom);
        log.info("dstNameCustom: "+dstNameCustom);

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






        if(linkT.getLinkType().equals("wireless")) {
            min_energia =  algohelper.getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),this.serviceStr,"energy_wireless_idle" )+
                    algohelper.getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(),this.serviceStr,"energy_wireless_idle" );



                    src_ener = algohelper.getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),this.serviceStr,"energy_wireless_u" );
            dst_ener = algohelper.getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(),this.serviceStr,"energy_wireless_d" );
        }else{
            min_energia =  algohelper.getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),this.serviceStr,"energy_wired_idle" )+
                    + algohelper.getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(),this.serviceStr,"energy_wired_idle" );

            src_ener = algohelper.getEnergyConsumptionFtable(linkT.getNodeSrc().getTypeMachine(),this.serviceStr,"energy_wired_u" );
            dst_ener = algohelper.getEnergyConsumptionFtable(linkT.getNodeDst().getTypeMachine(),this.serviceStr,"energy_wired_d" );
        }

        if(logBool){
            log.info("src_energ:"+src_ener);
            log.info("dst_energ:"+dst_ener);
            log.info("min_energ:"+min_energia);
        }


        //x_energia = src_ener + dst_ener + min_energia + 0.6191;
        //energy_wireless_idle
        x_energia = src_ener + dst_ener + min_energia + CPU_m_S + CPU_m_D;

        return  x_energia;
    }


    /*
    private double getDBDatarate() {
        String readTableString = "select datarate_mbs from service_conf where name = '"+this.serviceStr+"'";
        try(Connection con = dataSource.getConnection();
            PreparedStatement prepStmt = con.prepareStatement(readTableString)){

            ResultSet rs = prepStmt.executeQuery();


            if(rs.next() == false){
                throw new Exception();
            }

            //0 : idk what it is
            //1 : datarate,
            return Double.parseDouble(rs.getString(1));


        } catch (SQLException throwables) {
            log.info("ERROR reading table configurations from DataBase. "+getClass());
            throwables.printStackTrace();
            return 0;
        } catch (Exception e) {
            log.info("ERROR, configuration table is empty");
            return 0;
        }

    }
*/

    public double getDatarate() {
        return datarate;
    }
}