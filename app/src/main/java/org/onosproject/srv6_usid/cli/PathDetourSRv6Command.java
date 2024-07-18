package org.onosproject.srv6_usid.cli;

import java.util.ArrayList;
import java.util.List;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.Ip6Address;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.srv6_usid.Srv6Component;


/**
 *  Path Detour Command
 */
@Service
@Command(scope = "onos", name = "Path-Detour-SRv6",
        description = "Creates a SRv6 rule to a specific flow, making it acoid ceratin nodes")
public class PathDetourSRv6Command extends AbstractShellCommand{

    @Argument(index = 0, name = "uriSrc", description = "Source of Device ID", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String uriSrc = null;

    @Argument(index = 1, name = "uriDst", description = "Destination of Device ID", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String uriDst = null;

    @Argument(index = 2, name = "src_IP", description = "Target src IP address for the SRv6 policy", required = true, multiValued = false)
    String srcIp_value = null;

    @Argument(index = 3, name = "dst_IP", description = "Target dst IP address for the SRv6 policy",
    required = true, multiValued = false)
    String dstIp_value = null;

    @Argument(index = 4, name = "flow_lable", description = "Flow_lable for the SRv6 policy",
    required = true, multiValued = false)
    int flow_lable = 0;
    
    @Argument(index = 5, name = "currentIDs", description = "Array of node IDs that is currently used in the path, EXCLUDING THE 1º ONE, the order matters",
    required = true, multiValued = false)
    String pathIDsString = null;

    @Argument(index = 6, name = "avoidIDs", description = "Array of node IDs to avoid in the path detour SRv6 policy, the order matters",
    required = true, multiValued = false)
    String avoidIDsString = null;

    @Argument(index = 7, name = "load_avoidIDs", description = "Array of the respective current load (0-1) of each node IDs to avoid, the order matters",
    required = true, multiValued = false)
    String loadAvoidIDsString = null;

    @Override
    protected void doExecute() {
        int number;
        float number_float;
        String[] numberStrings;
        String result="placeholder";
        List<Integer> pathIDs = new ArrayList<>();
        List<Integer> avoidIDs = new ArrayList<>();
        List<Float> loadAvoidIDs = new ArrayList<>();
        DeviceService deviceService = get(DeviceService.class);
        Srv6Component app = get(Srv6Component.class);

        Device srcSwitchDevice = deviceService.getDevice(DeviceId.deviceId(uriSrc));
        Device dstSwitchDevice = deviceService.getDevice(DeviceId.deviceId(uriDst));
        if (srcSwitchDevice == null) {
            print("Device \"%s\" is not found", uriSrc);
            return;
        }
        if (dstSwitchDevice == null) {
            print("Device \"%s\" is not found", uriDst);
            return;
        }

        Ip6Address srcIp = Ip6Address.valueOf(srcIp_value);
        Ip6Address dstIp = Ip6Address.valueOf(dstIp_value);

        //-------------------Parse pathID, avoidID argument

        // Convert each number string to an integer and add it to the list
        numberStrings = pathIDsString.split("-");
        for (String numberString : numberStrings) {
            number = Integer.parseInt(numberString.trim());
            pathIDs.add(number);
        }

        numberStrings = avoidIDsString.split("-");
        for (String numberString : numberStrings) {
            number = Integer.parseInt(numberString.trim());
            avoidIDs.add(number);
        }

        numberStrings = loadAvoidIDsString.split("-");
        for (String numberString : numberStrings) {
            number_float = Float.parseFloat(numberString.trim());
            loadAvoidIDs.add(number_float);
        }

        /*
        for(int i=0; i<pathIDs.size(); i++){
            print("path: %d-%d", i, pathIDs.get(i));
        }
        for(int i=0; i<avoidIDs.size(); i++){
            print("path: %d-%d", i, avoidIDs.get(i));
        }
        for(int i=0; i<loadAvoidIDs.size(); i++){
            print("path: %d-%f", i, loadAvoidIDs.get(i));
        }*/

        print("Creating path detour using SRv6 policy");
        
        result = app.createPathDetourSRv6(srcSwitchDevice.id(), dstSwitchDevice.id(), 
                                            srcIp, dstIp, flow_lable, 
                                            pathIDs, avoidIDs, loadAvoidIDs);
        print(result);
    }
}