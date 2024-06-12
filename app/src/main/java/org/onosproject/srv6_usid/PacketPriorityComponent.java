package org.onosproject.srv6_usid;

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


import org.onlab.packet.Ip6Address;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flow.criteria.PiCriterion.Builder;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.srv6_usid.MainComponent;
import org.onosproject.srv6_usid.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;


/**
 * App component that configures devices to provides the devices the ability to set priority levels for packets based on the DSCP value.
 * across the whole fabric.
 */
@Component(
        immediate = true,
        enabled = true,
        service = PacketPriorityComponent.class
)
public class PacketPriorityComponent{
    private static final Logger log = LoggerFactory.getLogger(PacketPriorityComponent.class);

    //private final LinkListener linkListener = new InternalLinkListener();
    //private final DeviceListener deviceListener = new InternalDeviceListener();

    private ApplicationId appId;

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

    //--------------------------------------------------------------------------
    // COMPONENT ACTIVATION.
    //
    // When loading/unloading the app the Karaf runtime environment will call
    // activate()/deactivate().
    //--------------------------------------------------------------------------

    @Activate
    protected void activate() {
        appId = mainComponent.getAppId();

        // linkService.addListener(linkListener);
        // deviceService.addListener(deviceListener);

        // Schedule set up for all devices.
        // mainComponent.scheduleTask(this::setUpAllDevices, INITIAL_SETUP_DELAY);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        //linkService.removeListener(linkListener);
        //deviceService.removeListener(deviceListener);

        log.info("Stopped");
    }


    /**
     * Creates a rule to bind a DSCP value to a priority value
     * <p>
     * function called by Packet_Priority-Insert command
     *
     * @param deviceId       the device
     * @param key_value      key to be matched
     * @param arg_value      arg used by the action
    */
    public String insertRule_SetDSCP_Priority(DeviceId deviceId, int key_value, int arg_value) {
        String result = null;
        String tableId =  "IngressPipeImpl.set_priority_from_dscp";
        String actionId = "IngressPipeImpl.set_priority_value";

        //log.info("Inserting a table rule for device {} and table set_priority_from_dscp", deviceId);

        final PiAction action = PiAction.builder()
            .withId(PiActionId.of(actionId))                                          // Action name as specified
            .withParameter(new PiActionParam(PiActionParamId.of("value"), arg_value)) // Set action parameter
            .build();
        
        final PiCriterion match = PiCriterion.builder()
            .matchExact(PiMatchFieldId.of("local_metadata.OG_dscp"), key_value)
            .build();

        try{
            //log.info("Pushing priority mapping rule to device {}", deviceId);  
            final FlowRule rule = Utils.buildFlowRule(deviceId, appId, tableId, match, action);
            flowRuleService.applyFlowRules(rule);
            result = "Success";        
        }
        catch(Exception e){
                log.info("Error pushing INT Transit rule to device {}:\n {}", deviceId, e);
                result = "Something went wrong, see ONOS logs for more details"; 
        }
        return result;
    }
}