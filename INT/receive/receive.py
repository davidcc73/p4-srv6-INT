#!/usr/bin/env python
import csv
import fcntl
import queue
import sys
import os
import argparse
import threading
from scapy.all import sniff, get_if_hwaddr, TCP, UDP, IPv6

# Global variables to store metrics per flow
flows_metrics = {}
flows_lock = threading.Lock()

# Define the directory path inside the container
result_directory = "/INT/results"

args = None
packet_queue = queue.Queue()
out_of_order_packets = []

def get_if_with_zero():
    # Find all interfaces from /sys/class/net/
    ifaces = [i for i in os.listdir('/sys/class/net/') if '0' in i]
    
    # Filter interfaces that end with '0'
    iface = next((i for i in ifaces if i[-1] == '0'), None)
    
    if iface:
        return iface
    else:
        print("Cannot find any interface ending with '0'")
        exit(1)

def handle_pkt(pkt):
    packet_queue.put(pkt)

def process_packet(pkt):  # Process packets in queue
    #pkt.show2()  # Show packet details
    global flows_metrics  # Dictionary to track metrics per flow
    flow_key = None
    
    flow_key = (pkt[IPv6].src, pkt[IPv6].dst, pkt[IPv6].fl)

    if flow_key is None:
        return  # Ignore non-TCP/UDP packets

    # Extract and process the payload
    payload = None
    if TCP in pkt and pkt[TCP].payload:
        payload = bytes(pkt[TCP].payload).decode('utf-8', 'ignore')
    elif UDP in pkt and pkt[UDP].payload:
        payload = bytes(pkt[UDP].payload).decode('utf-8', 'ignore')

    with flows_lock:  # Ensure only one thread modifies flows_metrics at a time
        # Initialize flow metrics if this is the first packet for this flow
        if flow_key not in flows_metrics:
            flows_metrics[flow_key] = {
                "packet_count": 0,
                "sequence_numbers": [],
                "first_packet_time": pkt.time,
                "DSCP": pkt[IPv6].tc >> 2,
                "last_arrival_time": None,     # Track timestamp of the last packet arrival for jitter calculation
                "avg_jitter": None             # Store the average jitter for the flow
            }

        try:
            seq_number, message = payload.split('-', 1)
            seq_number = int(seq_number)  # Ensure the sequence number is an integer
            flows_metrics[flow_key]["sequence_numbers"].append(seq_number)
            print(f"Flow {flow_key} - Packet Sequence Number: {seq_number}")
        except ValueError:
            print(f"Flow {flow_key} - Error splitting payload: {payload}")

        
        #------------------Calculate Jitter------------------
        # Track the timestamp of the current packet arrival
        current_time = pkt.time
        if flows_metrics[flow_key]["avg_jitter"] is None:
            flows_metrics[flow_key]["avg_jitter"] = 0
        else:
            previous_pkt_count                    = flows_metrics[flow_key]["packet_count"]
            current_jitter                        = current_time - flows_metrics[flow_key]["last_arrival_time"]
            current_avg_jitter_undone             = flows_metrics[flow_key]["avg_jitter"] * previous_pkt_count
            flows_metrics[flow_key]["avg_jitter"] = (current_avg_jitter_undone + current_jitter) / (previous_pkt_count + 1)

        flows_metrics[flow_key]["last_arrival_time"] = current_time

        # Increment the packet count for this flow
        flows_metrics[flow_key]["packet_count"] += 1

    sys.stdout.flush()

def packet_processor():  # Thread that processes packets in queue
    while True:
        pkt = packet_queue.get()
        if pkt is None:
            break
        process_packet(pkt)
        packet_queue.task_done()

def terminate():
    print("Starting terminate")
    print("Flow Metrics Summary:")
    
    # Print metrics for each flow
    with flows_lock:
        for flow_key, metrics in flows_metrics.items():
            packet_count = metrics["packet_count"]
            sequence_numbers = metrics["sequence_numbers"]
            metrics["out_of_order_count"] = 0
            expected_seq = 0

            for seq in sequence_numbers:
                if seq < expected_seq:
                    metrics["out_of_order_count"] += 1
                else:
                    expected_seq = seq

            print(f"Flow {flow_key} - Sequence Numbers: {sequence_numbers}, Nº Received Packets: {packet_count}, Out of Order Packets Count: {metrics['out_of_order_count']}")

    if args.export:
        print("Exporting results...")
        export_results()

def export_results():
    print("Starting export_results()")
    global args
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

                with flows_lock:  # Ensure only one thread modifies flows_metrics at a time
                    for flow_key, metrics in flows_metrics.items():
                        src_ip, dst_ip, flow_label = flow_key
                        first_packet_time = metrics["first_packet_time"]
                        out_of_order_packets = metrics["out_of_order_count"] 
                        jitter = metrics["avg_jitter"] * 1000000000

                        line = [args.iteration, args.me, src_ip, dst_ip, flow_label, "receiver", metrics["packet_count"], first_packet_time, len(out_of_order_packets), out_of_order_packets, metrics["DSCP"], jitter]
                        
                        # Write data
                        writer.writerow(line)
        
        finally:
            # Release the lock
            fcntl.flock(lock_file, fcntl.LOCK_UN)
    print("Results exported")

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
                        type=float, action='store', required=False, default=None)
    
    args = parser.parse_args()
    if args.export is not None:
        if not args.me:
            parser.error('--me is required when --export is used')
        if not args.iteration:
            parser.error('--iteration is required when --export is used')

def main():
    global args
    parse_args()

    # Find interface ending in '0'
    iface = get_if_with_zero()
    
    # Capture only incoming IPv6 packets, no DNS nor ICMPv6
    bpf_filter = "ip6 and inbound and not ip6[6] = 58 and not port 53 and not port 5353"
    
    print(f"Starting sniffing for {args.duration} seconds...")
    processor_thread = threading.Thread(target=packet_processor)
    processor_thread.start()
    sniff(
        iface=iface, 
        prn=handle_pkt, 
        filter=bpf_filter,
        timeout=args.duration        # set timeout, if not set, sniff will run indefinitely
    )
    
    packet_queue.put(None)
    processor_thread.join()

    # Call terminate explicitly after the timeout
    terminate()

if __name__ == '__main__':
    main()
