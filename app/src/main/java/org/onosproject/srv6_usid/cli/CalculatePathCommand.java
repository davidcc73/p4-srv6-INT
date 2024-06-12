package org.onosproject.srv6_usid.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.srv6_usid.Ipv6RoutingComponent;


/**
 * Calculate-Routing-Path Command
 * It will calculate the best path to all devices and push those intructions to the devices in the path
 */
@Service
@Command(scope = "onos", name = "Calculate-Routing-Paths",
        description = "Changes the active criteria to calculate the routing path, calculates them and inserts the rules on the devices")
public class CalculatePathCommand extends AbstractShellCommand {
        @Argument(index = 0, name = "algorithm", 
                description = "either 'KShort' or 'ECMP'",
                required = true, multiValued = false)
        @Completion(DeviceIdCompleter.class)
        String algorithm = null;

        /*@Argument(index = 1, name = "secundary_weigher", 
            description = "Which secondary weigher criteria should be used to decide between same length paths, either 'latency' or 'bandwith'",
            required = true, multiValued = false)
        @Completion(DeviceIdCompleter.class)
        String secundary_weigher = null;*/
        
        @Override
        protected void doExecute() {
            String result = null;
            Ipv6RoutingComponent app = get(Ipv6RoutingComponent.class);

            if(!algorithm.equals("KShort") && !algorithm.equals("ECMP")){
                    print("Invalid algorithm, please use either 'KShort' or 'ECMP'");
                    return;
            }

            /*if(!secundary_weigher.equals("latency") && !secundary_weigher.equals("bandwith")){
                print("Invalid secundary_weigher, please use either 'latency' or 'bandwith'");
                return;
            }*/

            //Change path calculation criterias
            app.setAlgorithm(algorithm);
            //app.setPrioritize(secundary_weigher);
            print("Changed current algorithm to (" + algorithm + ")");

            print("Calculating and pushing routing rules...");
            result = app.recalculateAllPaths();
            print(result);
        }
}
