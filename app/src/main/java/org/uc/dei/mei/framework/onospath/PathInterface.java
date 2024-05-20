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

import org.onosproject.net.ElementId;
import org.onosproject.net.Path;
import org.onosproject.net.topology.LinkWeigher;
import org.uc.dei.mei.framework.algorithm.AlgoHelper;


public interface PathInterface {

    String getDisjoint(ElementId srcID, ElementId dstID, String weigher);
    Path getK(ElementId srcID, ElementId dstID, String weigher, String service_str);
    Path getKAlgo(ElementId srcID, ElementId dstID, String service_str, AlgoHelper algohelper);


}