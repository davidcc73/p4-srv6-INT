#!/usr/bin/env python
import argparse
import sys
import socket
import random
from time import sleep

from scapy.all import sendp, get_if_list, get_if_hwaddr
from scapy.all import Ether, IPv6, UDP, TCP

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

def main(args):
    addr = socket.gethostbyname(args.ip)
    iface = get_if()

    print("sending on interface %s to %s" % (iface, str(addr)))
    pkt = Ether(src=get_if_hwaddr(iface), dst='00:00:0a:00:03:02')     #i dont think the des_mac may be important

    if args.l4 == 'tcp':
        pkt = pkt / IPv6(dst=addr) / TCP(dport=args.port, sport=random.randint(49152, 65535)) / args.m
    elif args.l4 == 'udp':
        pkt = pkt / IPv6(dst=addr) / UDP(dport=int(args.port), sport=random.randint(49152, 65535)) / args.m
    pkt.show2()

    for i in range(args.c):
        sendp(pkt, iface=iface, verbose=False)
        sleep(1)

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
    args = parser.parse_args()
    main(args)
