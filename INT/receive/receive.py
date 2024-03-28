#!/usr/bin/env python
import sys
import struct
import os
import threading

from scapy.all import sniff, sendp, hexdump, get_if_list, get_if_hwaddr
from scapy.all import Packet, IPOption
from scapy.all import PacketListField, ShortField, IntField, LongField, BitField, FieldListField, FieldLenField
from scapy.all import IP, TCP, UDP, Raw
from scapy.layers.inet import _IPOption_HDR, TCP, bind_layers

stop_sniffing = False

def handle_pkt(pkt, iface):   #individually triggered by each sniffed packet
    if stop_sniffing:
        return
    print("got a packet on interface", iface)
    #we can add more conditions here to filter out packets, if IPv6, IPv4, TCP, ICMP, etc, see original script
    #pkt.show2()
    sys.stdout.flush()


def sniff_interface(iface):
    sniff(iface=iface, prn=lambda x: handle_pkt(x, iface), stop_filter=lambda _: stop_sniffing)

def main():   
    global stop_sniffing
    # Get all interface names ending with '900'
    ifaces = [i for i in os.listdir('/sys/class/net/') if i.endswith('900')]

    if ifaces:
        print("Sniffing on interfaces:", ifaces)
        threads = []
        try:
            for iface in ifaces:
                sys.stdout.flush()
                t = threading.Thread(target=sniff_interface, args=(iface,))
                threads.append(t)
                t.start()
            
            # Wait for all threads to finish
            for t in threads:
                t.join()
        except KeyboardInterrupt:
            stop_sniffing = True
            print("\nCtrl+C detected. Terminating...")
            sys.exit(0)
    else:
        print("No interfaces ending with '900' found.")

if __name__ == '__main__':
    main()
