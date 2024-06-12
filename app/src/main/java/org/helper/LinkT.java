package org.helper;

public class LinkT {

    //capacity
    private double bwTotalMbts;
    private double bwAvbleMbts;
    private double lossProb;

    private Node nodeSrc;
    private Node nodeDst;

    //wireless
    //wired
    private String linkType;


    private String edgeToString;


    public LinkT(double bwTotalMbts, double bwAvbleMbts, double lossProb, Node nodeSrc, Node nodeDst, String linkType ){

        this.bwTotalMbts = bwTotalMbts;
        this.bwAvbleMbts = bwAvbleMbts;
        this.lossProb = lossProb;
        this.nodeSrc = nodeSrc;
        this.nodeDst = nodeDst;
        this.linkType = linkType;
        this.edgeToString = "("+nodeSrc.getNodeName()+" : "+nodeDst.getNodeName()+")";

    }


    public Node getNodeSrc() {
        return nodeSrc;
    }

    public void setNodeSrc(Node nodeSrc) {
        this.nodeSrc = nodeSrc;
    }

    public double getBwTotalMbts() {
        return bwTotalMbts;
    }

    public void setBwTotalMbts(double bwTotalMbts) {
        this.bwTotalMbts = bwTotalMbts;
    }

    public double getBwAvbleMbts() {
        return bwAvbleMbts;
    }

    public void setBwAvbleMbts(double bwAvbleMbts) {
        this.bwAvbleMbts = bwAvbleMbts;
    }

    public double getLossProb() {
        return lossProb;
    }

    public void setLossProb(double lossProb) {
        this.lossProb = lossProb;
    }

    public Node getNodeDst() {
        return nodeDst;
    }

    public void setNodeDst(Node nodeDst) {
        this.nodeDst = nodeDst;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public String getEdgeToString() {
        return edgeToString;
    }

    public void setEdgeToString(String edgeToString) {
        this.edgeToString = edgeToString;
    }
}
