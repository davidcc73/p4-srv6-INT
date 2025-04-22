
import os
import time
import constants
from mininet.cli import CLI
from datetime import datetime, timezone

ORANGE = '\033[38;5;214m'
RED = '\033[31m'
BLUE = '\033[34m'
CYAN = '\033[36m'
GREEN = '\033[32m'
MAGENTA = '\033[35m'
PINK = '\033[38;5;205m'
END = "\033[0m"

export_file_LOW = "LOW"
export_file_MEDIUM = "MEDIUM"
export_file_HIGH = "HIGH"
export_file_HIGH_EMERGENCY = "HIGH+EMERGENCY"

host_IPs  = constants.host_IPs
intervals = {"Message": 0.1, "Audio": 0.01, "Video": 0.02, "Emergency": 0.02}         #seconds, used to update the other dictionaries below 0.02 is unstable
sizes     = {"Message": 262, "Audio": 420,  "Video": 1250, "Emergency": 100}          #bytes coincide with the INT/receive/packet size.json

packet_number    = {"Message": 0, "Audio": 0, "Video": 0, "Emergency": 0}            #placeholder values, updated in update_times()
receiver_timeout = 0                                                                 #placeholder values, updated in update_times(), time receiver will wait for pkts
iteration_sleep  = 0                                                                 #placeholder values, updated in update_times(), time between iterations

num_iterations = 10
iteration_duration_seconds = 5 * 60  #5 minutes, the duration of each iteration of the test

sender_receiver_gap = 5              #seconds to wait for the receiver to start before starting the sender
export_results_gap = 5               #seconds to wait for the senders/receivers to finish before exporting the results

def update_times():
    global iteration_duration_seconds, intervals, packet_number, receiver_timeout, iteration_sleep

    #update the number of packets to be sent in each flow type
    packet_number["Message"]   = round(iteration_duration_seconds / intervals["Message"]  )
    packet_number["Audio"]     = round(iteration_duration_seconds / intervals["Audio"]    )
    packet_number["Video"]     = round(iteration_duration_seconds / intervals["Video"]    )
    packet_number["Emergency"] = round(iteration_duration_seconds / intervals["Emergency"])

    #Give time to the receiver to receive all packets
    receiver_timeout = iteration_duration_seconds * 1.05 + sender_receiver_gap

    #Give time to exporting the results
    iteration_sleep  = receiver_timeout * 1.05 + export_results_gap

def create_lock_file(lock_filename):
    lock_file_path = os.path.join("/INT/results", lock_filename)

    # Create the lock file if it does not exist
    if not os.path.exists(lock_file_path):
        with open(lock_file_path, 'w') as lock_file:
            lock_file.write('') # Write an empty string to the file

def send_packet_script(me, dst_ip, l4, flow_label, dport,  msg, dscp, size, count, interval, export_file, iteration):
    global iteration_duration_seconds
    
    command = f"python3 /mininet/tools/send.py --dst_ip {dst_ip} --port {dport} --dscp {dscp} --l4 {l4} --flow_label {flow_label} --m {msg} --s {size} --c {count} --i {interval} --time_out {iteration_duration_seconds} "
    
    if export_file != None:
        command = command + f" --export {export_file} --me {me.name} --iteration {iteration}"

    command = command + f" >> /INT/results/logs/send-{iteration}-{me.name}.log"
    command = command + " &"
    #print(f"{me.name} running Command: {command}")
    
    me.cmd(command)

def receive_packet_script(me, export_file, iteration, duration):
    command = f"python3 /mininet/tools/receive.py"

    if export_file != None:
        command = command + f" --export {export_file} --me {me.name} --iteration {iteration} --duration {duration}"

    command = command + f" >> /INT/results/logs/receive-{iteration}-{me.name}.log"
    command = command + " &"
    #print(f"{me.name} running Command: {command}")

    me.cmd(command)

def create_Messages_flow(src_host, dst_IP, flow_label, dport, dscp, file_results, iteration):
    global intervals, packet_number, sizes, host_IPs
    l4 = "udp"
    msg = "INTH1"
    i = intervals["Message"]
    size = sizes["Message"]                #Total byte size of the packet

    num_packets = packet_number["Message"]

    #-------------Start the send script on the source hosts (1º because it has a longer setup time)
    send_packet_script(me = src_host, dst_ip = dst_IP, l4 = l4, 
                        flow_label = flow_label, dport = dport, msg = msg, 
                        dscp = dscp, size = size, count = num_packets, 
                        interval = i, export_file = file_results, iteration = iteration)

def create_Audio_flow(src_host, dst_IP, flow_label, dport, dscp, file_results, iteration):
    global intervals, packet_number, sizes, host_IPs
    l4 = "udp"
    msg = "INTH1"
    i = intervals["Audio"]
    size = sizes["Audio"]                #Total byte size of the packet

    num_packets = packet_number["Audio"]

    #-------------Start the send script on the source hosts (1º because it has a longer setup time)
    send_packet_script(me = src_host, dst_ip = dst_IP, l4 = l4, 
                        flow_label = flow_label, dport = dport, msg = msg, 
                        dscp = dscp, size = size, count = num_packets, 
                        interval = i, export_file = file_results, iteration = iteration)

def create_Video_flow(src_host, dst_IP, flow_label, dport, dscp, file_results, iteration):
    global intervals, packet_number, sizes, host_IPs
    l4 = "udp"
    msg = "INTH1"
    i = intervals["Video"]
    size = sizes["Video"]                #Total byte size of the packet

    num_packets = packet_number["Video"]    

    #-------------Start the send script on the source hosts (1º because it has a longer setup time)
    send_packet_script(me = src_host, dst_ip = dst_IP, l4 = l4, 
                        flow_label = flow_label, dport = dport, msg = msg, 
                        dscp = dscp, size = size, count = num_packets, 
                        interval = i, export_file = file_results, iteration = iteration)

def create_Emergency_flow(src_host, dst_IP, flow_label, dport, dscp, file_results, iteration):
    global intervals, packet_number, sizes, host_IPs
    l4 = "udp"
    msg = "INTH1"
    i = intervals["Emergency"]
    size = sizes["Emergency"]                #Total byte size of the packet

    num_packets = packet_number["Emergency"]

    #-------------Start the send script on the source hosts (1º because it has a longer setup time)
    send_packet_script(me = src_host, dst_ip = dst_IP, l4 = l4, 
                        flow_label = flow_label, dport = dport, msg = msg, 
                        dscp = dscp, size = size, count = num_packets, 
                        interval = i, export_file = file_results, iteration = iteration)


def low_load_test(net, routing):
    global export_file_LOW, host_IPs, iteration_sleep, receiver_timeout

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(GREEN + "Low Load Test, started at:" + str(rfc3339_time) + END)

    file_results = export_file_LOW + "-" + routing + "_raw_results.csv"
    lock_filename = f"LOCK_{file_results}"

    #create logs directory
    os.makedirs("/INT/results/logs", exist_ok=True)
    create_lock_file(lock_filename)

    h1_1 = net.get("h1_1")
    h3_1 = net.get("h3_1")    
    
    num_iterations_LOW = 3        #for tests 3 is enough

    if routing == "ECMP-SRv6":
        SRv6_used(iteration_sleep, num_iterations_LOW)

    for iteration in range(1, num_iterations_LOW + 1):
        print(f"--------------Starting iteration {iteration} of {num_iterations_LOW}")
        #-------------Start the send script on the source hosts (1º longer setup time)
        receive_packet_script(h3_1, file_results, iteration, receiver_timeout)

        time.sleep(sender_receiver_gap) 

        #--------------Start Message flows
        create_Messages_flow(h1_1, "2001:1:3::1", 1, 443, 0,  file_results, iteration)   #DSCP 0  

        #-------------Keep the test running for a specified duration
        print(f"Waiting for {iteration_sleep} seconds")
        time.sleep(iteration_sleep)  

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(CYAN + "Low Load Test finished at:" + str(rfc3339_time) + END)

def medium_load_test(net, routing):
    global export_file_MEDIUM, host_IPs, iteration_sleep, receiver_timeout
    dport = 443

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(GREEN + "Medium Load Test, started at:" + str(rfc3339_time) + END)

    file_results = export_file_MEDIUM + "-" + routing + "_raw_results.csv"
    lock_filename = f"LOCK_{file_results}"

    #create logs directory
    os.makedirs("/INT/results/logs", exist_ok=True)
    create_lock_file(lock_filename)

    #delete old .csv file if it exists
    if os.path.exists(f"/INT/results/{file_results}"):
        print(f"Deleting the old results file: {file_results}")
        os.remove(f"/INT/results/{file_results}")

    # Get the hosts
    h1_1 = net.get("h1_1") 
    h1_2 = net.get("h1_2") 
    h2_1 = net.get("h2_1")
    h2_2 = net.get("h2_2")
    h3_1 = net.get("h3_1")
    h5_1 = net.get("h5_1")
    h7_1 = net.get("h7_1")
    h7_2 = net.get("h7_2")
    h7_3 = net.get("h7_3")
    h8_1 = net.get("h8_1")
    h8_2 = net.get("h8_2")
    h8_3 = net.get("h8_3")
    
    # Get the hosts IPs
    h1_1_IP_and_maks = host_IPs[h1_1.name]
    h3_1_IP_and_maks = host_IPs[h3_1.name]
    h5_1_IP_and_maks = host_IPs[h5_1.name]
    h7_1_IP_and_maks = host_IPs[h7_1.name]
    h7_2_IP_and_maks = host_IPs[h7_2.name]
    h7_3_IP_and_maks = host_IPs[h7_3.name]
    h8_2_IP_and_maks = host_IPs[h8_2.name]
    h8_3_IP_and_maks = host_IPs[h8_3.name]
    
    h1_1_dst_IP = h1_1_IP_and_maks.split("/")[0]
    h3_1_dst_IP = h3_1_IP_and_maks.split("/")[0]
    h5_1_dst_IP = h5_1_IP_and_maks.split("/")[0]
    h7_1_dst_IP = h7_1_IP_and_maks.split("/")[0]
    h7_2_dst_IP = h7_2_IP_and_maks.split("/")[0]
    h7_3_dst_IP = h7_3_IP_and_maks.split("/")[0]
    h8_2_dst_IP = h8_2_IP_and_maks.split("/")[0]
    h8_3_dst_IP = h8_3_IP_and_maks.split("/")[0]

    if routing == "ECMP-SRv6":
        SRv6_used(iteration_sleep, num_iterations)
    
    for iteration in range(1, num_iterations + 1):
        print(f"--------------Starting iteration {iteration} of {num_iterations}")

        #-------------Start the receive script on the destination hosts
        receive_packet_script(h1_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h3_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_3, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_3, file_results, iteration, receiver_timeout)

        #---------------------------------------------Senders
        time.sleep(sender_receiver_gap) 
        
        #--------------Start Message flows
        create_Messages_flow (h8_1, h1_1_dst_IP, 1, dport, 0,  file_results, iteration)   #DSCP 0   
        create_Messages_flow (h2_1, h3_1_dst_IP, 1, dport, 0,  file_results, iteration)   #DSCP 0        

        #--------------Start Audio flows
        create_Audio_flow    (h1_2, h7_1_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34
        create_Audio_flow    (h5_1, h7_2_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34

        #--------------Start Video flows
        create_Video_flow    (h2_2, h8_2_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h3_1, h8_3_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h3_1, h7_3_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35

        #-------------Keep the test running for a specified duration
        print(f"Waiting for {iteration_sleep} seconds")
        time.sleep(iteration_sleep)  

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(CYAN + "Medium Load Test finished at:" + str(rfc3339_time) + END)

def high_load_test(net, routing):
    global export_file_HIGH, host_IPs, iteration_sleep, receiver_timeout
    dport = 443

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(GREEN + "High Load Test, started at:" + str(rfc3339_time) + END)

    file_results = export_file_HIGH + "-" + routing + "_raw_results.csv"
    lock_filename = f"LOCK_{file_results}"

    #create logs directory
    os.makedirs("/INT/results/logs", exist_ok=True)
    create_lock_file(lock_filename)

    #delete old .csv file if it exists
    if os.path.exists(f"/INT/results/{file_results}"):
        print(f"Deleting the old results file: {file_results}")
        os.remove(f"/INT/results/{file_results}")

    # Get the hosts
    h1_1 = net.get("h1_1")
    h1_2 = net.get("h1_2")
    h2_1 = net.get("h2_1")
    h2_2 = net.get("h2_2")
    h3_1 = net.get("h3_1")
    h5_1 = net.get("h5_1")
    h7_1 = net.get("h7_1")
    h7_2 = net.get("h7_2")
    h7_3 = net.get("h7_3")
    h8_1 = net.get("h8_1")
    h8_2 = net.get("h8_2")
    h8_3 = net.get("h8_3")
    h8_4 = net.get("h8_4")

    
    # Get the hosts IPs
    h1_1_IP_and_maks = host_IPs[h1_1.name]
    h1_2_IP_and_maks = host_IPs[h1_2.name]
    h2_1_IP_and_maks = host_IPs[h2_1.name]
    h2_2_IP_and_maks = host_IPs[h2_2.name]
    h3_1_IP_and_maks = host_IPs[h3_1.name]
    h5_1_IP_and_maks = host_IPs[h5_1.name]
    h7_1_IP_and_maks = host_IPs[h7_1.name]
    h7_2_IP_and_maks = host_IPs[h7_2.name]
    h7_3_IP_and_maks = host_IPs[h7_3.name]
    h8_1_IP_and_maks = host_IPs[h8_1.name]
    h8_2_IP_and_maks = host_IPs[h8_2.name]
    h8_3_IP_and_maks = host_IPs[h8_3.name]
    h8_4_IP_and_maks = host_IPs[h8_4.name]
    
    h1_1_dst_IP = h1_1_IP_and_maks.split("/")[0]
    h1_2_dst_IP = h1_2_IP_and_maks.split("/")[0]
    h2_1_dst_IP = h2_1_IP_and_maks.split("/")[0]
    h2_2_dst_IP = h2_2_IP_and_maks.split("/")[0]
    h3_1_dst_IP = h3_1_IP_and_maks.split("/")[0]
    h5_1_dst_IP = h5_1_IP_and_maks.split("/")[0]
    h7_1_dst_IP = h7_1_IP_and_maks.split("/")[0]
    h7_2_dst_IP = h7_2_IP_and_maks.split("/")[0]
    h7_3_dst_IP = h7_3_IP_and_maks.split("/")[0]
    h8_1_dst_IP = h8_1_IP_and_maks.split("/")[0]
    h8_2_dst_IP = h8_2_IP_and_maks.split("/")[0]
    h8_3_dst_IP = h8_3_IP_and_maks.split("/")[0]
    h8_4_dst_IP = h8_4_IP_and_maks.split("/")[0]

    if routing == "ECMP-SRv6":
        SRv6_used(iteration_sleep, num_iterations)
    
    for iteration in range(1, num_iterations + 1):
        print(f"--------------Starting iteration {iteration} of {num_iterations}")

        #-------------Start the receive script on the destination hosts
        receive_packet_script(h1_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h2_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h2_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h3_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h5_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_3, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_3, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_4, file_results, iteration, receiver_timeout)

        #---------------------------------------------Senders
        time.sleep(sender_receiver_gap) 
        
        #--------------Start Message flows
        create_Messages_flow (h8_1, h1_1_dst_IP, 1, dport, 0,  file_results, iteration)   #DSCP 0   
        create_Messages_flow (h2_1, h3_1_dst_IP, 1, dport, 0,  file_results, iteration)   #DSCP 0        

        #--------------Start Audio flows
        create_Audio_flow    (h1_2, h7_1_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34
        create_Audio_flow    (h5_1, h7_2_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34
        create_Audio_flow    (h3_1, h5_1_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34
        create_Audio_flow    (h8_1, h2_1_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34

        #--------------Start Video flows
        create_Video_flow    (h2_2, h8_2_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h3_1, h8_3_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h3_1, h7_3_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h2_1, h8_1_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h7_3, h8_4_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h5_1, h2_2_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35

        #-------------Keep the test running for a specified duration
        print(f"Waiting for {iteration_sleep} seconds")
        time.sleep(iteration_sleep)  

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(CYAN + "High Load Test finished at:" + str(rfc3339_time) + END)

def high_emergency_load_test(net, routing):    
    global export_file_HIGH_EMERGENCY, host_IPs, iteration_sleep, receiver_timeout
    dport = 443
    
    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(GREEN + "High with Emergency Load Test, started at:" + str(rfc3339_time) + END)

    file_results = export_file_HIGH_EMERGENCY + "-" + routing + "_raw_results.csv"
    lock_filename = f"LOCK_{file_results}"

    #create logs directory
    os.makedirs("/INT/results/logs", exist_ok=True)
    create_lock_file(lock_filename)

    #delete old .csv file if it exists
    if os.path.exists(f"/INT/results/{file_results}"):
        print(f"Deleting the old results file: {file_results}")
        os.remove(f"/INT/results/{file_results}")

    # Get the hosts
    h1_1 = net.get("h1_1")
    h1_2 = net.get("h1_2")
    h2_1 = net.get("h2_1")
    h2_2 = net.get("h2_2")
    h3_1 = net.get("h3_1")
    h5_1 = net.get("h5_1")
    h7_1 = net.get("h7_1")
    h7_2 = net.get("h7_2")
    h7_3 = net.get("h7_3")
    h8_1 = net.get("h8_1")
    h8_2 = net.get("h8_2")
    h8_3 = net.get("h8_3")
    h8_4 = net.get("h8_4")

    
    # Get the hosts IPs
    h1_1_IP_and_maks = host_IPs[h1_1.name]
    h1_2_IP_and_maks = host_IPs[h1_2.name]
    h2_1_IP_and_maks = host_IPs[h2_1.name]
    h2_2_IP_and_maks = host_IPs[h2_2.name]
    h3_1_IP_and_maks = host_IPs[h3_1.name]
    h5_1_IP_and_maks = host_IPs[h5_1.name]
    h7_1_IP_and_maks = host_IPs[h7_1.name]
    h7_2_IP_and_maks = host_IPs[h7_2.name]
    h7_3_IP_and_maks = host_IPs[h7_3.name]
    h8_1_IP_and_maks = host_IPs[h8_1.name]
    h8_2_IP_and_maks = host_IPs[h8_2.name]
    h8_3_IP_and_maks = host_IPs[h8_3.name]
    h8_4_IP_and_maks = host_IPs[h8_4.name]
    
    h1_1_dst_IP = h1_1_IP_and_maks.split("/")[0]
    h1_2_dst_IP = h1_2_IP_and_maks.split("/")[0]
    h2_1_dst_IP = h2_1_IP_and_maks.split("/")[0]
    h2_2_dst_IP = h2_2_IP_and_maks.split("/")[0]
    h3_1_dst_IP = h3_1_IP_and_maks.split("/")[0]
    h5_1_dst_IP = h5_1_IP_and_maks.split("/")[0]
    h7_1_dst_IP = h7_1_IP_and_maks.split("/")[0]
    h7_2_dst_IP = h7_2_IP_and_maks.split("/")[0]
    h7_3_dst_IP = h7_3_IP_and_maks.split("/")[0]
    h8_1_dst_IP = h8_1_IP_and_maks.split("/")[0]
    h8_2_dst_IP = h8_2_IP_and_maks.split("/")[0]
    h8_3_dst_IP = h8_3_IP_and_maks.split("/")[0]
    h8_4_dst_IP = h8_4_IP_and_maks.split("/")[0]

    if routing == "ECMP-SRv6":
        SRv6_used(iteration_sleep, num_iterations)
    
    for iteration in range(1, num_iterations + 1):
        print(f"--------------Starting iteration {iteration} of {num_iterations}")

        #-------------Start the receive script on the destination hosts
        receive_packet_script(h1_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h1_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h2_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h2_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h3_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h5_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h7_3, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_1, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_2, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_3, file_results, iteration, receiver_timeout)
        receive_packet_script(h8_4, file_results, iteration, receiver_timeout)

        #---------------------------------------------Senders
        time.sleep(sender_receiver_gap) 
        
        #--------------Start Message flows
        create_Messages_flow (h8_1, h1_1_dst_IP, 1, dport, 0,  file_results, iteration)   #DSCP 0   
        create_Messages_flow (h2_1, h3_1_dst_IP, 1, dport, 0,  file_results, iteration)   #DSCP 0        

        #--------------Start Audio flows
        create_Audio_flow    (h1_2, h7_1_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34
        create_Audio_flow    (h5_1, h7_2_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34
        create_Audio_flow    (h3_1, h5_1_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34
        create_Audio_flow    (h8_1, h2_1_dst_IP, 1, dport, 34, file_results, iteration)   #DSCP 34

        #--------------Start Video flows
        create_Video_flow    (h2_2, h8_2_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h3_1, h8_3_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h3_1, h7_3_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h2_1, h8_1_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h7_3, h8_4_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35
        create_Video_flow    (h5_1, h2_2_dst_IP, 1, dport, 35,  file_results, iteration)  #DSCP 35

        #--------------Start Emergency flows
        create_Emergency_flow(h8_4, h1_2_dst_IP, 1, dport, 46, file_results, iteration)   #DSCP 46

        #-------------Keep the test running for a specified duration
        print(f"Waiting for {iteration_sleep} seconds")
        time.sleep(iteration_sleep)  

    # Get the current time in FORMAT RFC3339
    rfc3339_time = datetime.now(timezone.utc).isoformat()
    print("---------------------------")
    print(CYAN + "High with Emergency Load Test finished at:" + str(rfc3339_time) + END)


def SRv6_used(iteration_sleep, num_iter):
    print(f"ATTENTION: Running a test with SRv6, this requires that INT analyzer is also run at the same time")
    print(f"ATTENTION: The iteration sleep time for this test will be: {iteration_sleep} pass it to the INT analyzer script as argument")
    print(f"ATTENTION: The number of iterations for this test will be: {num_iter} pass it to the INT analyzer script as argument")
    print(f"ATTENTION: Press Enter to start the test, start the analyzer at the same time, they need to be in sync")
    input()

def print_menu():
    menu = """
    ONOS CLI Command Menu:
    ATTENTION: For clean results, delete the contents of the /INT/results directory before starting the tests
    0. Stop Mininet
    1. Mininet CLI
    2. Make all Hosts be detetced by ONOS (needed for packet forwarding)
    3. Low Load Test (Just for debugging, not the final test)
    4. Medium Load Test
    5. High Load Test
    6. High Load Test with Emergency Flow
    7. High -> High with Emergency Flow Tests in Sequence
    8. Medium -> High -> High with Emergency Flow Tests in Sequence
    """
    print(menu)

def print_routing_menu():
    menu = """
    Routing Method Menu:
    0. Cancel
    1. KShort
    2. ECMP
    3. ECMP + SRv6 (Do not use when selecting do the tests in sequence, analyzer may need different sleep times for each test)
    """

    while True:
        print(menu)
        try:
            print("Which Routing method is going to be used?")
            choice = int(input("Enter the number of your choice:"))
            if choice < 0 or choice > 3:
                print("Invalid choice. Please enter a valid number")
                continue

            return choice
        except ValueError:
            print("Invalid input. Please enter a number.")
            continue

def detect_all_hosts(net):
    #Iteratte all hosts, so their source switch send their info to ONOS, on how to forward to them
    print("Make all Hosts be detetced by ONOS")
    dst_host_ip = '2001:1:1::fe'                                          #use as dst IP any host in the network that does not belong to any host
    for host in net.hosts:
        print(f"Detecting host {host.name}")
        command = f"nohup ping -c 3 -i 0.001 -w 0.001 "+dst_host_ip+" &"      #use as dst IP any host in the network
        host.cmd(command)

def main_menu(net, choice):
    routing = None
    
    update_times()

    # Which routing method is going to be used?
    if choice == 3 or choice == 4 or choice == 5 or choice == 6 or choice == 7 or choice == 8:
        choise2 = print_routing_menu()
        if choise2 == 0:
            return True
        elif choise2 == 1:
            routing = "KShort"
        elif choise2 == 2:
            routing = "ECMP"
        elif choise2 == 3:
            routing = "ECMP-SRv6"


    # What will be done
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
        low_load_test(net, routing)                  #FOR LAST ONE, REMENBER TO CLEAN SRV6 BETWEEN TEST CASES
    elif choice == 4:
        medium_load_test(net, routing)
    elif choice == 5:
        high_load_test(net, routing)
    elif choice == 6:
        high_emergency_load_test(net, routing)
    elif choice == 7:
        high_load_test(net, routing)
        print(ORANGE + "Waiting for 10 seconds between tests scenarios" + END)
        time.sleep(15)
        
        high_emergency_load_test(net, routing)
    elif choice == 8:
        medium_load_test(net, routing)
        print(ORANGE + "Waiting for 10 seconds between tests scenarios" + END)
        time.sleep(15)

        high_load_test(net, routing)
        print(ORANGE + "Waiting for 10 seconds between tests scenarios" + END)
        time.sleep(15)
        
        high_emergency_load_test(net, routing)
    else:
        print("Invalid choice")
    
    return True