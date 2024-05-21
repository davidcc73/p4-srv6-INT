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
package org.uc.dei.mei.framework.onospath;


import org.helper.CustomLinkWeigher;
import org.helper.EnergyLinkWeigher;
import org.helper.EnergyLinkWeigherAlgo;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.*;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uc.dei.mei.framework.algorithm.AlgoHelper;

import java.util.Comparator;
import java.util.NoSuchElementException;


@Component(immediate = true,
        service = {PathInterface.class}
)

public class ONOSAppPathComponent implements PathInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());



    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;



    @Activate
    protected void activate(){
        log.info("Started Path Component - rik");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped Path Component - rik");
    }


    @Override
    public String getDisjoint(ElementId srcID, ElementId dstID, String weigherSTR){

        LinkWeigher weigher = new CustomLinkWeigher();
        if(weigherSTR.equals("geo")) {
            weigher = null;
        }else if(weigherSTR.equals("hop")){
            weigher = HopCountLinkWeigher.DEFAULT_HOP_COUNT_WEIGHER;
        }else if(weigherSTR.equals("energy")){
            //weigher = new EnergyLinkWeigher("video", null);
        }

        System.out.println(pathService.getDisjointPaths(srcID, dstID, weigher));


        /*DeviceId src = DeviceId.deviceId("of:1000000000000006");
        DeviceId dst = DeviceId.deviceId("of:1000000000000009");

        HostId srch = HostId.hostId("AA:BB:CC:DD:00:0B/None");
        HostId dsth = HostId.hostId("AA:BB:CC:DD:00:0C/None");

        System.out.println("----------");


        //http://api.onosproject.org/1.13.2/org/onosproject/net/topology/PathService.html
        System.out.println("Dijont 06 to 09"+pathService.getDisjointPaths(src, dst));
        System.out.println("Dijont 0B(11) to 0C(12)"+pathService.getDisjointPaths(srch, dsth));

        System.out.println("----------");

        //System.out.println("K 06 to 09"+pathService.getKShortestPaths(src, dst).toString());

        pathService.getKShortestPaths(src, dst).forEach(s -> System.out.println("K 0B 06 to 09"+s));
        pathService.getKShortestPaths(srch, dsth).forEach(s -> System.out.println("K 0B(11) to 0C(12)"+s));

        System.out.println("----------");*/


        return "getDisjointPath";
    }


    @Override
    public Path getK(ElementId srcID, ElementId dstID, String weigherSTR, String service_str){

        LinkWeigher weigher = new CustomLinkWeigher();
        if(weigherSTR.equals("geo")) {
            weigher = null;
        }else if(weigherSTR.equals("hop")){
            weigher = HopCountLinkWeigher.DEFAULT_HOP_COUNT_WEIGHER;
        }else if(weigherSTR.equals("energy")){
            //weigher = new EnergyLinkWeigher(service_str, null);
            System.out.println("WARNING: EnergyLinkWeigher deactivated");
        }

        Path minPath = pathService.getKShortestPaths(srcID, dstID, weigher)
                                    .limit(50)
                                    .min(Comparator.comparing(Path::weight))
                                    .orElseThrow(NoSuchElementException::new);

        System.out.println(minPath.weight());
        //System.out.println(minPath.links());          //to see all info
        minPath.links().forEach(link -> {
            switch (link.type()) {
                case DIRECT:
                    System.out.println("switch-switch: " + link.src() + " -> " + link.dst());
                    break;
                case EDGE:
                    System.out.println("switch-host: " + link.src() + " -> " + link.dst());
                    break;
                default:
                    System.out.println("unknow link type: " + link.src() + " -> " + link.dst());
                    break;
            }
        });

        return minPath;
    }

    @Override
    public Path getKAlgo(ElementId srcID, ElementId dstID, String service_str, AlgoHelper algohelper){

        //LinkWeigher weigher  = new EnergyLinkWeigherAlgo(service_str, dataSource, algohelper);
        LinkWeigher weigher  = new EnergyLinkWeigherAlgo(service_str, algohelper);

        Path minPath = pathService.getKShortestPaths(srcID, dstID, weigher).limit(50).min(Comparator.comparing(Path::weight))
                .orElseThrow(NoSuchElementException::new);


        return minPath;

    }

}


