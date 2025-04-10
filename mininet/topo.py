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
import importlib
import interface
import constants

from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo
from mininet.link import TCLink

from stratum import StratumBmv2Switch
from host6 import IPv6Host

CPU_PORT = constants.CPU_PORT

host_IPs = constants.host_IPs

I_I_bw = constants.network_config['INFRA_INFRA']['bw']
I_I_max_queue = constants.network_config['INFRA_INFRA']['max_queue']
I_I_delay = constants.network_config['INFRA_INFRA']['delay']
I_I_jitter = constants.network_config['INFRA_INFRA']['jitter']
I_I_loss = constants.network_config['INFRA_INFRA']['loss']

I_V_bw = constants.network_config['INFRA_VEHICULE']['bw']
I_V_max_queue = constants.network_config['INFRA_VEHICULE']['max_queue']
I_V_delay = constants.network_config['INFRA_VEHICULE']['delay']
I_V_jitter = constants.network_config['INFRA_VEHICULE']['jitter']
I_V_loss = constants.network_config['INFRA_VEHICULE']['loss']

V_V_bw = constants.network_config['VEHICULE_VEHICULE']['bw']
V_V_max_queue = constants.network_config['VEHICULE_VEHICULE']['max_queue']
V_V_delay = constants.network_config['VEHICULE_VEHICULE']['delay']
V_V_jitter = constants.network_config['VEHICULE_VEHICULE']['jitter']
V_V_loss = constants.network_config['VEHICULE_VEHICULE']['loss']

H_V_bw = constants.network_config['HOST_VEHICULE']['bw']
H_V_max_queue = constants.network_config['HOST_VEHICULE']['max_queue']
H_V_delay = constants.network_config['HOST_VEHICULE']['delay']
H_V_jitter = constants.network_config['HOST_VEHICULE']['jitter']
H_V_loss = constants.network_config['HOST_VEHICULE']['loss']

r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14 = None, None, None, None, None, None, None, None, None, None, None, None, None, None
h1_1, h1_2, h2_1, h2_2, h3_1, h5_1, h7_1, h7_2, h7_3, h8_1, h8_2, h8_3, h8_4 = None, None, None, None, None, None, None, None, None, None, None, None, None

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

    def create_switch(self):
        global r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14 
        global h1_1, h1_2, h2_1, h2_2, h3_1, h5_1, h7_1, h7_2, h7_3, h8_1, h8_2, h8_3, h8_4
        global I_I_bw, I_I_max_queue, I_I_delay, I_I_jitter, I_I_loss
        global I_V_bw, I_V_max_queue, I_V_delay, I_V_jitter, I_V_loss
        global V_V_bw, V_V_max_queue, V_V_delay, V_V_jitter, V_V_loss

        # End routers
        r1  = self.addSwitch('r1', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info") #, loglevel="info"
        r2  = self.addSwitch('r2', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")

        # Transit routers
        r3  = self.addSwitch('r3', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")
        r4  = self.addSwitch('r4', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")
        r5  = self.addSwitch('r5', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")
        r6  = self.addSwitch('r6', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")
        r7  = self.addSwitch('r7', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")
        r8  = self.addSwitch('r8', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")
        r9  = self.addSwitch('r9', cls=StratumBmv2Switch,  cpuport=CPU_PORT, loglevel="info")
        r10 = self.addSwitch('r10', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r11 = self.addSwitch('r11', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r12 = self.addSwitch('r12', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r13 = self.addSwitch('r13', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")
        r14 = self.addSwitch('r14', cls=StratumBmv2Switch, cpuport=CPU_PORT, loglevel="info")

        # Links
        # For each Link added:
        # Add it to one of the 4 link types (infra-infra, infra-vehicule, vehicule-vehicule, host-vehicule) in the section "links" im config\netcfg.json
        # If intended to send broadcast packets, add the port (that sends) to the "ports" section in config\netcfg.json 
        # usualy the port that connects to the next switch that know how to solve the broadcast or links to it the same way
        self.addLink(r1, r4,  port1 = 1,  port2 = 1, cls=TCLink, bw=V_V_bw, max_queue_size = V_V_max_queue, delay=V_V_delay, jitter = V_V_jitter, loss = V_V_loss, use_hfsc=True)
        self.addLink(r1, r9,  port1 = 2,  port2 = 1, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)

        self.addLink(r2, r3,  port1 = 1,  port2 = 1, cls=TCLink, bw=V_V_bw, max_queue_size = V_V_max_queue, delay=V_V_delay, jitter = V_V_jitter, loss = V_V_loss, use_hfsc=True)
        self.addLink(r2, r14, port1 = 2,  port2 = 1, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)

        self.addLink(r9, r4,   port1 = 2, port2 = 2, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)
        self.addLink(r9, r10,  port1 = 3, port2 = 1, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)
        self.addLink(r9, r13,  port1 = 4, port2 = 1, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)
        self.addLink(r9, r14,  port1 = 5, port2 = 2, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)

        self.addLink(r14, r10, port1 = 3, port2 = 2, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)
        self.addLink(r14, r3,  port1 = 4, port2 = 2, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)
        self.addLink(r14, r13, port1 = 5, port2 = 2, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)

        self.addLink(r4, r5,   port1 = 3, port2 = 1, cls=TCLink, bw=V_V_bw, max_queue_size = V_V_max_queue, delay=V_V_delay, jitter = V_V_jitter, loss = V_V_loss, use_hfsc=True)
        self.addLink(r4, r10,  port1 = 4, port2 = 3, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)

        self.addLink(r3, r13,  port1 = 3, port2 = 3, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)
        self.addLink(r3, r6,   port1 = 4, port2 = 1, cls=TCLink, bw=V_V_bw, max_queue_size = V_V_max_queue, delay=V_V_delay, jitter = V_V_jitter, loss = V_V_loss, use_hfsc=True)

        self.addLink(r10, r5,  port1 = 4, port2 = 2, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)
        self.addLink(r10, r11, port1 = 5, port2 = 1, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)
        self.addLink(r10, r12, port1 = 6, port2 = 1, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)
        self.addLink(r10, r13, port1 = 7, port2 = 4, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)

        self.addLink(r13, r11, port1 = 5, port2 = 2, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)
        self.addLink(r13, r12, port1 = 6, port2 = 2, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)
        self.addLink(r13, r6,  port1 = 7, port2 = 2, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)

        self.addLink(r5, r8,   port1 = 3, port2 = 1, cls=TCLink, bw=V_V_bw, max_queue_size = V_V_max_queue, delay=V_V_delay, jitter = V_V_jitter, loss = V_V_loss, use_hfsc=True)
        self.addLink(r5, r11,  port1 = 4, port2 = 3, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)

        self.addLink(r6, r7,   port1 = 3, port2 = 1, cls=TCLink, bw=V_V_bw, max_queue_size = V_V_max_queue, delay=V_V_delay, jitter = V_V_jitter, loss = V_V_loss, use_hfsc=True)
        self.addLink(r6, r12,  port1 = 4, port2 = 3, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)

        self.addLink(r11, r8,  port1 = 4, port2 = 2, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)
        self.addLink(r11, r12, port1 = 5, port2 = 4, cls=TCLink, bw=I_I_bw, max_queue_size = I_I_max_queue, delay=I_I_delay, jitter = I_I_jitter, loss = I_I_loss, use_hfsc=True)

        self.addLink(r12, r7,  port1 = 5, port2 = 2, cls=TCLink, bw=I_V_bw, max_queue_size = I_V_max_queue, delay=I_V_delay, jitter = I_V_jitter, loss = I_V_loss, use_hfsc=True)

        self.addLink(r8, r7,   port1 = 3, port2 = 3, cls=TCLink, bw=V_V_bw, max_queue_size = V_V_max_queue, delay=V_V_delay, jitter = V_V_jitter, loss = V_V_loss, use_hfsc=True)

    def create_hosts(self):
        global r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14 
        global h1_1, h1_2, h2_1, h2_2, h3_1, h5_1, h7_1, h7_2, h7_3, h8_1, h8_2, h8_3, h8_4
        global H_V_bw, H_V_max_queue, H_V_delay, H_V_jitter, H_V_loss

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
                            ipv6=host_IPs['h1_1'], ipv6_gw='2001:1:1::ff')
        h1_2 = self.addHost('h1_2', cls=IPv6Host, mac="00:00:00:00:00:11",
                            ipv6=host_IPs['h1_2'], ipv6_gw='2001:1:1::ff')
        
        # IPv6 hosts attached to r2
        h2_1 = self.addHost('h2_1', cls=IPv6Host, mac="00:00:00:00:00:20",
                            ipv6=host_IPs['h2_1'], ipv6_gw='2001:1:2::ff')
        h2_2 = self.addHost('h2_2', cls=IPv6Host, mac="00:00:00:00:00:21",
                            ipv6=host_IPs['h2_2'], ipv6_gw='2001:1:2::ff')
        
        # IPv6 hosts attached to r3
        h3_1 = self.addHost('h3_1', cls=IPv6Host, mac="00:00:00:00:00:30",
                            ipv6=host_IPs['h3_1'], ipv6_gw='2001:1:3::ff')
        
        
        # IPv6 hosts attached to r5
        h5_1 = self.addHost('h5_1', cls=IPv6Host, mac="00:00:00:00:00:50",
                            ipv6=host_IPs['h5_1'], ipv6_gw='2001:1:5::ff')
        
        # IPv6 hosts attached to r7
        h7_1 = self.addHost('h7_1', cls=IPv6Host, mac="00:00:00:00:00:70",
                            ipv6=host_IPs['h7_1'], ipv6_gw='2001:1:7::ff')
        h7_2 = self.addHost('h7_2', cls=IPv6Host, mac="00:00:00:00:00:71",
                            ipv6=host_IPs['h7_2'], ipv6_gw='2001:1:7::ff')
        h7_3 = self.addHost('h7_3', cls=IPv6Host, mac="00:00:00:00:00:72",
                            ipv6=host_IPs['h7_3'], ipv6_gw='2001:1:7::ff')

        # IPv6 hosts attached to r8
        h8_1 = self.addHost('h8_1', cls=IPv6Host, mac="00:00:00:00:00:80",
                            ipv6=host_IPs['h8_1'], ipv6_gw='2001:1:8::ff')
        h8_2 = self.addHost('h8_2', cls=IPv6Host, mac="00:00:00:00:00:81",
                            ipv6=host_IPs['h8_2'], ipv6_gw='2001:1:8::ff')
        h8_3 = self.addHost('h8_3', cls=IPv6Host, mac="00:00:00:00:00:82",
                            ipv6=host_IPs['h8_3'], ipv6_gw='2001:1:8::ff')
        h8_4 = self.addHost('h8_4', cls=IPv6Host, mac="00:00:00:00:00:83",
                            ipv6=host_IPs['h8_4'], ipv6_gw='2001:1:8::ff')
        
        # Hosts Links
        self.addLink(h1_1, r1, port2=11, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        self.addLink(h1_2, r1, port2=12, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)

        self.addLink(h2_1, r2, port2=11, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        self.addLink(h2_2, r2, port2=12, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)

        self.addLink(h3_1, r3, port2=11, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        
        self.addLink(h5_1, r5, port2=11, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)

        self.addLink(h7_1, r7, port2=11, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        self.addLink(h7_2, r7, port2=12, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        self.addLink(h7_3, r7, port2=13, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)

        self.addLink(h8_1, r8, port2=11, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        self.addLink(h8_2, r8, port2=12, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        self.addLink(h8_3, r8, port2=13, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)
        self.addLink(h8_4, r8, port2=14, cls=TCLink, bw=H_V_bw, max_queue_size=H_V_max_queue, delay=H_V_delay, jitter=H_V_jitter, loss=H_V_loss, use_hfsc=True)

    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)

        global r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11, r12, r13, r14 

        self.create_switch()
        self.create_hosts()

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


    while True:
        try:
            importlib.reload(interface)  # TO MAKE DEGUG EASIER: Reload the module to reflect any changes, after any choice
            interface.print_menu()
            choice = int(input("Enter the number of your choice:"))
            result = interface.main_menu(net, choice)
            if not result:          
                break

        except ValueError:
            print("Invalid input. Please enter a number.")
            continue


if __name__ == "__main__":
    main()
