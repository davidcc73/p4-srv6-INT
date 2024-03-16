package org.onosproject.srv6_usid;

import org.onosproject.core.ApplicationId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.runtime.PiAction;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.srv6_usid.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * App component that configures devices to provide IPv6 routing capabilities
 * across the whole fabric.
 */
@Component(
        immediate = true,
        enabled = true,
        service = INTComponent.class
)
public class INTComponent {

    private static final Logger log = LoggerFactory.getLogger(INTComponent.class);

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
     * Creates a rule INT Transit rule about wich info to collect
     * <p>
     * function called by INTTransitInsert command
     *
     * @param deviceId       the device
     * @param key_value            key to be matched
     * @param action_name    action to be triggered
     */
    public FlowRule insertINT_TransitRule(DeviceId deviceId, String table, 
                                           int key_value, String action_name) {
        log.info("Started, insertINT_TransitRule()");
        final String tableId = "IngressPipeImpl." + action_name;
        final PiCriterion match = PiCriterion.builder()
                .matchExact(PiMatchFieldId.of("hdr.int_header.instruction_mask_0003"),
                            key_value)
                .build();

        final PiAction action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl." + action_name))
                .build();

        log.info("Pushing INT Transit rule to device {} NOT SURE THE IMPLEMENTATION IS WORKING", deviceId);  
        FlowRule t =  Utils.buildFlowRule(
                deviceId, appId, tableId, match, action); //NOT SURE IF THIS IS CORRECTLY ADDING NEW TABLE ENTRIES

        return t;
    }
}
