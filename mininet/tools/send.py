#!/usr/bin/env python
import argparse
import csv
from datetime import datetime
import fcntl
import os
import sys
import socket
import random
import time  # Add time module for sleep
import ipaddress
from scapy.all import sendp, get_if_list, get_if_addr, get_if_hwaddr
from scapy.all import Ether, IPv6, UDP, TCP
from scapy.all import srp, ICMPv6ND_NS


args = None

# Define the directory path inside the container
result_directory = "/INT/results"

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

def get_ipv6_address(iface):
    with open('/proc/net/if_inet6', 'r') as f:
        for line in f:
            parts = line.strip().split()
            if parts[-1] == iface:
                hex_ip = parts[0]
                # Convert hex string to an IPv6 address using ipaddress module
                ipv6_obj = ipaddress.IPv6Address(int(hex_ip, 16))
                return str(ipv6_obj)
    return None

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

def send_packet(args, pkt_ETHE, payload_space, iface, addr, src_ip):

    global my_IP
    results = {
        'first_timestamp': None,
        'failed_packets': 0
    }

    #prev_timestamp = None
    l3_layer = IPv6(src=src_ip, dst=addr, fl=args.flow_label , tc=args.dscp << 2)

    # Construct l4 layer, TCP or UDP
    if args.l4 == 'tcp':
        l4_layer = TCP(dport=args.port, sport=random.randint(49152, 65535))
    elif args.l4 == 'udp':
        l4_layer = UDP(dport=int(args.port), sport=random.randint(49152, 65535))

    Base_pkt = pkt_ETHE / l3_layer / l4_layer 
    my_IP = Base_pkt[IPv6].src

    for i in range(args.c):
        # Reset packet
        pkt = Base_pkt

        # Adjust payload for each packet
        payload = f"{i + 1}-{args.m}".encode()  # Convert payload to bytes
        
        # Ensure payload length matches payload_space
        if len(payload) < payload_space:
            trash_data = b'\x00' * (payload_space - len(payload))
            payload += trash_data
        elif len(payload) > payload_space:
            payload = payload[:payload_space]

        pkt = pkt / payload

        # Set the timestamp of the first packet sent
        if results['first_timestamp'] is None:
            dt = datetime.now()
            ts = datetime.timestamp(dt)
            results['first_timestamp'] = ts             
        
        '''
        #----------------------Record the current timestamp
        current_timestamp = datetime.timestamp(datetime.now())

        # Print the interval if previous timestamp exists
        
        if prev_timestamp is not None:
            interval = current_timestamp - prev_timestamp
            print(f"Interval since last packet: {interval:.6f} seconds, thr expected: {args.i:.6f} seconds")
        '''

        #pkt.show2()

        pre_timestamp = datetime.now()
        try:
            # Send the constructed packet
            sendp(pkt, iface=iface, inter=0, loop=0, verbose=False)
            #sendpfast(pkt, iface=iface, file_cache=True, pps=0, loop=0)
            print(f"({src_ip}, {args.dst_ip}, {args.flow_label}) Packet {i + 1} sent")
        except Exception as e:
            results['failed_packets'] += 1
            print(f"({src_ip}, {args.dst_ip}, {args.flow_label}) Packet {i + 1} failed to send: {e}")

        pkt_sending_time = datetime.now() - pre_timestamp
        pkt_sending_time_seconds = pkt_sending_time.total_seconds()
        #print(f"Packet sent in {pkt_sending_time_seconds} seconds")
        
        # Update previous timestamp
        #prev_timestamp = current_timestamp
        
        # Sleep for specified interval - the time it took to send the packet, must be subtracted
        rounded_number = round(args.i - pkt_sending_time_seconds)
        t = max(rounded_number, 0)
        time.sleep(t)
    
    return results

def export_results(results):
    # Write in the CSV file a line with the following format: 
    global args, result_directory
    num_packets_successefuly_sent = args.c - results['failed_packets']

    os.makedirs(result_directory, exist_ok=True)

    # Define the filename
    filename_results = args.export
    lock_filename = f"LOCK_{filename_results}"
    
    # Combine the directory path and filename
    full_path_results = os.path.join(result_directory, filename_results)
    full_path_LOCK = os.path.join(result_directory, lock_filename)
    
    
    # Open the lock file
    with open(full_path_LOCK, 'w') as lock_file:
        try:
            # Acquire an exclusive lock on the lock file
            fcntl.flock(lock_file, fcntl.LOCK_EX)
            
            # Check if the results file exists
            file_exists = os.path.exists(full_path_results)

            # Open the results file for appending
            print("Exporting results to", full_path_results)
            with open(full_path_results, mode='a', newline='') as file:
                # Create a CSV writer object
                writer = csv.writer(file)
                
                # If file does not exist, write the header row
                if not file_exists:
                    header = ["Iteration", "Host", "IP Source", "IP Destination", "Flow Label", "Is", "Number", "Timestamp (seconds-Unix Epoch)", "Nº pkt out of order", "Out of order packets", "DSCP", "Avg Jitter (Nanoseconds)"]
                    writer.writerow(header)
                
                # Prepare the data line
                timestamp_first_sent = results['first_timestamp']
                line = [args.iteration, args.me, my_IP, args.dst_ip, args.flow_label, "sender", num_packets_successefuly_sent, timestamp_first_sent, None, None, args.dscp, None]
                
                # Write data
                writer.writerow(line)
                
        finally:
            # Release the lock on the lock file
            fcntl.flock(lock_file, fcntl.LOCK_UN)


def parse_args():
    global args
    parser = argparse.ArgumentParser()

    parser = argparse.ArgumentParser(description='sender parser')
    parser.add_argument('--c', help='number of probe packets',
                        type=int, action="store", required=False,
                        default=1)
    
    parser.add_argument('--dst_ip', help='dst ip',
                        type=str, action="store", required=True)
    
    parser.add_argument('--flow_label', help="flow label", type=int,
                        action="store", required=True)
    
    parser.add_argument('--port', help="dest port", type=int,
                        action="store", required=True)
    
    parser.add_argument('--l4', help="layer 4 proto (tcp or udp)",
                        type=str, action="store", required=True)
    
    parser.add_argument('--m', help="message", type=str,
                        action='store', required=False, default="")
    
    parser.add_argument('--dscp', help="DSCP value", type=int,
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

def main():
    global args
    parse_args()

    addr_dst = get_ipv6_addr(args.dst_ip)  # Get IPv6 address

    interval = args.i
    iface = get_if()
    src_ip = get_ipv6_address(iface)
    src_mac = get_if_hwaddr(iface)

    print("Sending packets on interface {} (IP: {}, MAC: {}) to {} every {} seconds".format(iface, src_ip, src_mac, addr_dst, interval))
    
    dst_mac = '00:00:00:00:00:01'           #dummy value, currently no support for ARP and get the real MAC address of the destination
    pkt = Ether(src=src_mac, dst=dst_mac)

    header_size = check_header_size()

    payload_space = args.s - header_size

    results = send_packet(args, pkt, payload_space, iface, addr_dst, src_ip)

    if args.export is not None:
        # Export results
        export_results(results)


if __name__ == '__main__':
    main()
