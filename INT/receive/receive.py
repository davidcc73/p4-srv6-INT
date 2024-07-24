#!/usr/bin/python3
import argparse
import csv
import fcntl
import sys
import os

from scapy.all import sniff, get_if_list
from scapy.all import TCP, UDP, IPv6
from scapy.layers.inet import  TCP

# Global variables to count packets and store sequence numbers
packet_TCP_UDP_count = 0
out_of_order_packets = []
sequence_numbers = []
results = {}

# Define the directory path inside the container
result_directory = "/INT/results"

args = None
#last_packet_time = None  # Initialize to keep track of the time of the last packet

def get_if():
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
    global packet_TCP_UDP_count, sequence_numbers, results #, last_packet_time
    packet_TCP_UDP_count += 1

    #print("got a TCP/UDP packet")

    #print("Original packet received:")
    #pkt.show2()
    #sys.stdout.flush()

    #----------Calculate and print the interval since the last packet
    '''
    if last_packet_time is not None:
        interval = pkt.time - last_packet_time
        print(f"Interval since last packet: {interval:.6f} seconds")
    
    # Update the last packet time
    last_packet_time = pkt.time
    '''
    
    #store flow info of the packet, and when was first packet received if not already stored
    if "flow" not in results:
        results["flow"] = (pkt[IPv6].src, pkt[IPv6].dst, pkt[IPv6].fl)
        results["first_packet_time"] = pkt.time
    

    #----------Extract and print the message from the packet
    if TCP in pkt and pkt[TCP].payload:
        payload = pkt[TCP].payload.load.decode('utf-8', 'ignore')
    elif UDP in pkt and pkt[UDP].payload:
        payload = pkt[UDP].payload.load.decode('utf-8', 'ignore')
    
    try:
        seq_number, message = payload.split('-', 1)
        seq_number = int(seq_number)  # Ensure the sequence number is an integer
        sequence_numbers.append(seq_number)
        print(f"Packet Sequence Number: {seq_number} Packet Message: {message}")
    except ValueError:
        print(f"Error splitting payload: {payload}")
    
    sys.stdout.flush()

'''
def a():
    full_path = os.path.join(result_directory, "receivers.csv")
    with open(full_path, mode='a', newline='') as file:
        writer = csv.writer(file)
        line = [args.me, args.iteration]
        writer.writerow(line)
'''

def terminate():
    print("staring terminate")

    global sequence_numbers, packet_TCP_UDP_count, out_of_order_packets

    # Determine out-of-order packets by comparing each packet with the previous one
    last_seq_num = None

    print("all received:", sequence_numbers)
    for seq in sequence_numbers:
        if last_seq_num is not None and seq <= last_seq_num:
            out_of_order_packets.append(seq)
        last_seq_num = seq
    
    print("\nTotal TCP/UDP packets received:", packet_TCP_UDP_count)
    print("Out of order packets count:", len(out_of_order_packets))
    print("Out of order packets:", out_of_order_packets)

    #a()  
    export_results()
    print("Results exported")

def export_results():
    global args, results, packet_TCP_UDP_count
    
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
                    header = ["Iteration", "IP Source", "IP Destination", "Flow Label", "Is", "Number", "Timestamp (microseconds)", "Nº pkt out of order", "Out of order packets"]
                    writer.writerow(header)

                #Prepare CSV line
                src_ip = results["flow"][0]
                dst_ip = results["flow"][1]
                flow_label = results["flow"][2]
                first_packet_time = results["first_packet_time"]
                line = [args.iteration, src_ip, dst_ip, flow_label, "receiver", packet_TCP_UDP_count, first_packet_time, len(out_of_order_packets), out_of_order_packets]

                # Write data
                writer.writerow(line)
        
        finally:
            # Release the lock
            fcntl.flock(lock_file, fcntl.LOCK_UN)

def parse_args():
    global args
    parser = argparse.ArgumentParser(description='receiver parser')

    # Non-mandatory flag
    parser.add_argument('--export', help='File to export results', 
                        type=str, action='store', required=False, default=None)
    
    # Group of flags that are mandatory if --export is used
    parser.add_argument('--me', help='Name of the host running the script', 
                        type=str, action='store', required=False, default=None)
    parser.add_argument('--iteration', help='Current test iteration number', 
                        type=int, action='store', required=False, default=None)
    parser.add_argument('--duration', help='Current test duration seconds', 
                        type=float, action='store', required=True, default=None)
    
    args = parser.parse_args()
    if args.export is not None:
        if not args.me:
            parser.error('--me is required when --export is used')
        if not args.iteration:
            parser.error('--iteration is required when --export is used')

def main():
    parse_args()

    ifaces = [i for i in os.listdir('/sys/class/net/') if 'eth' in i]
    iface = ifaces[0]
    print("sniffing on %s" % iface)
    sys.stdout.flush()
    
    # Using sniff with a timeout
    print(f"Starting sniffing for {args.duration} seconds...")
    sniff(
        iface=iface, 
        filter='inbound and (tcp or udp) and not port 53 and not port 5353',    # Also Filter out (m)DNS packets
        prn=lambda x: handle_pkt(x),
        timeout=int(args.duration)
    )
        
    # Call terminate explicitly after the timeout
    terminate()

if __name__ == '__main__':
    main()
