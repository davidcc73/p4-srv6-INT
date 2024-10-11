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
package org.onosproject.srv6_usid.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.srv6_usid.Srv6Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SRv6 Transit Insert Command
 */
@Service
@Command(scope = "onos", name = "srv6-insert",
        description = "Insert a SRv6 injection rule in a device SRv6 Transit table")
public class Srv6InsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID", required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "src_IP", description = "Target src IP address for the SRv6 policy", required = true, multiValued = false)
    String srcIp_value = null;

    @Argument(index = 2, name = "dst_IP", description = "Target dst IP address for the SRv6 policy",
    required = true, multiValued = false)
    String dstIp_value = null;

    @Argument(index = 3, name = "flow_lable", description = "Flow_lable for the SRv6 policy",
    required = true, multiValued = false)
    int flow_lable = 0;

    @Argument(index = 4, name = "srcMask", description = "Mask for the src IP address for the SRv6 policy",
    required = true, multiValued = false)
    int srcMask = 0;

    @Argument(index = 5, name = "dstMask", description = "Mask for the dst IP address for the SRv6 policy",
    required = true, multiValued = false)
    int dstMask = 0;

    @Argument(index = 6, name = "flowMask", description = "Mask for the flow label for the SRv6 policy",
    required = true, multiValued = false)
    int flowMask = 0;

    @Argument(index = 7, name = "segments", description = "SRv6 Segments (space separated list); last segment is target IP address",
    required = false, multiValued = true)
    @Completion(Srv6SidCompleter.class)
    List<String> segments = null;

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        Srv6Component app = get(Srv6Component.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }
        if (srcMask == 0 && dstMask == 0 && flowMask == 0) {
            print("At least one mask should be non-zero");
            return;
        }
        
        if (segments.size() == 0) {
            print("No segments listed");
            return;
        }
        
        Ip6Address srcIp = Ip6Address.valueOf(srcIp_value);
        Ip6Address dstIp = Ip6Address.valueOf(dstIp_value);

        List<Ip6Address> sids = segments.stream()
                .map(Ip6Address::valueOf)
                .collect(Collectors.toList());

        print("Installing path on device %s: %s",
                uri, sids.stream()
                        .map(IpAddress::toString)
                        .collect(Collectors.joining(", ")));
        
        app.insertSrv6InsertRule(device.id(), srcIp, dstIp, flow_lable, srcMask, dstMask, flowMask, sids);

    }

}
