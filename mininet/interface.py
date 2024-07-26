
import os
import time
from mininet.cli import CLI
from datetime import datetime, timezone


#ECMP will is only configured on ONOS to have rules for Flow labels between 0-4, if not, the packet will not be routed
num_iterations = 10
iteration_duration_seconds = 5 * 60  #5 minutes, the duration of each iteration of the test

export_file_LOW = "LOW_raw_results.csv"

def create_lock_file(lock_filename):
    lock_file_path = os.path.join("/INT/results", lock_filename)

    # Create the lock file if it does not exist
    if not os.path.exists(lock_file_path):
        with open(lock_file_path, 'w') as lock_file:
            lock_file.write('') # Write an empty string to the file

def print_menu():
    menu = """
    ONOS CLI Command Menu:
    0. Stop Mininet
    1. Mininet CLI
    2. Make all Hosts be detetced by ONOS (needed for packet forwarding)
    3. Low Load Test (Just for debugging, not the final test)
    """
    print(menu)

def detect_all_hosts(net):
    #Iteratte all hosts, so their source switch send their info to ONOS, on how to forward to them
    print("Make all Hosts be detetced by ONOS")
    dst_host_ip = '2001:1:1::1'                                          #use as dst IP any host in the network
    for host in net.hosts:
        print(f"Detecting host {host.name}")
        command = f"nohup ping -c 3 -i 0.001 -w 0.001 "+dst_host_ip+" &"      #use as dst IP any host in the network
        host.cmd(command)

def send_packet_script(me, dst_ip, l4, port, flow_label, msg, dscp, size, count, interval, export_file, iteration):
    #Best use interval values >=0.1, lower values sleep becomes inaccurate
    
    command = f"python3 /INT/send/send.py --ip {dst_ip} --l4 {l4} --port {port} --flow_label {flow_label} --m {msg} --dscp {dscp} --s {size} --c {count} --i {interval}"
    
    if export_file != None:
        command = command + f" --export {export_file} --me {me.name} --iteration {iteration}"

    command = command + f" > /INT/results/logs/send-{iteration}.log"
    command = command + " &"
    #print(f"{me.name} running Command: {command}")
    
    me.cmd(command)

def receive_packet_script(me, export_file, iteration, duration):
    command = f"python3 /INT/receive/receive.py"

    if export_file != None:
        command = command + f" --export {export_file} --me {me.name} --iteration {iteration} --duration {duration}"

    command = command + f" > /INT/results/logs/receive-{iteration}.log"
    command = command + " &"
    #print(f"{me.name} running Command: {command}")

    me.cmd(command)

def low_load_test(net):
    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print("Low Load Test, started at:", rfc3339_time)

    file_results = export_file_LOW
    lock_filename = f"LOCK_{file_results}"

    #create logs directory
    os.makedirs("/INT/results/logs", exist_ok=True)
    create_lock_file(lock_filename)

    h1_1 = net.get("h1_1")
    h2_1 = net.get("h2_1")    
    
    num_iterations_LOW = 4        #for tests 4 is enough
    i = 0.001                     #seconds, lower values that 0.1, scapy sending/receiveing the pkt + sleep between emissions becomes inaccurate

    iteration_duration_seconds_LOW = 5 * 60                         #the duration of each iteration of the test
    num_packets = round(iteration_duration_seconds_LOW / (i + 0.1)) #distribute the packets over the duration of the test (over i intervals) and consider that each packet takes 0.1 sec to send/process at receive
    
    receiver_timeout = num_packets * 0.1 * 1.01         #each packet takes < 0.1 seconds to send/process at receive (may vary on the system), added small margin to ensure the receiver script receives all packets, before stopping
    iteration_sleep= receiver_timeout * 1.01            #with margin to ensure all receivers script have written the results with the concorrent writes prevention

    print(f"num_iterations_LOW: {num_iterations_LOW}")
    print(f"iteration_duration_seconds_LOW: {iteration_duration_seconds_LOW}")
    print(f"receiver_timeout: {receiver_timeout}")
    print(f"iteration_sleep: {iteration_sleep}")
    print(f"Number of packets: {num_packets}")
    for iteration in range(1, num_iterations_LOW + 1):
        print(f"--------------Starting iteration {iteration} of {num_iterations_LOW}")
        
        #-------------Start the send script on the source hosts (1º longer setup time)
        send_packet_script(h1_1, "2001:1:2::1", "udp", 443, 1, "INTH1", 0, 262, num_packets, i, file_results, iteration)

        #-------------Start the receive script on the destination hosts
        receive_packet_script(h2_1, file_results, iteration, receiver_timeout)

        print(f"Waiting for {iteration_sleep} seconds")
        #-------------Keep the test running for a specified duration
        time.sleep(iteration_sleep)  

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print("Low Load Test finished at:", rfc3339_time)


def main_menu(net, choice):

    if   choice == 0:
        print("Stopping Mininet")
        net.stop()
        return False
    elif choice == 1:
        print("To leave the Mininet CLI, type 'exit' or 'quit'")
        CLI(net)
    elif choice == 2:
        detect_all_hosts(net)
    elif choice == 3:
        low_load_test(net)                  #FOR LAST ONE, REMENBER TO CLEAN SRV6 BETWEEN ITERATIONS
    else:
        print("Invalid choice")
    
    return True