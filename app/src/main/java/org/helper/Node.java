package org.helper;

import java.util.ArrayList;
import java.util.HashMap;

public class Node {

    private String nodeName = null;

    private String nodeUri = null;

    //PI
    //Cubi
    private String typeMachine = null;

    //sw
    //swc
    //swcc
    private String typeNode = null;

    private int clusterIndex;


    private HashMap<String,LinkT> linkTS;




    public Node(String nodeName, String nodeUri, String typeMachine, String typeNode, int clusterIndex ){
        this.nodeName = nodeName;
        this.nodeUri = nodeUri;
        this.typeMachine = typeMachine;
        this.typeNode = typeNode;
        this.clusterIndex = clusterIndex;
        this.linkTS = new HashMap<>();

    }




    //TODO ir buscar à base de dados
    public double getEnergyUpWireless(double datarate){
        if(typeMachine.equals("PI")){
            return -0.25*Math.exp(-06)*Math.pow(datarate,2)  + 1.99*Math.exp(-03)*datarate -   0.072;
        }else{
            return -0.307*Math.exp(-03)*Math.pow(datarate,2)   + 22.8*Math.exp(-03)*datarate + 0.011;
        }
    }

    public double getEnergyUpWired(double datarate){
        if(typeMachine.equals("PI")){
            return 26.2*Math.exp(-06)*Math.pow(datarate,2)    + 0.357*Math.exp(-03)*datarate + 0.007;//swc8
            //return Math.pow(26.2, -06)*Math.pow(datarate,2)    + 0.357*Math.exp(-03)*datarate + 0.007;//swc8
        }else{
            return  -17.6*Math.exp(-06)*Math.pow(datarate,2)   + 6.13*Math.exp(-03)*datarate - 0.056;
        }
    }

    public double getEnergyDownWireless(double datarate){
        if(typeMachine.equals("PI")){
            //return 1.85*Math.exp(-03)*Math.pow(datarate,2) - 13.5*Math.exp(-03)*datarate +   0.072;
            //ajuste do prof return 1.85*Math.exp(-03)*Math.pow(datarate,2) + 13.5*Math.exp(-03)*datarate +   0.072;
            return 1.85*Math.exp(-03)*Math.pow(datarate,2) + 13.5*Math.exp(-03)*datarate +   0.072;
        }else{
            return 0.137*Math.exp(-03)*Math.pow(datarate,2)   + 6.33*Math.exp(-03)*datarate - 0.011;
        }
    }

    public double getEnergyDownWired(double datarate){//
        if(typeMachine.equals("PI")){
            return -4.33*Math.exp(-06)*Math.pow(datarate,2)   + Math.pow(0.485,-03)*datarate - 0.007;
        }else{
            return (-20.9)*Math.exp(-06)*Math.pow(datarate,2)   + 2.5*Math.exp(-03)*datarate + 0.056; //sw4
        }
    }








    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeUri() {
        return nodeUri;
    }

    public void setNodeUri(String nodeUri) {
        this.nodeUri = nodeUri;
    }

    public String getTypeMachine() {
        return typeMachine;
    }

    public void setTypeMachine(String typeMachine) {
        this.typeMachine = typeMachine;
    }

    public HashMap<String,LinkT> getLinks() {
        return linkTS;
    }

    public String getTypeNode() {
        return typeNode;
    }

    public int getClusterIndex() {
        return clusterIndex;
    }

    public void setClusterIndex(int clusterIndex) {
        this.clusterIndex = clusterIndex;
    }
}
