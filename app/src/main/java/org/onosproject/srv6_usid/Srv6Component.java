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
import org.onosproject.core.ApplicationId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.Path;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flow.criteria.PiCriterion.Builder;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.net.topology.Topology;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.srv6_usid.common.Srv6DeviceConfig;
import org.onosproject.srv6_usid.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onlab.packet.MacAddress;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.collect.Streams.stream;
import static org.onosproject.srv6_usid.AppConstants.INITIAL_SETUP_DELAY;

/**
 * Application which handles SRv6 segment routing.
 */
@Component(
        immediate = true,
        enabled = true,
        service = Srv6Component.class
)
public class Srv6Component {

    private static final Logger log = LoggerFactory.getLogger(Srv6Component.class);

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
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private NetworkConfigService networkConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MainComponent mainComponent;

    private final DeviceListener deviceListener = new Srv6Component.InternalDeviceListener();

    private ApplicationId appId;

    //--------------------------------------------------------------------------
    // COMPONENT ACTIVATION.
    //
    // When loading/unloading the app the Karaf runtime environment will call
    // activate()/deactivate().
    //--------------------------------------------------------------------------

    @Activate
    protected void activate() {
        appId = mainComponent.getAppId();

        // Register listeners to be informed about device and host events.
        deviceService.addListener(deviceListener);

        // Schedule set up for all devices.
        mainComponent.scheduleTask(this::setUpAllDevices, INITIAL_SETUP_DELAY);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        deviceService.removeListener(deviceListener);

        log.info("Stopped");
    }

    /**
     * Populate the My micro SID table from the network configuration for the
     * specified device.
     *
     * @param deviceId the device Id
     */
    private void setUpMyUSidTable(DeviceId deviceId) {
        //TODO: add to the listenet
        Ip6Address myUSid = getMyUSid(deviceId);
        Ip6Address myUDX = getMyUDX(deviceId);

        log.info("Adding two myUSid rules on {} (sid {})...", deviceId, myUSid);

        String tableId = "IngressPipeImpl.srv6_localsid_table";

        PiCriterion match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        myUSid.toOctets(), 48)
                .build();

        PiTableAction action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.srv6_usid_un"))
                .build();

        FlowRule myStationRule = Utils.buildFlowRule(
                deviceId, appId, tableId, match, action);

        flowRuleService.applyFlowRules(myStationRule);

        match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        myUSid.toOctets(), 64)
                .build();
        action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.srv6_end"))
                .build();
        myStationRule = Utils.buildFlowRule(
                 deviceId, appId, tableId, match, action);
        flowRuleService.applyFlowRules(myStationRule);

        if (myUDX != null) {
            match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        myUDX.toOctets(), 64)
                .build();
            action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.srv6_end_dx6"))
                .build();
            myStationRule = Utils.buildFlowRule(
                 deviceId, appId, tableId, match, action);
            flowRuleService.applyFlowRules(myStationRule);
        }
    }

    /*
     * Insert a uA instruction 
     */
    public void insertUARule(DeviceId routerId, Ip6Address uAInstruction,
                                    Ip6Address nextHopIpv6, MacAddress nextHopMac) {
        log.info("Adding a uAInstruction on {}...", routerId);

        final String uATableId = "IngressPipeImpl.srv6_localsid_table";
        final String uAActionName = "IngressPipeImpl.srv6_usid_ua";

        final String xconnTableId = "IngressPipeImpl.xconnect_table";
        final String xconnActionName = "IngressPipeImpl.xconnect_act";

        final int mask = 64;

        //table srv6_localsid_table
        PiCriterion match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        uAInstruction.toOctets(),
                        mask)
                .build();

        PiAction action = PiAction.builder()
                    .withId(PiActionId.of(uAActionName))
                    .withParameter(new PiActionParam(
                            // Action param name.
                            PiActionParamId.of("next_hop"),
                            // Action param value.
                            nextHopIpv6.toOctets()))
                    .build();

        flowRuleService.applyFlowRules(Utils
                 .buildFlowRule(routerId, appId, uATableId, match, action));

        //table xconnect_table
        match = PiCriterion.builder()
                    .matchLpm(
                                PiMatchFieldId.of("local_metadata.ua_next_hop"),
                                nextHopIpv6.toOctets(),
                                mask)
                    .build();

        action = PiAction.builder()
                    .withId(PiActionId.of(xconnActionName))
                    .withParameter(new PiActionParam(
                                    PiActionParamId.of("next_hop"),
                                    nextHopMac.toBytes()))
                    .build();

        flowRuleService.applyFlowRules(Utils
                    .buildFlowRule(routerId, appId, xconnTableId, match, action));
    }    

    /**
     * Receives the 3 SRv6 trigger IPs and their respective masks, returns the match Builder.
     * At least one of the 3 masks must be different from 0.
     * 
     * @param srcIp        target src IP address for the SRv6 policy
     * @param dstIp        target dst IP address for the SRv6 policy
     * @param flow_label   target flow label for the SRv6 policy
     * @param srcMask      prefix length for the src target IP
     * @param dstMask      prefix length for the dst target IP
     * @param flowMask     prefix length for the flow target IP
     * @return Builder     the match Builder
     */
    public Builder createMatchCriteria(Ip6Address srcIp, Ip6Address dstIp, int flow_label, 
                                            int srcMask, int dstMask, int flowMask){
        byte[] src_mask_bytes = convertIntToByteArray(srcMask);
        byte[] dst_mask_bytes = convertIntToByteArray(dstMask);

        byte[] label_byte_array = ByteBuffer.allocate(4).putInt(flow_label).array();
        byte[] flowMask_bytes   = ByteBuffer.allocate(4).putInt(flowMask).array();

        Builder builder = PiCriterion.builder();
        if(srcMask  != 0){  builder.matchTernary(PiMatchFieldId.of("hdr.ipv6.src_addr"), srcIp.toOctets(), src_mask_bytes);}
        if(dstMask  != 0){  builder.matchTernary(PiMatchFieldId.of("hdr.ipv6.dst_addr"), dstIp.toOctets(), dst_mask_bytes);}
        if(flowMask != 0){  builder.matchTernary(PiMatchFieldId.of("hdr.ipv6.flow_label"), label_byte_array, flowMask_bytes);}

        return builder;
    }


    /**
     * Insert a micro SID encap insert policy that will inject an IPv6 in IPv6 header for
     * packets destined to destIp.
     *
     * @param deviceId     device ID
     * @param srcIp        target src IP address for the SRv6 policy
     * @param dstIp        target dst IP address for the SRv6 policy
     * @param flow_label   target flow label for the SRv6 policy
     * @param srcMask      prefix length for the src target IP
     * @param dstMask      prefix length for the dst target IP
     * @param flowMask     prefix length for the flow target IP
     * @param segmentList  list of SRv6 SIDs that make up the path, must include last switch before dest
     */
    public void insertSrv6InsertRule(DeviceId deviceId, Ip6Address srcIp, Ip6Address dstIp, int flow_label,
                                    int srcMask, int dstMask, int flowMask,
                                    List<Ip6Address> segmentList) {

        String tableId = "IngressPipeImpl.srv6_encap";
        Ip6Address myUSid= getMyUSid(deviceId);

        //-------------------------------------Match
        PiCriterion match = createMatchCriteria(srcIp, dstIp, flow_label, srcMask, dstMask, flowMask).build();

        List<PiActionParam> actionParams = Lists.newArrayList();

        //This argument will set the source IP to the uN of the current device
        PiActionParamId paramId = PiActionParamId.of("src_addr");
        PiActionParam param = new PiActionParam(paramId, myUSid.toOctets());
        
        actionParams.add(param);

        for (int i = 0; i < segmentList.size(); i++) {
            paramId = PiActionParamId.of("s" + (i + 1));
            param = new PiActionParam(paramId, segmentList.get(i).toOctets());
            actionParams.add(param);
        }

        PiAction action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.usid_encap_" + (segmentList.size())))
                .withParameters(actionParams)
                .build();

        final FlowRule rule = Utils.buildFlowRule(
                deviceId, appId, tableId, match, action);

        //log.info("Inserting SRv6 rule:\n{}", rule);
        flowRuleService.applyFlowRules(rule);
    }

    /**
     * Removes all micro SID encap insert policy from a device that match the specified parameters.
     *
     * @param deviceId     device ID
     * @param srcIp        target src IP address for the SRv6 policy
     * @param dstIp        target dst IP address for the SRv6 policy
     * @param flow_label   target flow label for the SRv6 policy
     * @param srcMask      prefix length for the src target IP
     * @param dstMask      prefix length for the dst target IP
     * @param flowMask     prefix length for the flow label target
     */
    public void removeSrv6InsertRule(DeviceId deviceId, Ip6Address srcIp, Ip6Address dstIp, int flow_label,
                                    int srcMask, int dstMask, int flowMask) {

        String tableId = "IngressPipeImpl.srv6_encap";

        //-------------------------------------Match
        PiCriterion match = createMatchCriteria(srcIp, dstIp, flow_label, srcMask, dstMask, flowMask).build();

        // The action is not needed for removal but the flow rule still needs a dummy action
        PiAction dummyAction = PiAction.builder()
                .withId(PiActionId.of("NoAction"))
                .build();

        final FlowRule rule = Utils.buildFlowRule(
                deviceId, appId, tableId, match, dummyAction);

        //log.info("Removing SRv6 rule:\n{}", rule);
        flowRuleService.removeFlowRules(rule);
    }

    /**
     * Remove all SRv6 transit insert polices for the specified device.
     *
     * @param deviceId device ID
     */
    public void clearSrv6InsertRules(DeviceId deviceId) {
        String tableId = "IngressPipeImpl.srv6_encap";

        FlowRuleOperations.Builder ops = FlowRuleOperations.builder();
        stream(flowRuleService.getFlowEntries(deviceId))
                .filter(fe -> fe.appId() == appId.id())
                .filter(fe -> fe.table().equals(PiTableId.of(tableId)))
                .forEach(ops::remove);
        flowRuleService.apply(ops.build());
    }

    /**
     * Uses SRv6 to create a path detour, that avoids a list of switches.
     * It always use the shortest paths, in the case of multiple paths, 
     * it will choose the one that avoids the most switchs in the list listAvoidID,
     * and in case of another tie, it will choose the one that uses the least relevant switches.
     *
     * @param srcSwitchDeviceId     device ID of the switch connected to the source host
     * @param dstSwitchDeviceId     device ID of the switch connected to the destination host
     * @param srcIp                 target src IP address for the SRv6 policy
     * @param dstIp                 target dst IP address for the SRv6 policy
     * @param flow_label            target flow label for the SRv6 policy
     * @param listPathID            List of the switchs' ID that the packets are currently using, the order matters
     * @param listAvoidID           List of the switchs' ID to avoid, the first ones are the most important to avoid
     * @param loadAvoidIDs          List of the respective current load (0-1) of each node IDs to avoid, the order matters
     * @return String               "Success" if the path was created, or an error message
     */
    public String createPathDetourSRv6(DeviceId srcSwitchDeviceId, DeviceId dstSwitchDeviceId, 
                                        Ip6Address srcIp, Ip6Address dstIp, int flow_label, 
                                        List<Integer> listPathID, List<Integer> listAvoidID, List<Float> loadAvoidIDs){
        
        //paths with less load (if ties, less count, if ties, does not change)
        int current_best_index = -1;  
        int current_best_count = Integer.MAX_VALUE;
        float current_best_load = Float.MAX_VALUE;
        List<DeviceId> current_best_new_path_DeviceIds = null;         //excludes first switch

        String result = "Success";
        List<Path> pathsList = new ArrayList<>();

        Topology currentTopology = topologyService.currentTopology();
        if (currentTopology == null){
            log.info("Topology service is null");
            return null;
        }

        //Retrieve best paths
        Set<Path> paths = topologyService.getPaths(currentTopology, srcSwitchDeviceId, dstSwitchDeviceId);

        pathsList.addAll(paths);
        if(pathsList.isEmpty()){
            log.info("No paths found");
            return "No paths found";
        }
        
        //------------------Go through all paths and find one with less load and uses the least of bad switches
        
        // Go through all paths and count the number of devices that match listAvoidID
        for (Path path : pathsList) {
            List<DeviceId> newPathDeviceIds = path.links().stream()        //each element is device:rx x is the number
                    .map(link -> link.dst().deviceId())
                    .collect(Collectors.toList());
            
            //------------------If only one path is found, we may just push SRv6 rule or not
            if(pathsList.size() == 1){
                //check if the new path is the same as the current path
                boolean same = comparePaths(listPathID, newPathDeviceIds);
                if(same){
                    log.info("The only path alternative is the same as the current path");
                    return "No path alternatives, no SRv6 rule created";
                }

                result = pushSRv6RuleForPath(srcSwitchDeviceId, srcIp, dstIp, flow_label, newPathDeviceIds);
                if (result == "Success") {
                    log.info("Only one path found, but different from current, SRv6 rule pushed: {}", newPathDeviceIds);
                }
                return result;
            }

            //------------------More that 1 possible path, find the best one
            // Prepare info for later comparasions, count the number/loiad of listAvoidID matches in this path
            int avoidIdCount = 0;
            float avoidIdLoad = 0;
            int node_index;
            for (DeviceId deviceId : newPathDeviceIds) {
                int id_number = extractDeviceId(deviceId.toString());
                if (listAvoidID.contains(id_number)) {
                    avoidIdCount++;
                    node_index   = listAvoidID.indexOf(id_number);
                    avoidIdLoad += loadAvoidIDs.get(node_index);
                }
            }

            //------------------check if we got a better path, first compare loads them count
            //select the path with the least load
            //if a tie select the path with the least avoidID count
            //if a tie, the paths are basically the same, do nothing
        
            if(current_best_index == -1){
                current_best_index = pathsList.indexOf(path);
                current_best_count = avoidIdCount;
                current_best_load = avoidIdLoad;
                current_best_new_path_DeviceIds = newPathDeviceIds;
            }
            else{
                if(avoidIdLoad < current_best_load){
                    current_best_index = pathsList.indexOf(path);
                    current_best_count = avoidIdCount;
                    current_best_load = avoidIdLoad;
                    current_best_new_path_DeviceIds = newPathDeviceIds;
                }
                else if(avoidIdLoad == current_best_load){
                    if(avoidIdCount < current_best_count){
                        current_best_index = pathsList.indexOf(path);
                        current_best_count = avoidIdCount;
                        current_best_load = avoidIdLoad;
                        current_best_new_path_DeviceIds = newPathDeviceIds;
                    }
                }
            }
        }
        
        //log.info("5-Best Path: index:{} - Count: {} - Load: {}", current_best_index, current_best_count, current_best_load);

        //---------------------------------Create the SRv6 policy
        result = pushSRv6RuleForPath(srcSwitchDeviceId, srcIp, dstIp, flow_label, current_best_new_path_DeviceIds);

        return result;
    }

    // ---------- END METHODS TO COMPLETE ----------------

    //--------------------------------------------------------------------------
    // EVENT LISTENERS
    //
    // Events are processed only if isRelevant() returns true.
    //--------------------------------------------------------------------------

    /**
     * Listener of device events.
     */
    public class InternalDeviceListener implements DeviceListener {

        @Override
        public boolean isRelevant(DeviceEvent event) {
            switch (event.type()) {
                case DEVICE_ADDED:
                case DEVICE_AVAILABILITY_CHANGED:
                    break;
                default:
                    // Ignore other events.
                    return false;
            }
            // Process only if this controller instance is the master.
            final DeviceId deviceId = event.subject().id();
            return mastershipService.isLocalMaster(deviceId);
        }

        @Override
        public void event(DeviceEvent event) {
            final DeviceId deviceId = event.subject().id();
            if (deviceService.isAvailable(deviceId)) {
                // A P4Runtime device is considered available in ONOS when there
                // is a StreamChannel session open and the pipeline
                // configuration has been set.
                mainComponent.getExecutorService().execute(() -> {
                    log.info("{} event! deviceId={}", event.type(), deviceId);

                    setUpMyUSidTable(event.subject().id());
                });
            }
        }
    }


    // ---------- UTILITY METHODS ----------------
    // UTILITY METHODS
    //--------------------------------------------------------------------------

    /**
     * Sets up SRv6 My SID table on all devices known by ONOS and for which this
     * ONOS node instance is currently master.
     */
    private synchronized void setUpAllDevices() {
        // Set up host routes
        stream(deviceService.getAvailableDevices())
                .map(Device::id)
                .filter(mastershipService::isLocalMaster)
                .forEach(deviceId -> {
                    log.info("*** SRV6 - Starting initial set up for {}...", deviceId);
                    this.setUpMyUSidTable(deviceId);
                });
    }

    /**
     * Returns the Srv6 config for the given device.
     *
     * @param deviceId the device ID
     * @return Srv6  device config
     */
    private Optional<Srv6DeviceConfig> getDeviceConfig(DeviceId deviceId) {
        Srv6DeviceConfig config = networkConfigService.getConfig(deviceId, Srv6DeviceConfig.class);
        return Optional.ofNullable(config);
    }
    
    /**
     * Returns Srv6 uSID for the given device.
     *
     * @param deviceId the device ID
     * @return SID for the device
     */
    private Ip6Address getMyUSid(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::myUSid)
                .orElseThrow(() -> new RuntimeException(
                        "Missing myUSid config for " + deviceId));
    }

    /**
     * Returns Srv6 uDX for the given device.
     *
     * @param deviceId the device ID
     * @return uDX for the device
     */
    private Ip6Address getMyUDX(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::myUDX)
                .orElse(null);
    }

    /**
     * Returns subNetIP for the given device.
     * @param deviceId the device ID
     * @return subNetIP for the device
     */
    private Ip6Address getMySubNetIP(DeviceId deviceId) {   //only on this class just to not create a new class for this, the same for the netcfg.json
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::mySubNetIP)
                .orElse(null);
    }


    /**
     * Receives an int with the number of the most relevant bits of a mask, 
     * and returns a byte array with said mask.
     * The total length of the byte array is 16 (same as an IPv6 address), 
     * and the mask is placed in the most significant bits.
     *
     * @param mask an integer representing the number of most relevant bits
     * @return a byte array of length 16 with the mask
     */
    public byte[] convertIntToByteArray(int mask) {
        // Create a byte array of length 16
        byte[] byteArray = new byte[16];
        
        // Calculate the number of full bytes and remaining bits
        int fullBytes = mask / 8;
        int remainingBits = mask % 8;

        // Set the full bytes to 0xFF (all bits set)
        for (int i = 0; i < fullBytes; i++) {
            byteArray[i] = (byte) 0xFF;
        }

        // Set the remaining bits in the next byte, if there are any
        if (remainingBits > 0) {
            byteArray[fullBytes] = (byte) (0xFF << (8 - remainingBits));
        }

        // The rest of the byteArray is already 0 by default
        //log.info("\nReceived Mask: {} \nConverted to: {}", mask, byteArray);
        return byteArray;
    }

    /*
     * Extracts the device ID from a device string.
     */
    public static int extractDeviceId(String deviceString) {
        Pattern pattern = Pattern.compile("device:r(\\d+)");
        Matcher matcher = pattern.matcher(deviceString);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new IllegalArgumentException("Invalid device string format: " + deviceString);
        }
    }

    /**
     * Compares the current path with the new path.
     * 
     * @param listPathID        List of the switchs' ID that the packets are currently using, the order matters
     * @param newPathDeviceIds  List of the switchs' ID that the packets will use in the new path, the order matters
     * @return boolean          true if the paths are the same, false otherwise
     */
    public static boolean comparePaths(List<Integer> listPathID, List<DeviceId> newPathDeviceIds){
        log.info("Comparing current path with new path");
        if(listPathID.size() == newPathDeviceIds.size()){
            int num_equals = 0;
            for(int i = 0; i < listPathID.size(); i++){
                int a = listPathID.get(i);
                int b = extractDeviceId(newPathDeviceIds.get(i).toString());
                if(a == b){ 
                    if(++num_equals == listPathID.size()){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Creates/Pushes an SRv6 rule for a given path, it receives the IDs of the switches in the path.
     * We can only specify 4 nodes, the last one in the rule always the dstSwitchDeviceId.
     * If the COMPLETE path has 5 or less nodes (including source/destination switches), the SRv6 will set the exact path.
     * If the path has more than 5 nodes, the SRv6 will set the first 3 nodes and the last one will be the dstSwitchDeviceId,
     * the path between the 3rd and the last node will not be forced and be whatever routing algorithm is being used.
     *    
     * @param srcSwitchDeviceId     device ID of the switch connected to the source host
     * @param srcIp                 target src IP address for the SRv6 policy
     * @param dstIp                 target dst IP address for the SRv6 policy
     * @param flow_label            target flow label for the SRv6 policy
     * @param newPathDeviceIds      List of the switchs' ID that the packets will use in the new path, the order matters (exludes source switch)
     */
    public String pushSRv6RuleForPath(DeviceId srcSwitchDeviceId, Ip6Address srcIp, Ip6Address dstIp, int flow_label, 
                                            List<DeviceId> newPathDeviceIds){
        String result = "Success";
        String SIDstring = "fcbb:bb00";
        //fcbb:bb00:7:1:8:2:fd00::    exemple, means leave source switch, go to 7, 1, 8 and then to dst switch 2, the nodes do not need to be consecutive
        
        //add at the most 3 nodes to the SID, excludes source switch
        for(int i = 0; i < newPathDeviceIds.size(); i++){
            int number_id = extractDeviceId(newPathDeviceIds.get(i).toString());
            String s = Integer.toHexString(number_id);
            SIDstring += ":" + s;
            if(i == 2){                     //At the most I can only set the first 3 nodes, leave space to dst switch
                break;
            } 
        }
        //set last node as the destination switch, if the loop did not do it already
        if(newPathDeviceIds.size() >= 4){
            int last = newPathDeviceIds.size() - 1;
            int number_id = extractDeviceId(newPathDeviceIds.get(last).toString());
            String s = Integer.toHexString(number_id);
            SIDstring += ":" + s;
        }

        SIDstring += ":fd00::";         //close the SID

        //convert String to IP6Address
        Ip6Address SID = Ip6Address.valueOf(SIDstring);
        
        //List of IP6Address
        List<Ip6Address> segmentList = new ArrayList<>();
        segmentList.add(SID);
        
        log.info("SRv6 rule pushed to cause detour: {}", segmentList);

        insertSrv6InsertRule(srcSwitchDeviceId, srcIp, dstIp, flow_label, 128, 128, 255, segmentList);
        return result;
    }


}
