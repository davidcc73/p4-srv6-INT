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
package org.uc.dei.mei.framework.flowrule;


import org.onlab.packet.*;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.CoreService;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;

import org.onosproject.net.flow.*;

import org.onosproject.net.flow.criteria.Criterion;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uc.dei.mei.framework.flowrule.FlowRuleInterface;


@Component(immediate = true,
        service = {FlowRuleInterface.class}
)

public class ONOSAppFlowRuleComponent implements FlowRuleInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;





    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Activate
    protected void activate(){
        log.info("Started FlowRule Component - rik");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped FlowRule Component - rik");
    }


    @Override
    public void removeFlowRulesApp(short appID) {

        System.out.println("--Remove Rules-");

        DefaultApplicationId appId = new DefaultApplicationId(appID, "fillername_henrique");
        flowRuleService.removeFlowRulesById(appId);

    }

    @Override
    public String addFlowMACARP(String deviceId, short appID, short timeout, int priority, int in_port, int out_port, String src_mac, String dst_mac){

        Iterable<Device> devices = deviceService.getAvailableDevices();
        DefaultFlowRule.Builder frbuilder = new DefaultFlowRule.Builder();

        //device ID
        Device dev = deviceService.getDevice(DeviceId.deviceId(deviceId));
        frbuilder.forDevice(dev.id());


        //App Id
        DefaultApplicationId appId = new DefaultApplicationId(appID, "fillername_henrique");
        frbuilder.fromApp(appId);

        //TimeOut
        if(timeout == -1){
            frbuilder.makePermanent();
        }else{
            frbuilder.withIdleTimeout(timeout);
        }

        //Priority
        frbuilder.withPriority(priority);


        //Mach / Selector
        TrafficSelector.Builder trafficSelectorBuild = DefaultTrafficSelector.builder();
        trafficSelectorBuild.matchInPort(PortNumber.portNumber(in_port)); //matchEthDst(MacAddress addr);

        if(src_mac.equals("") == false){
            MacAddress addrsrc = MacAddress.valueOf(src_mac);
            trafficSelectorBuild.matchEthSrc(addrsrc);

        }if( dst_mac.equals("") == false) {
            MacAddress addrdst = MacAddress.valueOf(dst_mac);
            trafficSelectorBuild.matchEthDst(addrdst);
        }




        //??????
        trafficSelectorBuild.matchEthType(EthType.EtherType.ARP.ethType().toShort() );


        TrafficSelector selector = trafficSelectorBuild.build();
        frbuilder.withSelector(selector);


        //frbuilder.withSelector(DefaultTrafficSelector.emptySelector());


        //Action
        TrafficTreatment.Builder trafficTreatBuild = DefaultTrafficTreatment.builder();
        if(out_port == -1){
            trafficTreatBuild.drop();
        }else{
            trafficTreatBuild.setOutput(PortNumber.portNumber(out_port));
        }

        //trafficTreatBuild.setOutput(PortNumber.CONTROLLER);
        //treatment=DefaultTrafficTreatment{immediate=[OUTPUT:1],

        TrafficTreatment treat = trafficTreatBuild.build();
        frbuilder.withTreatment(treat);


        //	trafficTreatBuild.meter(MeterId meterId)

        //ADD RULE
        FlowRule fr = frbuilder.build();
        log.info("Added in "+deviceId+" the rule:"+fr.toString() );
        flowRuleService.applyFlowRules(fr);


        return "addFlowRule";
    }



    @Override
    public FlowRule addFlowMAC(String deviceId, short appID, short timeout, int priority, int in_port, int out_port, String src_mac, String dst_mac){

        Iterable<Device> devices = deviceService.getAvailableDevices();
        DefaultFlowRule.Builder frbuilder = new DefaultFlowRule.Builder();

        //device ID
        Device dev = deviceService.getDevice(DeviceId.deviceId(deviceId));
        frbuilder.forDevice(dev.id());


        //App Id
        DefaultApplicationId appId = new DefaultApplicationId(appID, "fillername_henrique");
        frbuilder.fromApp(appId);

        //TimeOut
        if(timeout == -1){
            frbuilder.makePermanent();
        }else{
            frbuilder.withIdleTimeout(timeout);
        }

        //Priority
        frbuilder.withPriority(priority);


        //Mach / Selector
        TrafficSelector.Builder trafficSelectorBuild = DefaultTrafficSelector.builder();
        trafficSelectorBuild.matchInPort(PortNumber.portNumber(in_port)); //matchEthDst(MacAddress addr);

        if(src_mac.equals("") == false){
            MacAddress addrsrc = MacAddress.valueOf(src_mac);
            trafficSelectorBuild.matchEthSrc(addrsrc);

        }if( dst_mac.equals("") == false) {
            MacAddress addrdst = MacAddress.valueOf(dst_mac);
            trafficSelectorBuild.matchEthDst(addrdst);
        }




        //??????
        trafficSelectorBuild.matchEthType(EthType.EtherType.IPV4.ethType().toShort());


        TrafficSelector selector = trafficSelectorBuild.build();
        frbuilder.withSelector(selector);


        //frbuilder.withSelector(DefaultTrafficSelector.emptySelector());


        //Action
        TrafficTreatment.Builder trafficTreatBuild = DefaultTrafficTreatment.builder();
        if(out_port == -1){
            trafficTreatBuild.drop();
        }else{
            trafficTreatBuild.setOutput(PortNumber.portNumber(out_port));
        }

        //trafficTreatBuild.setOutput(PortNumber.CONTROLLER);
        //treatment=DefaultTrafficTreatment{immediate=[OUTPUT:1],

        TrafficTreatment treat = trafficTreatBuild.build();
        frbuilder.withTreatment(treat);


        //	trafficTreatBuild.meter(MeterId meterId)

        //ADD RULE
        FlowRule fr = frbuilder.build();
        log.info("Added in "+deviceId+" the rule:"+fr.toString() );
        flowRuleService.applyFlowRules(fr);


        return fr;
    }


    @Override
    public String addFlowIP(String deviceId, short appID, short timeout, int priority, int in_port, int out_port, String src_str, String dst_str){

        Iterable<Device> devices = deviceService.getAvailableDevices();
        DefaultFlowRule.Builder frbuilder = new DefaultFlowRule.Builder();

        //device ID
        Device dev = deviceService.getDevice(DeviceId.deviceId(deviceId));
        frbuilder.forDevice(dev.id());


        //App Id
        DefaultApplicationId appId = new DefaultApplicationId(appID, "fillername_henrique");
        frbuilder.fromApp(appId);

        //TimeOut
        if(timeout == -1){
            frbuilder.makePermanent();
        }else{
            frbuilder.withIdleTimeout(timeout);
        }

        //Priority
        frbuilder.withPriority(priority);


        //Mach / Selector
        TrafficSelector.Builder trafficSelectorBuild = DefaultTrafficSelector.builder();
        trafficSelectorBuild.matchInPort(PortNumber.portNumber(in_port)); //matchEthDst(MacAddress addr);

        if(src_str.equals("") == false){
            Ip4Address ipsrc = Ip4Address.valueOf(src_str);
            trafficSelectorBuild.matchArpSpa(ipsrc);

        }if( dst_str.equals("") == false) {
            Ip4Address ipdst = Ip4Address.valueOf(dst_str);
            trafficSelectorBuild.matchArpTpa(ipdst);
        }



        trafficSelectorBuild.matchEthType(EthType.EtherType.IPV4.ethType().toShort());



        TrafficSelector selector = trafficSelectorBuild.build();
        frbuilder.withSelector(selector);


        //frbuilder.withSelector(DefaultTrafficSelector.emptySelector());

        //Action
        TrafficTreatment.Builder trafficTreatBuild = DefaultTrafficTreatment.builder();
        if(out_port == -1){
            trafficTreatBuild.drop();
        }else{
            trafficTreatBuild.setOutput(PortNumber.portNumber(out_port));
        }



        //treatment=DefaultTrafficTreatment{immediate=[OUTPUT:1],

        TrafficTreatment treat = trafficTreatBuild.build();
        frbuilder.withTreatment(treat);


        //	trafficTreatBuild.meter(MeterId meterId)

        //ADD RULE
        FlowRule fr = frbuilder.build();
        log.info("Added in "+deviceId+" the rule:"+fr.toString() );
        flowRuleService.applyFlowRules(fr);


        return "addFlowRule";
    }



    @Override
    public Iterable<FlowEntry>  getFlowRulesDevice(String deviceId) {

        Iterable<FlowEntry> flowentries =  flowRuleService.getFlowEntries(DeviceId.deviceId(deviceId));
        System.out.println(flowentries);

        return flowentries;
    }

    @Override
    public Integer getFlowRulesDevicePacketCount(short appID, String deviceId, String src_mac, String dst_mac ) {

        Iterable<FlowEntry> flowEntries = flowRuleService.getFlowEntries(DeviceId.deviceId(deviceId));

        System.out.println("macSRC:"+ src_mac);
        System.out.println("macDST:"+ dst_mac);

        for (FlowEntry flowentry : flowEntries ) {

            //as minhas flow rules tem ID == 22 (mesh) ou 23 (wired)
            if(flowentry.appId() == appID){

                if(flowentry.selector().getCriterion(Criterion.Type.ETH_SRC) != null &&
                        flowentry.selector().getCriterion(Criterion.Type.ETH_SRC).toString().equals(Criterion.Type.ETH_SRC.toString()+":"+src_mac) &&
                        flowentry.selector().getCriterion(Criterion.Type.ETH_DST) != null &&
                        flowentry.selector().getCriterion(Criterion.Type.ETH_DST).toString().equals(Criterion.Type.ETH_DST.toString()+":"+dst_mac) ){

                    System.out.println("packets: "+ flowentry.packets());
                    return (int) flowentry.packets();

                }
            }

        }

        return 0;
    }


}


