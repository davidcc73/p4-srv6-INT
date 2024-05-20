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
package org.uc.dei.mei.framework.algorithm;

import org.helper.LinkT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface AlgorithmInterface {

    ArrayList<String> getFairPath(String service_str);
    ArrayList<String> getONOSShortPath(String service_str);
    double getPacktDiffLink(String nodeNameSrc, String nodeNameDst);
    double getPacktDiffSW(String nodeNameSrc, String nodeNameDst);

    void hardUpdateLoss() ;
    String getStoredLoss();

    boolean getNoe();
    void setNoe(boolean noe);

    void addProcess();
    void removeProcess();

    String pullDBConf();
    String seePullDBConf();

    void resetFPathHistoy();
    void resetOPathHistory();

    ArrayList<String> getPHistF();
    ArrayList<String> getPHistO();

    String getAllTotalBW();
    void setLinkLossG(String src, String dst, String value);

    void getLinkLossDEBUG(String str, String str2);

    void getAlgos();

}