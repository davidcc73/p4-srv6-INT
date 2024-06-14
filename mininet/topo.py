#!/usr/bin/python3

#  Copyright 2019-present Open Networking Foundation
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.


from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo
from mininet.link import TCLink

from stratum import StratumBmv2Switch
from host6 import IPv6Host

CPU_PORT = 255

BW_INFRA_INFRA = 900                 #Bandwith   (Mbps)              Glass Fiber cable, 10 km
DL_INFRA_INFRA = 2                   #Delay      (ms) 

BW_INFRA_VEHICULE = 700              #Bandwith   (Mbps)              5G cellular towers, 10 km
DL_INFRA_VEHICULE = 20               #Delay      (ms)                (10-30 ms)

BW_VEHICULE_VEHICULE = 700           #Bandwith   (Mbps)              5G between cars, max 100 meters
DL_VEHICULE_VEHICULE = 7             #Delay      (ms)                (1-10 ms)

class TutorialTopo(Topo):
    
    """
    /--------\   /----\   /----\   /----\   /----\
    | Site A |---| R1 |---| R4 |---| R5 |---| R8 |
    \________/   \____/   \____/   \____/   \____/
                   |         |       |        |
                     \     /       |        |
					   R11		R10		 R9
				     / 	   \   
				   |				
    /--------\   /----\   /----\   /----\   /----\
    | Site B |---| R2 |---| R3 |---| R6 |---| R7 |
    \________/   \____/   \____/   \____/   \____/

    """


    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)

        # End routers
        r1 = self.addSwitch('r1', cls=StratumBmv2Switch,cpuport=CPU_PORT, loglevel="info") #, loglevel="info"
        r2 = self.addSwitch('r2', cls=StratumBmv2Switch,cpuport=CPU_PORT, loglevel="info")

        # Transit routers
        r3 = self.addSwitch('r3', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r4 = self.addSwitch('r4', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r5 = self.addSwitch('r5', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r6 = self.addSwitch('r6', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r7 = self.addSwitch('r7', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r8 = self.addSwitch('r8', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r9 = self.addSwitch('r9', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r10 = self.addSwitch('r10', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r11 = self.addSwitch('r11', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r12 = self.addSwitch('r12', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r13 = self.addSwitch('r13', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r14 = self.addSwitch('r14', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        
        # Links
        # For each Link added:
        # Add it to one of the 3 link types (infra-infra, infra-vehicule, vehicule-vehicule) in the section "links" im config\netcfg.json
        # If intended to send broadcast packets, add the port (that sends) to the "ports" section in config\netcfg.json 
        # usualy the port that connects to the next switch that know how to solve the broadcast or links to it the same way
        self.addLink(r1, r4,  port1 = 1,  port2 = 1, cls=TCLink, rate=BW_VEHICULE_VEHICULE, delay=DL_VEHICULE_VEHICULE)
        self.addLink(r1, r9,  port1 = 2,  port2 = 1, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)

        self.addLink(r2, r3,  port1 = 1,  port2 = 1, cls=TCLink, rate=BW_VEHICULE_VEHICULE, delay=DL_VEHICULE_VEHICULE)
        self.addLink(r2, r14, port1 = 2,  port2 = 1, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)

        self.addLink(r9, r4,   port1 = 2, port2 = 2, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)
        self.addLink(r9, r10,  port1 = 3, port2 = 1, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)
        self.addLink(r9, r13,  port1 = 4, port2 = 1, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)
        self.addLink(r9, r14,  port1 = 5, port2 = 2, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)

        self.addLink(r14, r10, port1 = 3, port2 = 2, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)
        self.addLink(r14, r3,  port1 = 4, port2 = 2, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)
        self.addLink(r14, r13, port1 = 5, port2 = 2, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)

        self.addLink(r4, r5,   port1 = 3, port2 = 1, cls=TCLink, rate=BW_VEHICULE_VEHICULE, delay=DL_VEHICULE_VEHICULE)
        self.addLink(r4, r10,  port1 = 4, port2 = 3, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)

        self.addLink(r3, r13,  port1 = 3, port2 = 3, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)
        self.addLink(r3, r6,   port1 = 4, port2 = 1, cls=TCLink, rate=BW_VEHICULE_VEHICULE, delay=DL_VEHICULE_VEHICULE)

        self.addLink(r10, r5,  port1 = 4, port2 = 2, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)
        self.addLink(r10, r11, port1 = 5, port2 = 1, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)
        self.addLink(r10, r12, port1 = 6, port2 = 1, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)
        self.addLink(r10, r13, port1 = 7, port2 = 4, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)

        self.addLink(r13, r11, port1 = 5, port2 = 2, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)
        self.addLink(r13, r12, port1 = 6, port2 = 2, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)
        self.addLink(r13, r6,  port1 = 7, port2 = 2, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)

        self.addLink(r5, r8,   port1 = 3, port2 = 1, cls=TCLink, rate=BW_VEHICULE_VEHICULE, delay=DL_VEHICULE_VEHICULE)
        self.addLink(r5, r11,  port1 = 4, port2 = 3, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)

        self.addLink(r6, r7,   port1 = 3, port2 = 1,  cls=TCLink, rate=BW_VEHICULE_VEHICULE, delay=DL_VEHICULE_VEHICULE)
        self.addLink(r6, r12,  port1 = 4, port2 = 3, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)

        self.addLink(r11, r8,  port1 = 4, port2 = 2, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)
        self.addLink(r11, r12, port1 = 5, port2 = 4, cls=TCLink, rate=BW_INFRA_INFRA, delay=DL_INFRA_INFRA)

        self.addLink(r12, r7,  port1 = 5, port2 = 2, cls=TCLink, rate=BW_INFRA_VEHICULE, delay=DL_INFRA_VEHICULE)

        self.addLink(r8, r7,   port1 = 3, port2 = 3, cls=TCLink, rate=BW_VEHICULE_VEHICULE, delay=DL_VEHICULE_VEHICULE)


        # Hosts
        #For each host added:
        #add it's info to the file of the switch connected to it at INT_Tables (the ports, to be Source and Sink to it)
        #add it's info to config\netcong.txt at (hosts), so ONOS can detect the IPv6Host (the mac)
        #at config\netcong.txt, add the port of the switch facing the new host to "ports" so it receives broadcast packets
        #add it's info to config\hosts_routing_tables.txt, so ONOS can map the IPs to the MACs
        #if the host is added to a new switch make sure that said switch contains a (uDX) defined in the netcfg.json file, so SRv6 can be (d)encapsulated
        #IPs must respect the subnet of their switch, see netcfg.json to see which subnet IP ONOS assumes each switch has
        
        # IPv6 hosts attached to r1
        h1_1 = self.addHost('h1_1', cls=IPv6Host, mac="00:00:00:00:00:10",
                            ipv6='2001:1:1::1/64', ipv6_gw='2001:1:1::ff')
        h1_2 = self.addHost('h1_2', cls=IPv6Host, mac="00:00:00:00:00:11",
                            ipv6='2001:1:1::2/64', ipv6_gw='2001:1:1::ff')
        
        # IPv6 hosts attached to r2
        h2_1 = self.addHost('h2_1', cls=IPv6Host, mac="00:00:00:00:00:20",
                            ipv6='2001:1:2::1/64', ipv6_gw='2001:1:2::ff')
        h2_2 = self.addHost('h2_2', cls=IPv6Host, mac="00:00:00:00:00:21",
                            ipv6='2001:1:2::2/64', ipv6_gw='2001:1:2::ff')
        
        # IPv6 hosts attached to r3
        h3_1 = self.addHost('h3_1', cls=IPv6Host, mac="00:00:00:00:00:30",
                            ipv6='2001:1:3::1/64', ipv6_gw='2001:1:3::ff')
        
        # IPv6 hosts attached to r8
        h8_1 = self.addHost('h8_1', cls=IPv6Host, mac="00:00:00:00:00:80",
                            ipv6='2001:1:8::1/64', ipv6_gw='2001:1:8::ff')
        h8_2 = self.addHost('h8_2', cls=IPv6Host, mac="00:00:00:00:00:81",
                            ipv6='2001:1:8::2/64', ipv6_gw='2001:1:8::ff')
        h8_3 = self.addHost('h8_3', cls=IPv6Host, mac="00:00:00:00:00:82",
                            ipv6='2001:1:8::3/64', ipv6_gw='2001:1:8::ff')
        h8_4 = self.addHost('h8_4', cls=IPv6Host, mac="00:00:00:00:00:83",
                            ipv6='2001:1:8::4/64', ipv6_gw='2001:1:8::ff')
        
        # Hosts Links
        self.addLink(h1_1, r1, port2=11)
        self.addLink(h1_2, r1, port2=12)

        self.addLink(h2_1, r2, port2=11)
        self.addLink(h2_2, r2, port2=12)

        self.addLink(h3_1, r3, port2=11)

        self.addLink(h8_1, r8, port2=11)
        self.addLink(h8_2, r8, port2=12)
        self.addLink(h8_3, r8, port2=13)
        self.addLink(h8_4, r8, port2=14)


        #---------------------INT POTION 
        #create the collector
        coll = self.addHost('coll', cls=IPv6Host, mac="00:00:00:00:00:05",
                            ipv6='2001:1:30::1/64', loglevel="info")        
        #port 100 of all leaf switchs, points to the collector
        self.addLink(coll, r1, port2 = 100)        
        self.addLink(coll, r2, port2 = 100)             
        self.addLink(coll, r3, port2 = 100)   
        self.addLink(coll, r4, port2 = 100)   
        self.addLink(coll, r5, port2 = 100)   
        self.addLink(coll, r6, port2 = 100)   
        self.addLink(coll, r7, port2 = 100)   
        self.addLink(coll, r8, port2 = 100)   


def main():
    topo = TutorialTopo()
    controller = RemoteController('c0', ip="127.0.0.1")


    net = Mininet(topo=topo, controller=None)
    net.addController(controller)

    net.start()


    CLI(net)
    net.stop()


if __name__ == "__main__":
    main()
