//https://groups.google.com/a/onosproject.org/g/onos-dev/c/38IL1y3-TmY
package org.helper;

import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onosproject.net.DeviceId;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.TopologyEdge;



public final class CustomLinkWeigher implements LinkWeigher {

    @Override
    public Weight getInitialWeight() {
        return ScalarWeight.toWeight(0.0);
    }

    @Override
    public Weight getNonViableWeight() {
        return ScalarWeight.NON_VIABLE_WEIGHT;
    }

    @Override
    public Weight weight(TopologyEdge edge) {

        // If the link is a pair link just return infinite value
        if (false) {
            return ScalarWeight.NON_VIABLE_WEIGHT;
        }

        //DeviceId srcDeviceLink = edge.link().src().deviceId();
        // Identify the link as leaf-spine link


        // All other cases we return
        return ScalarWeight.toWeight(7.0);
    }


}