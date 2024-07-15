#!/usr/bin/python3
import argparse
import csv
import sys
import struct
import os
import signal

from scapy.all import sniff, sendp, hexdump, get_if_list, get_if_hwaddr
from scapy.all import Packet, IPOption
from scapy.all import PacketListField, ShortField, IntField, LongField, BitField, FieldListField, FieldLenField
from scapy.all import IPv6, TCP, UDP, Raw
from scapy.layers.inet import _IPOption_HDR, TCP, bind_layers

# Global variables to count packets and store sequence numbers
packet_TCP_UDP_count = 0
sequence_numbers = []

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

def handle_pkt(pkt):
    global packet_TCP_UDP_count, sequence_numbers
    packet_TCP_UDP_count += 1

    print("got a TCP/UDP packet")

    #print("Original packet received:")
    #pkt.show2()
    #sys.stdout.flush()

    # Extract and print the message from the packet
    if TCP in pkt and pkt[TCP].payload:
        payload = pkt[TCP].payload.load.decode('utf-8', 'ignore')
    elif UDP in pkt and pkt[UDP].payload:
        payload = pkt[UDP].payload.load.decode('utf-8', 'ignore')
    
    try:
        seq_number, message = payload.split('-', 1)
        seq_number = int(seq_number)  # Ensure the sequence number is an integer
        sequence_numbers.append(seq_number)
        print(f"Packet Sequence Number: {seq_number}")
        print(f"Packet Message: {message}")
    except ValueError:
        print(f"Error splitting payload: {payload}")
    
    sys.stdout.flush()

def signal_handler(sig, frame):
    global sequence_numbers

    # Determine out-of-order packets by comparing each packet with the previous one
    out_of_order_packets = []
    last_seq_num = None

    print("all received:", sequence_numbers)
    for seq in sequence_numbers:
        if last_seq_num is not None and seq <= last_seq_num:
            out_of_order_packets.append(seq)
        last_seq_num = seq
    
    print("\nTotal TCP/UDP packets received:", packet_TCP_UDP_count)
    print("Out of order packets count:", len(out_of_order_packets))
    print("Out of order packets:", out_of_order_packets)

    save_to_file(packet_TCP_UDP_count, out_of_order_packets)
    sys.exit(0)

def save_to_file(packet_TCP_UDP_count, out_of_order_packets):
    # Define the directory path inside the container
    directory = "/INT/results"

    # Define the filename
    filename = args.f
    
    # Combine the directory path and filename
    full_path = os.path.join(directory, filename)
    
    # Write data to specific cells in CSV
    with open(full_path, mode='w', newline='') as file:
        writer = csv.writer(file)
        
        # Write header and values
        writer.writerow(["Description", "Value"])
        
        # Total TCP/UDP packets received
        writer.writerow(["Total TCP/UDP packets received:", packet_TCP_UDP_count])
        
        # Out of order packets count
        writer.writerow(["Out of order packets count:", len(out_of_order_packets)])
        
        # Write out of order packets
        writer.writerow(["Out of order packets:"])
        for i, seq in enumerate(out_of_order_packets, start=1):
            writer.writerow(["", f"Cell {i}: {seq}"])  # Modify this line to write to specific cells
    
    print(f"Results saved to {full_path}")



def main():
    global args  # Access the global args variable
    parser = argparse.ArgumentParser(description='receive parser')

    parser.add_argument('--f', help='filename to store results',
                        type=str, action="store", required=True)
    args = parser.parse_args()


    ifaces = [i for i in os.listdir('/sys/class/net/') if 'eth' in i]
    iface = ifaces[0]
    print("sniffing on %s" % iface)
    sys.stdout.flush()
    
    # Register Ctrl+C handler
    signal.signal(signal.SIGINT, signal_handler)

    sniff(iface=iface, filter='inbound and (tcp or udp) and not port 53 and not port 5353',    # Also Filter out (m)DNS packets
        prn=lambda x: handle_pkt(x))

if __name__ == '__main__':
    main()
