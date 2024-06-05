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
package org.onosproject.srv6_usid.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ElementId;
import org.onosproject.net.HostId;
import org.onosproject.net.topology.*;
import org.onosproject.net.Path;
import org.osgi.service.component.annotations.Reference;

import org.uc.dei.mei.framework.onospath.PathInterface;
import org.helper.CustomLinkWeigher;
import org.helper.EnergyLinkWeigher;


/*
import javax.sql.DataSource;
*/

/**
 * Apache Karaf CLI commands.
 */
@Service
@Command(scope = "onos", name = "path",
         description = "get paths + set path values/whigths ")   //in the command line: path

//http://khuhub.khu.ac.kr/2017000000/onos/commit/41fe1ecad03811adc49b2ef2406afc7027363272
//http://api.onosproject.org/1.13.2/org/onosproject/net/LinkKey.html
//http://api.onosproject.org/1.13.2/org/onosproject/net/config/basics/BasicLinkConfig.html#METRIC
/*
 * Receives 2 hosts, algorithm type, and returns the best path between them
 */
public class PathCalculationCommand extends AbstractShellCommand{


    /* path -kshort -hop AA:BB:CC:DD:00:06/None AA:BB:CC:DD:00:03/None

    DefaultPath{src=AA:BB:CC:DD:00:06/None/0, dst=AA:BB:CC:DD:00:03/None/0, type=INDIRECT, state=ACTIVE, expected=false, links=[DefaultEdgeLink{src=AA:BB:CC:DD:00:06/None/0, dst=of:0000000000000006/2, type=EDGE, state=ACTIVE, expected=false}, DefaultLink{src=of:0000000000000006/5, dst=of:0000000000000003/7, type=DIRECT, state=ACTIVE, expected=false}, DefaultEdgeLink{src=of:0000000000000003/2, dst=AA:BB:CC:DD:00:03/None/0, type=EDGE, state=ACTIVE, expected=false}], cost=ScalarWeight{value=3.0}}
      DefaultPath{src=AA:BB:CC:DD:00:06/None/0, dst=AA:BB:CC:DD:00:03/None/0, type=INDIRECT, state=ACTIVE, expected=false, links=[DefaultEdgeLink{src=AA:BB:CC:DD:00:06/None/0, dst=of:0000000000000006/2, type=EDGE, state=ACTIVE, expected=false}, DefaultLink{src=of:0000000000000006/7, dst=of:0000000000000005/7, type=DIRECT, state=ACTIVE, expected=false}, DefaultLink{src=of:0000000000000005/5, dst=of:0000000000000003/6, type=DIRECT, state=ACTIVE, expected=false}, DefaultEdgeLink{src=of:0000000000000003/2, dst=AA:BB:CC:DD:00:03/None/0, type=EDGE, state=ACTIVE, expected=false}], cost=ScalarWeight{value=4.0}}

    //path -disjoint -hop AA:BB:CC:DD:00:06/None AA:BB:CC:DD:00:03/None
    DefaultDisjointPath{src=AA:BB:CC:DD:00:06/None/0, dst=AA:BB:CC:DD:00:03/None/0, type=INDIRECT, state=ACTIVE, expected=false, links=[DefaultEdgeLink{src=AA:BB:CC:DD:00:06/None/0, dst=of:0000000000000006/2, type=EDGE, state=ACTIVE, expected=false}, DefaultLink{src=of:0000000000000006/5, dst=of:0000000000000003/7, type=DIRECT, state=ACTIVE, expected=false}, DefaultEdgeLink{src=of:0000000000000003/2, dst=AA:BB:CC:DD:00:03/None/0, type=EDGE, state=ACTIVE, expected=false}], cost=ScalarWeight{value=3.0}},
      DefaultDisjointPath{src=AA:BB:CC:DD:00:06/None/0, dst=AA:BB:CC:DD:00:03/None/0, type=INDIRECT, state=ACTIVE, expected=false, links=[DefaultEdgeLink{src=AA:BB:CC:DD:00:06/None/0, dst=of:0000000000000006/2, type=EDGE, state=ACTIVE, expected=false}, DefaultLink{src=of:0000000000000006/5, dst=of:0000000000000003/7, type=DIRECT, state=ACTIVE, expected=false}, DefaultEdgeLink{src=of:0000000000000003/2, dst=AA:BB:CC:DD:00:03/None/0, type=EDGE, state=ACTIVE, expected=false}], cost=ScalarWeight{value=3.0}},
    */

    @Option( name="-kshort",description = "k-shortes") boolean kshortBoll;                  //path calculations
    @Option( name="-disjoint",description = "get disjoint paths") boolean disjointBoll;

    @Option( name="-geo",description = "geo weigher ") boolean geoBoll;                     //No Weights
    @Option( name="-hop",description = "hop weigher ") boolean hopBoll;                     //Weights (Hop Count)
    @Option( name="-metric",description = "metric weigher ") boolean metricBoll;            //Weights (Energy)

    @Argument(index = 0, name = "srcElem", description = "src Element of path", required = false)   //end-point MAC Addr
    String srcElem_str;

    @Argument(index = 1, name = "dstElem", description = "dst Element of path", required = false)
    String dstElem_str;

    //@Reference
    //private DataSource dataSource;

    @Override
    protected void doExecute() {

        String service_str = "video";                                 //service type (for energy caculations)
        PathInterface pathService = get(PathInterface.class);
        Path minPath;

        /*LinkWeigher weigher = new CustomLinkWeigher();
        if(geoBoll == true) {
            weigher = null;
        }else if(hopBoll == true){
            weigher = HopCountLinkWeigher.DEFAULT_HOP_COUNT_WEIGHER;
        }else if(metricBoll == true){
            weigher = new EnergyLinkWeigher("video", dataSource);
        }*/
        String weigher = "custom";
        if(geoBoll == true) {
            weigher = "geo";
        }else if(hopBoll == true){
            weigher = "hop";
        }else if(metricBoll == true){
            weigher = "energy";
        }


        ElementId src = null;
        ElementId dst = null;
        if(srcElem_str.contains("device:")){  src = DeviceId.deviceId(srcElem_str);}
        else{                                   src = HostId.hostId(srcElem_str);}

        if(dstElem_str.contains("device:")){  dst = DeviceId.deviceId(dstElem_str);}
        else{                                   dst = HostId.hostId(dstElem_str);}


        if(disjointBoll == true){
            pathService.getDisjoint(src,dst, weigher);

        }else if(kshortBoll == true){
            print("calculating k-shortest path between %s and %s", src, dst);
            minPath = pathService.getK(src, dst, weigher, service_str);

            minPath.links().forEach(link -> {
                System.out.println(link.src() + " -> " + link.dst());
                }
            );
        }
    }
}
