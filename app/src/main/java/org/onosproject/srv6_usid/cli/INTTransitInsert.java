
package org.onosproject.srv6_usid.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.srv6_usid.INTComponent;

import org.onosproject.net.flow.FlowRule;


/**
 *  INT Transit Insert Command
 */
@Service
@Command(scope = "onos", name = "INT_Transit-insert",
        description = "Insert a INT rule into the Transit table")
public class INTTransitInsert extends AbstractShellCommand {
//table      key     action                  no-arguments
        @Argument(index = 0, name = "table", 
                description = "table to insert the rule into",
                required = true, multiValued = false)
        @Completion(DeviceIdCompleter.class)
        String table = null;

        @Argument(index = 1, name = "key",
                description = "key value",
                required = true, multiValued = false)
        String key = null;

        @Argument(index = 2, name = "action",
                description = "action name",
                required = true, multiValued = false)
        String action = null;

        @Override
        protected void doExecute() {
                DeviceService deviceService = get(DeviceService.class);
                INTComponent app = get(INTComponent.class);

                for(int num = 1; num <= 14; num++) {
                        String uri = "device:r" + num;
                        Device device = deviceService.getDevice(DeviceId.deviceId(uri));

                        int intValue = Integer.parseInt(key.substring(2), 16);
                        //String hexValue = Integer.toHexString(intValue);
                        //System.out.println("Hexadecimal Value: 0x" + hexValue.toUpperCase());

                        if (device == null) {
                                print("Device \"%s\" is not found", uri);
                                continue;
                        }

                        print("Installing route on device %s", uri);
                        app.insertINT_TransitRule(device.id(), table, intValue, action);
                }
        }
}
