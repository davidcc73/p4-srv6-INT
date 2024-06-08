/*
 * Copyright 2019-present Open Networking Foundation
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

package org.onosproject.srv6_usid;

import com.google.common.collect.Lists;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.util.ItemNotFoundException;
import org.onosproject.core.ApplicationId;
import org.onosproject.ecmp.ECMPPathService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ElementId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.Path;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.InterfaceIpAddress;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiActionProfileGroupId;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.srv6_usid.MainComponent;
import org.onosproject.srv6_usid.Srv6Component;
import org.onosproject.srv6_usid.common.Srv6DeviceConfig;
import org.onosproject.srv6_usid.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uc.dei.mei.framework.onospath.PathInterface;
import org.onosproject.ecmp.ECMPPathService;


import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.swing.text.html.parser.Element;

import java.util.HashSet;

import static com.google.common.collect.Streams.stream;
import static org.onosproject.srv6_usid.AppConstants.INITIAL_SETUP_DELAY;

/**
 * App component that configures devices to provide IPv6 routing capabilities
 * across the whole fabric.
 */
@Component(
        immediate = true,
        // TODO EXERCISE 3
        // set to true when ready
        enabled = true,
        service = Ipv6RoutingComponent.class
)
public class Ipv6RoutingComponent{

    private static final Logger log = LoggerFactory.getLogger(Ipv6RoutingComponent.class);

    private final LinkListener linkListener = new InternalLinkListener();
    private final DeviceListener deviceListener = new InternalDeviceListener();

    private ApplicationId appId;
    
    //path calculations will use this variables to decide which algorithm and secundadry weigher to use
    int prioritize = 0; // 0 = lantency, 1 = bandwith
    int algorithm = 0; // 0 = KShort, 1 = ECMP

    //--------------------------------------------------------------------------
    // ONOS CORE SERVICE BINDING
    //
    // These variables are set by the Karaf runtime environment before calling
    // the activate() method.
    //--------------------------------------------------------------------------

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private NetworkConfigService networkConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MainComponent mainComponent;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private Srv6Component srv6Component;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PathInterface multiplePathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private ECMPPathService ecmpPathService;

    //--------------------------------------------------------------------------
    // COMPONENT ACTIVATION.
    //
    // When loading/unloading the app the Karaf runtime environment will call
    // activate()/deactivate().
    //--------------------------------------------------------------------------

    @Activate
    protected void activate() {
        appId = mainComponent.getAppId();

        linkService.addListener(linkListener);
        deviceService.addListener(deviceListener);

        // Schedule set up for all devices.
        mainComponent.scheduleTask(this::setUpAllDevices, INITIAL_SETUP_DELAY);  //commented so setnextl2 could change, and is not needed, the topology is received at runtime via onos cli

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        linkService.removeListener(linkListener);
        deviceService.removeListener(deviceListener);

        log.info("Stopped");
    }

    /**
     * Sets up the "My Station" table for the given device using the
     * myStationMac address found in the config.
     * <p>
     * This method will be called at component activation for each device
     * (switch) known by ONOS, and every time a new device-added event is
     * captured by the InternalDeviceListener defined below.
     *
     * @param deviceId the device ID
     */
    private void setUpMyStationTable(DeviceId deviceId) {

        log.info("Adding My Station rules to {}...", deviceId);

        final MacAddress myStationMac = getMyStationMac(deviceId);

        final String tableId = "IngressPipeImpl.l2_firewall";

        final PiCriterion match = PiCriterion.builder()
                .matchExact(
                        PiMatchFieldId.of("hdr.ethernet.dst_addr"),
                        myStationMac.toBytes())
                .build();

        final PiTableAction action = PiAction.builder()
                .withId(PiActionId.of("NoAction"))
                .build();

        final FlowRule myStationRule = Utils.buildFlowRule(
                deviceId, appId, tableId, match, action);

        flowRuleService.applyFlowRules(myStationRule);
    }


    /**
     * Creates a flow rule for the L2 table mapping the given next hop MAC to
     * the given output port.
     * <p>
     * This is called by the routing policy methods below to establish L2-based
     * forwarding inside the fabric, e.g., when deviceId is a leaf switch and
     * nextHopMac is the one of a core switch.
     *
     * @param deviceId   the device
     * @param nexthopMac the next hop (destination) mac
     * @param outPort    the output port
     */
    private FlowRule createL2NextHopRule(DeviceId deviceId, MacAddress nexthopMac,
                                         PortNumber outPort) {   //maps next MAC to Out Port

        final String tableId = "IngressPipeImpl.unicast";
        final PiCriterion match = PiCriterion.builder()
                .matchExact(PiMatchFieldId.of("hdr.ethernet.dst_addr"),
                            nexthopMac.toBytes())
                .build();


        final PiAction action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.set_output_port"))
                .withParameter(new PiActionParam(
                        PiActionParamId.of("port_num"),
                        outPort.toLong()))
                .build();

        return Utils.buildFlowRule(
                deviceId, appId, tableId, match, action);
    }

    /**
     * All L3 paths are recaulculated on each relevant link event!, and there will be a lot of overlap between the 
     * many paths results and their pushes to the switchs, very ineffecient, but it's a simple way to do it,
     * and this project uses a simple/static topology so there is no problem for us.
     * Then sets up the L3 nexthop rules of the devices, along the path to provide forwarding inside the
     * fabric (we assume the hosts have the subnet IPs of their switchs), So the Hosts are also reached.
     */
    public String recalculateAllPaths(){
        int count_fails = 0;
        Path minPath;
        String result = "Success";
        Set<DeviceId> allDevices = new HashSet<>();
        log.info("Calculating all paths...");

        // Get all devices in the network (just switchs, not hosts)
        deviceService.getDevices().forEach(device -> allDevices.add(device.id()));

        // Iterate over all possible pairs devices, THIS LOOP CAN BE OPTIMIZED! 
        for (DeviceId sourceDevice : allDevices) {
        for (DeviceId destinationDevice : allDevices) {
            if(sourceDevice.equals(destinationDevice)) {continue;}
            //Calculate the path from the current device to the destination device
            try{
                if(algorithm == 0){ //KShort
                    minPath = multiplePathService.getK(sourceDevice, destinationDevice, "geo", "video");
                }
                else if(algorithm == 1){ //ECMP         
                    minPath = ecmpPathService.getPath(sourceDevice, destinationDevice, 2);
                }
                else{
                    log.info("Invalid algorithm");
                    return "Invalid algorithm";
                }
            }catch(NoSuchElementException e){
                log.info("No path found between {} and {}", sourceDevice, destinationDevice);
                count_fails++;
                continue;
            }
    
            //Having the path, we can now push the rules to the switches, 
            //we go link by link push the rule to current source
            //(only in one direction, the other will be done by another function call)
            final Ip6Address ip_final_dst = getMySubNetIP(destinationDevice);
            final Ip6Address uN_final_dst = getDeviceSid(destinationDevice);

            minPath.links().forEach(link -> {
                //log.info("Link: {} -> {}", link.src().deviceId(), link.dst().deviceId());

                //Create flow rules and push it to the switch that is source to the current link
                final MacAddress nextHopMac = getMyStationMac(link.dst().deviceId());

                //USING IP6 ADDRESS (for normal routing)
                //Always use mask 64, so it also forwards host's traffic into the right switch, that is connected to it (host's IPs are subnets of switch's IP)         
                insertRoutingRule(link.src().deviceId(), ip_final_dst, 64, nextHopMac);

                //USING uN Value    (for Srv6 routing) (it's uSID value of the switch)
                //Always use mask 48
                insertRoutingRule(link.src().deviceId(), uN_final_dst, 48, nextHopMac);
            }
            );
        }
        }
        if(count_fails != 0){ result = "Failed to calculate path for " + count_fails + " pairs of devices";}
        return result;
    }

    //--------------------------------------------------------------------------
    // EVENT LISTENERS
    //
    // Events are processed only if isRelevant() returns true.
    //--------------------------------------------------------------------------

    /**
     * Listener of link events, which triggers configuration of routing rules to
     * forward packets across the fabric, i.e. from leaves to cores and vice
     * versa.
     * <p>
     * Reacting to link events instead of device ones, allows us to make sure
     * all device are always configured with a topology view that includes all
     * links, e.g. modifying an ECMP group as soon as a new link is added. The
     * downside is that we might be configuring the same device twice for the
     * same set of links/paths. However, the ONOS core treats these cases as a
     * no-op when the device is already configured with the desired forwarding
     * state (i.e. flows and groups)
     */
    class InternalLinkListener implements LinkListener {

        @Override
        public boolean isRelevant(LinkEvent event) {
            switch (event.type()) {
                case LINK_ADDED:
                    break;
                case LINK_UPDATED:              //maybe useful for when links break
                case LINK_REMOVED:
                default:
                    return false;
            }
            DeviceId srcDev = event.subject().src().deviceId();
            DeviceId dstDev = event.subject().dst().deviceId();
            return mastershipService.isLocalMaster(srcDev) ||
                    mastershipService.isLocalMaster(dstDev);
        }

        @Override
        public void event(LinkEvent event) {    //see isRelevant() explanation
            DeviceId srcDev = event.subject().src().deviceId();
            DeviceId dstDev = event.subject().dst().deviceId();

            // Being a new link discovered we need to map the MAC of the new devices to their own outports
            if (mastershipService.isLocalMaster(srcDev)) {
                mainComponent.getExecutorService().execute(() -> {
                    log.info("{} event! Configuring {}... linkSrc={}, linkDst={}",
                             event.type(), srcDev, srcDev, dstDev);
                    setUpL2NextHopRules(srcDev);
                });
            }
            if (mastershipService.isLocalMaster(dstDev)) {
                mainComponent.getExecutorService().execute(() -> {
                    log.info("{} event! Configuring {}... linkSrc={}, linkDst={}",
                             event.type(), dstDev, srcDev, dstDev);
                    setUpL2NextHopRules(dstDev);
                });
            }
        }
    }

    /**
     * Listener of device events which triggers configuration of the My Station
     * table.
     */
    class InternalDeviceListener implements DeviceListener {

        @Override
        public boolean isRelevant(DeviceEvent event) {
            switch (event.type()) {
                case DEVICE_AVAILABILITY_CHANGED:
                case DEVICE_ADDED:
                    break;
                default:
                    return false;
            }
            // Process device event if this controller instance is the master
            // for the device and the device is available.
            DeviceId deviceId = event.subject().id();
            return mastershipService.isLocalMaster(deviceId) &&
                    deviceService.isAvailable(event.subject().id());
        }

        @Override
        public void event(DeviceEvent event) {
            mainComponent.getExecutorService().execute(() -> {
                DeviceId deviceId = event.subject().id();
                log.info("{} event! device id={}", event.type(), deviceId);
                setUpMyStationTable(deviceId);
            });
        }
    }

    //--------------------------------------------------------------------------
    // ROUTING POLICY METHODS
    //
    // Called by event listeners, these methods implement the actual routing
    // policy, responsible of computing paths and creating ECMP groups.
    //--------------------------------------------------------------------------

    /**
     * Set up L2 nexthop rules of a device to providing forwarding inside the
     * fabric, i.e. between leaf and core switches.
     *
     * @param deviceId the device ID
     */
    private void setUpL2NextHopRules(DeviceId deviceId) {

        Set<Link> egressLinks = linkService.getDeviceEgressLinks(deviceId);

        for (Link link : egressLinks) {
            // For each other switch directly connected to this.
            final DeviceId nextHopDevice = link.dst().deviceId();
            // Get port of this device connecting to next hop.
            final PortNumber outPort = link.src().port();
            // Get next hop MAC address.
            final MacAddress nextHopMac = getMyStationMac(nextHopDevice);

            final FlowRule nextHopRule = createL2NextHopRule(
                    deviceId, nextHopMac, outPort);

            flowRuleService.applyFlowRules(nextHopRule);
        }
    }

    /*
     * Creates and Pushes L3 routing rules to the device, so it can forward packets 
     * to the next hop MAC address based on its IP and mask.
     * Existing rules are overwritten, when the match fields coincide.
     */
    public void insertRoutingRule(DeviceId routerId, Ip6Address ipv6Addr,
                                    int mask, MacAddress nextHopMac) {
        log.info("Adding a route on {}...", routerId);

        final String tableId = "IngressPipeImpl.routing_v6_kShort";

        final PiCriterion match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        ipv6Addr.toOctets(),
                        mask)
                .build();

        final PiAction action = PiAction.builder()
                    .withId(PiActionId.of("IngressPipeImpl.set_next_hop"))
                    .withParameter(new PiActionParam(
                            // Action param name.
                            PiActionParamId.of("next_hop"),
                            // Action param value.
                            nextHopMac.toBytes()))
                    .build();
        flowRuleService.applyFlowRules(Utils
                 .buildFlowRule(routerId, appId, tableId, match, action));
       
    }    

    //--------------------------------------------------------------------------
    // UTILITY METHODS
    //--------------------------------------------------------------------------

    /**
     * Returns the MAC address configured in the "myStationMac" property of the
     * given device config.
     *
     * @param deviceId the device ID
     * @return MyStation MAC address
     */
    private MacAddress getMyStationMac(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::myStationMac)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Missing myStationMac config for " + deviceId));
    }

    /**
     * Returns the IP address configured in the "subNetIP" property of the
     * given device config.
     *
     * @param deviceId the device ID
     * @return MyStation MAC address
     */
    private Ip6Address getMySubNetIP(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::mySubNetIP)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Missing mySubNetIP config for " + deviceId));
    }

    /**
     * Returns the Srv6 config object for the given device.
     *
     * @param deviceId the device ID
     * @return Srv6  device config
     */
    private Optional<Srv6DeviceConfig> getDeviceConfig(DeviceId deviceId) {
        Srv6DeviceConfig config = networkConfigService.getConfig(
                deviceId, Srv6DeviceConfig.class);
        return Optional.ofNullable(config);
    }

    /**
     * Returns the set of interface IPv6 subnets (prefixes) configured for the
     * given device.
     *
     * @param deviceId the device ID
     * @return set of IPv6 prefixes
     */
    private Set<Ip6Prefix> getInterfaceIpv6Prefixes(DeviceId deviceId) {
        return interfaceService.getInterfaces().stream()
                .filter(iface -> iface.connectPoint().deviceId().equals(deviceId))
                .map(Interface::ipAddressesList)
                .flatMap(Collection::stream)
                .map(InterfaceIpAddress::subnetAddress)
                .filter(IpPrefix::isIp6)
                .map(IpPrefix::getIp6Prefix)
                .collect(Collectors.toSet());
    }

    /**
     * Returns a 32 bit bit group ID from the given MAC address.
     *
     * @param mac the MAC address
     * @return an integer
     */
    private int macToGroupId(MacAddress mac) {
        return mac.hashCode() & 0x7fffffff;
    }

    /**
     * Gets Srv6 SID for the given device.
     *
     * @param deviceId the device ID
     * @return SID for the device
     */
    private Ip6Address getDeviceSid(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::myUSid)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Missing myUSid config for " + deviceId));
    }

    /**
     * Sets up IPv6 routing on all devices known by ONOS and for which this ONOS
     * node instance is currently master.
     */
    private synchronized void setUpAllDevices() {       //acivated at this component boot
        // Set up host routes
        stream(deviceService.getAvailableDevices())
                .map(Device::id)
                .filter(mastershipService::isLocalMaster)
                .forEach(deviceId -> {
                    log.info("*** IPV6 ROUTING - Starting initial set up for {}...", deviceId);
                    setUpMyStationTable(deviceId);
                    setUpL2NextHopRules(deviceId);
                });        
    }

    /*
     * To change the algorithm used to calculate the paths, use this function
     */
    public void setAlgorithm(String alg){
        if(alg.equals("KShort")){
            this.algorithm = 0;
        }else if(alg.equals("ECMP")){
            this.algorithm = 1;
        }
    }

    /*
     * To change the Secundary weight used to calculate the paths, use this function
     */
    public void setPrioritize(String criteria){
        if(criteria.equals("latency")){
            this.prioritize = 0;
        }else if(criteria.equals("bandwith")){
            this.prioritize = 1;
        }
    }
}
