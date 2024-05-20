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
package org.uc.dei.mei.framework.flowrule;

import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;

public interface FlowRuleInterface {

    String addFlowMACARP(String deviceId, short appID, short timeout, int priority, int in_port,int out_port, String src_mac, String dst_mac);
    FlowRule addFlowMAC(String deviceId, short appID, short timeout, int priority, int in_port, int out_port, String src_mac, String dst_mac);
    String addFlowIP(String deviceId, short appID, short timeout, int priority, int in_port,int out_port, String src_mac, String dst_mac);
    void removeFlowRulesApp(short appID);

    Iterable<FlowEntry> getFlowRulesDevice(String deviceId);
    Integer getFlowRulesDevicePacketCount(short appID, String deviceId, String src_mac, String dst_mac );

}