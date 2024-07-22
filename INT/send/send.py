#!/usr/bin/python3
import argparse
import csv
import fcntl
import os
import sys
import socket
import random
from time import sleep
from datetime import datetime


from scapy.all import sendp, get_if_list, get_if_hwaddr
from scapy.all import Ether, IPv6, UDP, TCP
from scapy.all import srp, ICMPv6ND_NS

# Define the directory path inside the container
result_directory = "/INT/results"
my_IP = None
args = None

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

# Check if the specified packet size is enough to include all the headers
def check_header_size():
    global args
    # Calculate the size of headers (Ethernet + IPv6 + TCP/UDP)
    if args.l4 == 'tcp':
        header_size = len(Ether() / IPv6() / TCP())
    elif args.l4 == 'udp':
        header_size = len(Ether() / IPv6() / UDP())

    # Check if the specified size is enough to include all the headers
    if args.s < header_size:
        print(f"Error: Specified size {args.s} bytes is not enough to include all the headers (at least {header_size} bytes needed).")
        sys.exit(1)

    return header_size

def send_packet(args, pkt_ETHE, payload_space, iface, addr):
    global my_IP
    results = {
        'first_timestamp': None,
        'failed_packets': 0
    }
    
    for i in range(args.c):
        # Reset packet
        pkt = pkt_ETHE

        # Adjust payload for each packet
        payload = f"{i + 1}-{args.m}".encode()  # Convert payload to bytes
        
        # Ensure payload length matches payload_space
        if len(payload) < payload_space:
            trash_data = b'\x00' * (payload_space - len(payload))
            payload += trash_data
        elif len(payload) > payload_space:
            payload = payload[:payload_space]
        
        # Construct IPv6 packet with either TCP or UDP
        if args.l4 == 'tcp':
            pkt = pkt / IPv6(dst=addr, tc=args.dscp << 2, fl=args.flow_label) / TCP(dport=args.port, sport=random.randint(49152, 65535)) / payload
        elif args.l4 == 'udp':
            pkt = pkt / IPv6(dst=addr, tc=args.dscp << 2, fl=args.flow_label) / UDP(dport=int(args.port), sport=random.randint(49152, 65535)) / payload
        
        my_IP = pkt[IPv6].src

        # Set the timestamp of the first packet sent
        if results['first_timestamp'] is None:
            dt = datetime.now()
            ts = datetime.timestamp(dt)
            results['first_timestamp'] = ts             #precision of microseconds
        
        try:
            # Send the constructed packet
            sendp(pkt, iface=iface, verbose=False)
        except Exception as e:
            results['failed_packets'] += 1
            print(f"Packet {i + 1} failed to send: {e}")

        # Sleep for specified interval
        sleep(args.i)
    
    return results

def parse_args():
    global args

    parser = argparse.ArgumentParser(description='sender parser')
    parser.add_argument('--c', help='number of probe packets',
                        type=int, action="store", required=False,
                        default=1)
    
    #parser.add_argument('--ip_src', help='src ip',
    #                    type=str, action="store", required=True)
    
    parser.add_argument('--ip_dst', help='dst ip',
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
        
    parser.add_argument('--s', help="packet's total size in bytes", type=int,
                        action='store', required=True)
    


    # Non-mandatory flag
    parser.add_argument('--export', help='File to export results', 
                        type=str, action='store', required=False, default=None)
    
    # Group of flags that are mandatory if --enable-feature is used
    parser.add_argument('--me', help='Name of the host running the script', 
                        type=str, action='store', required=False, default=None)
    parser.add_argument('--iteration', help='Current test iteration number', 
                        type=int, action='store', required=False, default=None)
    
    
    args = parser.parse_args()
    if args.export is not None:
        if not args.me:
            parser.error('--me is required when --export is used')
        if not args.iteration:
            parser.error('--iteration is required when --export is used')

def export_results(results):
    # Write in the CSV file a line with the following format: 
    global args, result_directory
    num_packets_successefuly_sent = args.c - results['failed_packets']

    # Define the filename
    filename = args.export
    
    # Combine the directory path and filename
    full_path = os.path.join(result_directory, filename)
    print("Exporting results to", full_path)
    
    os.makedirs(result_directory, exist_ok=True)
    file_exists = os.path.exists(full_path)
    
    # Write data to specific cells in CSV
    with open(full_path, mode='a', newline='') as file:
        # Acquire an exclusive lock on the file
        fcntl.flock(file, fcntl.LOCK_EX) 
        
        try:
            writer = csv.writer(file)
            
            # If file does not exist, write the header row
            if not file_exists:
                header = ["Iteration", "IP Source", "IP Destination", "Flow Label", "Is", "Number", "Timestamp (microseconds)", "Nº pkt out of order", "Out of order packets"]
                writer.writerow(header)
            
            # Prepare the data line
            timestamp_first_sent = results['first_timestamp']
            line = [args.iteration, my_IP, args.ip_dst, args.flow_label, "sender", num_packets_successefuly_sent, timestamp_first_sent]
            
            # Write data
            writer.writerow(line)
        
        finally:
            # Release the lock
            fcntl.flock(file, fcntl.LOCK_UN)


def main():
    global args
    parse_args()

    addr_dst = get_ipv6_addr(args.ip_dst)  # Get IPv6 address
    iface = get_if()

    print("sending on interface %s to %s" % (iface, str(addr_dst)))
    dst_mac = get_dest_mac(addr_dst, iface)
    pkt = Ether(src=get_if_hwaddr(iface), dst=dst_mac)

    header_size = check_header_size()

    payload_space = args.s - header_size

    results = send_packet(args, pkt, payload_space, iface, addr_dst)

    if args.export is not None:
        # Export results
        export_results(results)

if __name__ == '__main__':
    main()
