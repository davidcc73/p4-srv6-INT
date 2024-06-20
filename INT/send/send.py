#!/usr/bin/python3
import argparse
import sys
import socket
import random
from time import sleep

from scapy.all import sendp, get_if_list, get_if_hwaddr
from scapy.all import Ether, IPv6, UDP, TCP
from scapy.all import srp, ICMPv6ND_NS

def get_if():
    ifs = get_if_list()
    iface = None
    for i in get_if_list():
        if "eth0" in i:
            iface = i
            break
    if not iface:
        print("Cannot find eth0 interface")
        exit(1)
    return iface

def get_dest_mac(ipv6_addr, iface):
    ns_pkt = Ether(dst="ff:ff:ff:ff:ff:ff") / IPv6(dst=ipv6_addr) / ICMPv6ND_NS()
    ans, _ = srp(ns_pkt, iface=iface, timeout=2, retry=2)
    for _, rcv in ans:
        return rcv[Ether].src
    
def get_ipv6_addr(hostname):
    # Get IPv6 address using getaddrinfo
    try:
        info = socket.getaddrinfo(hostname, None, socket.AF_INET6)
        ipv6_addr = [addr[4][0] for addr in info if addr[0] == socket.AF_INET6][0]
        return ipv6_addr
    except socket.gaierror as e:
        print("Error getting IPv6 address:", e)
        sys.exit(1)

def send_packet(args, pkt, iface):
    for i in range(args.c):
        sendp(pkt, iface=iface, verbose=False)
        sleep(args.i)

def main(args):
    addr = get_ipv6_addr(args.ip)  # Get IPv6 address
    iface = get_if()

    print("sending on interface %s to %s" % (iface, str(addr)))
    dst_mac = get_dest_mac(addr, iface)
    pkt = Ether(src=get_if_hwaddr(iface), dst=dst_mac)


    # Calculate the size of headers (Ethernet + IPv6 + TCP/UDP)
    if args.l4 == 'tcp':
        header_size = len(Ether() / IPv6() / TCP())
    elif args.l4 == 'udp':
        header_size = len(Ether() / IPv6() / UDP())

    # Check if the specified size is enough to include all the headers
    if args.size < header_size:
        print(f"Error: Specified size {args.size} bytes is not enough to include all the headers (at least {header_size} bytes needed).")
        sys.exit(1)
    remaining_size = args.size - header_size


    # Prepare the payload with the desired size
    payload = args.m.encode()
    if len(payload) < remaining_size:
        trash_data = b'\x00' * (remaining_size - len(payload))
        payload += trash_data
    elif len(payload) > remaining_size:
        payload = payload[:remaining_size]

    # Prepare the packet
    if args.l4 == 'tcp':
        pkt = pkt / IPv6(dst=addr, tc=args.dscp << 2, fl=args.flow_label) / TCP(dport=args.port, sport=random.randint(49152, 65535)) / payload
    elif args.l4 == 'udp':
        pkt = pkt / IPv6(dst=addr, tc=args.dscp << 2, fl=args.flow_label) / UDP(dport=int(args.port), sport=random.randint(49152, 65535)) / payload
    pkt.show2()

    send_packet(args, pkt, iface)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='sender parser')
    parser.add_argument('--c', help='number of probe packets',
                        type=int, action="store", required=False,
                        default=1)
    
    parser.add_argument('--ip', help='dst ip',
                        type=str, action="store", required=True)
    
    parser.add_argument('--port', help="dest port", type=int,
                        action="store", required=True)
    
    parser.add_argument('--l4', help="layer 4 proto (tcp or udp)",
                        type=str, action="store", required=True)
    
    parser.add_argument('--m', help="message", type=str,
                        action='store', required=False, default="")
    
    parser.add_argument('--dscp', help="DSCP value", type=int,
                        action='store', required=False, default=0)
    
    parser.add_argument('--flow_label', help="flow_label value", type=int,
                        action='store', required=False, default=0)
    
    parser.add_argument('--i', help="interval to send packets (second)", type=float,
                        action='store', required=False, default=1.0)
        
    parser.add_argument('--size', help="packet's total size in bytes", type=int,
                        action='store', required=True)
    
    args = parser.parse_args()
    main(args)
