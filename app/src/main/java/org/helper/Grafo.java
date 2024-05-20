package org.helper;

import org.onosproject.net.flow.FlowRule;

import java.util.ArrayList;
import java.util.HashMap;

public class Grafo {

    private HashMap<String,Node> nodes;
    private ArrayList<ArrayList<FlowRule>> flowrulesInstalled;

    public Grafo(){

        nodes = new HashMap<String,Node>();
        flowrulesInstalled = new ArrayList<ArrayList<FlowRule>>();

    }

    public HashMap<String, Node> getNodes() {
        return nodes;
    }

    public ArrayList<ArrayList<FlowRule>> getFlowrulesInstalled() {
        return flowrulesInstalled;
    }

    public void setFlowrulesInstalled(ArrayList<ArrayList<FlowRule>> flowrulesInstalled) {
        this.flowrulesInstalled = flowrulesInstalled;
    }
}
