import argparse
import os
import re
import sys
from time import sleep
from influxdb import InfluxDBClient
from datetime import datetime, timedelta, timezone
import numpy as np
import time
import paramiko
import ipaddress

ORANGE = '\033[38;5;214m'
RED = '\033[31m'
BLUE = '\033[34m'
CYAN = '\033[36m'
GREEN = '\033[32m'
MAGENTA = '\033[35m'
PINK = '\033[38;5;205m'
END = "\033[0m"

# Define DB connection parameters
host='localhost'
dbname='int'

args = None
minutes_ago_str = None                                      #string with the time of the last minute to analyze
current_iteration = None
doNotLog = False

sleep_time_seconds = 15
analisy_window_minutes = 0.25
static_infra_switches = [9, 10, 11, 12, 13, 14]              #lsit of the switch's id that belong to the static infrastructure

thresholds_overloaded    = 0.75                              #percentage (including) threshold to consider a switch as overloaded
thresholds_no_overloaded = 0.60                              #percentage (including) threshold to NO LONGER consider a switch as overloaded

network_MTU = 1500                                           #Maximum Transmission Unit (MTU) of the network (bytes)

# Contains the normalization limits for each data type
normalization_limits = {}

# Define weights for each variable, THE SUM MUST BE 1
weights = {
    'is_infra_switch': 0.30,           # Weight for switch type
    'num_packets': 0.40,               # Weight for number of packets
    'avg_packet_procesing_time': 0.20, # Weight for average packet processing time
    'avg_packet_size': 0.10            # Weight for average packet size
}

# To store the currenty in usage SRv6 rules, key(switch that was overloaded) values: list dictionaries with the SRv6 args (strings)
active_SRv6_rules = {}

current_directory = os.path.dirname(os.path.realpath(__file__))

def parse_args():
    global args

    parser = argparse.ArgumentParser(description='analyzer parser')
    parser.add_argument('--routing', help='Which routing method is the topology using, will be used to kown which file the results will be stored',
                        type=str, action="store", required=False, default=None)
    parser.add_argument('--num_iterations', help='num of iterations being tested on current execution',
                        type=int, action="store", required=True, default=None)
    parser.add_argument('--iterations_timer', help='Time in seconds for each iteration, 0 mens infinite',
                        type=float, action="store", required=True, default=None)


    args = parser.parse_args()


    if args.num_iterations <= 0:
        print("Invalid number of iterations, must be a positive integer")
        sys.exit(1)
    if args.iterations_timer < 0:
        print("Invalid timer for iterations, must be a zero or positive integer")
        sys.exit(1)
    if args.num_iterations != 1 and args.iterations_timer == 0:
        print("Impossible to have more than one iteration without a timer")
        sys.exit(1)

def delete_old_log():
    if args.routing is None:
        return

    log_file = args.routing + "-SRv6 rules.log"

    # Delete the log file if it already exists
    if os.path.exists(os.path.join(current_directory, log_file)):
        os.remove(os.path.join(current_directory, log_file))

def write_log(message):
    if args.routing is None or doNotLog is True:
        return
    
    log_file = args.routing + "-SRv6 rules.log"

    #Open file and append the message to the log file at the same directory of the script is on
    with open(os.path.join(current_directory, log_file), 'a') as file:
        file.write(f"{current_iteration} - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} - {message}\n")

def remove_all_active_SRv6_rules(session):
    global active_SRv6_rules, doNotLog
    switch_marked_to_remove = []
    doNotLog = True
    print(PINK + "Removing all active SRv6 rules" + END)

    for switch_id, SRv6_rules in active_SRv6_rules.items():
        #Remove all rules from the current switch
        remove_switch_SRv6_rules(session, switch_id, SRv6_rules, switch_marked_to_remove)
        print(PINK + "Removed active SRv6 rules from switch:" + str(switch_id) + END)

    #iterate the list of switches that had all of their rules removed and remove them from active_SRv6_rules
    for switch_id in switch_marked_to_remove:
        del active_SRv6_rules[switch_id]
    
    doNotLog = False

# Function to strip ANSI escape sequences from a string
def strip_ansi_escape_sequences(string):    
    ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])')
    string = ansi_escape.sub('', string)

    #If \x1b> is still present, remove it
    string = string.replace('\x1b>', '')

    return string

def connect_to_onos():
    # Define connection parameters
    hostname = 'localhost'
    port = 8101
    username = 'onos'
    password = 'rocks' 

    # Create an SSH client
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    try:
        # Connect to the ONOS CLI
        client.connect(
            hostname=hostname,
            port=port,
            username=username,
            password=password,
            look_for_keys=False,
            allow_agent=False
        )
        
        # Open a session
        session = client.invoke_shell()
        
        # Allow some time for the session to be ready
        time.sleep(1)
        
        # Check if the channel is active
        if session.recv_ready():
            print(session.recv(1024).decode('utf-8'))
        
        return session

    except Exception as e:
        print(f"Failed to connect: {e}")
        client.close()
        return None

def send_command(session, command):
    if session:
        session.send(command + '\n')
        time.sleep(1)  # Wait for the command to be executed

        output = ""
        while True:
            if session.recv_ready():
                output_chunk = session.recv(1024).decode('utf-8')
                output += output_chunk
            else:
                break
        
        return output
    else:
        return "Session not established"

def compare_ipv6_segment(ipv6_address, segment_index, comparison_value):
    # Receive an IPv6 address in its compressed form, compares a specific segment to a given value,
    # the segment is converted to an integer before the comparison, the comparation is done in decimal
    # returns True if the segment is equal to the given value, False otherwise

    # Parse the IPv6 address
    parsed_ip = ipaddress.IPv6Address(ipv6_address)

    # Convert the IPv6 address to its exploded form (fully expanded)
    exploded_ip = parsed_ip.exploded

    # Split the exploded IPv6 address into its hexadecimal components
    segments = exploded_ip.split(':')

    # Extract the specific segment to compare
    segment_to_compare = int(segments[segment_index], 16)

    # Compare the extracted segment to the given value
    return segment_to_compare == comparison_value

def get_current_path(flow):
    # Get the current path of the flow, arguments: (src_ip, dst_ip, flow_label)
    # Returns a string with the current path of the flow, separated by -
    # Example: "1-2-3-4"

    # Get the flow arguments
    src_ip = flow[0]
    dst_ip = flow[1]
    flow_label = flow[2]

    # Query the DB to get the current path of the flow
    query = f"""
        SELECT "path" 
        FROM flow_stats
        WHERE "src_ip" = '{src_ip}' 
        AND "dst_ip" = '{dst_ip}' 
        AND "flow_label" = '{flow_label}'
        ORDER BY time DESC
        LIMIT 1
        """
    
    result = apply_query(query)

    # Many checks done already, so I can assume that there is data to analyze
    #print(result)
    path = result.raw['series'][0]['values'][0][1]
    #print(f"Current path of the flow {src_ip} -> {dst_ip} (Flow label: {flow_label}): {path}")
    return path

def store_SRv6_rule(switch_id, values):
    #check if there is already a SRv6 rule for the same flow and delete it before adding new on the new key
    stop = False
    for key, SRv6_rules in active_SRv6_rules.items():
        for SRv6_rule in SRv6_rules:
            #if the flow is the same, remove it
            if SRv6_rule['srcIP'] == values['srcIP'] and SRv6_rule['dstIP'] == values['dstIP'] and SRv6_rule['flow_label'] == values['flow_label']:
                SRv6_rules.remove(SRv6_rule)
                stop = True
                break
        if stop:
            break
    
    #add the new rule to switch_id
    if switch_id not in active_SRv6_rules:
        active_SRv6_rules[switch_id] = [values]
    else:
        active_SRv6_rules[switch_id].append(values)
    
    write_log(f"Created SRv6 rule => {switch_id}: {values}")

def remove_switch_SRv6_rules(session, switch_id, SRv6_rules, switch_marked_to_remove):
    
    #--------Iterate trough all of it's SRv6_args and remove each rule from ONOS
    for SRv6_rule in SRv6_rules:
        print(f"Trying to remove rule: {SRv6_rule}")
        devideID = SRv6_rule['deviceID']                  #device that injects the SRv6 in the packet
        srcIP = SRv6_rule['srcIP']                        #source IP of the flow
        dstIP = SRv6_rule['dstIP']                        #destination IP of the flow
        flow_label = SRv6_rule['flow_label']              #flow label of the flow
        src_mask = SRv6_rule['src_mask']                  #source mask of the flow
        dst_mask = SRv6_rule['dst_mask']                  #destination mask of the flow
        flow_label_mask = SRv6_rule['flow_label_mask']    #flow label mask of the flow

        #This command does not return anything if successful (or if there is no rule with said args)
        args = (devideID, srcIP, dstIP, flow_label, src_mask, dst_mask, flow_label_mask)
        command = 'srv6-remove device:r%s %s %s %s %s %s %s' % args

        #remove rule from ONOS
        output = send_command(session, command)
        print(output)
        write_log(f"Removed SRv6 rule => {switch_id}: {SRv6_rule}")

    #all rules created by this switch removed from ONOS, mark to remove after iterating active_SRv6_rules
    switch_marked_to_remove.append(switch_id)

    return switch_marked_to_remove

def request_SRv6_detour(session, wrost, current_path, bad_switch_loads):
    parsed_current_path = current_path.split('-')
    code = 0
    srcID = parsed_current_path[0]
    dstID = parsed_current_path[-1]

    uriSrc = "device:r" + str(srcID)
    uriDst = "device:r" + str(dstID)

    # Remove first node from the path (ONOS command does not need it)
    parsed_current_path.pop(0)
    path_updated = '-'.join(parsed_current_path)
    #print(path_updated)

    # Get the current path of the flow, arguments: (src_ip, dst_ip, flow_label)
    src_ip = wrost[0]
    dst_ip = wrost[1]
    flow_label = wrost[2]

    #From switch_loads get the switch_id and the load value, into their own strings
    bad_switch_ids_to_avoid   = "-".join(str(switch_id)  for switch_id, _ in bad_switch_loads)
    bad_switch_loads_to_avoid = "-".join(str(load_value) for _, load_value in bad_switch_loads)

    # Create the command
    #"Path-Detour-SRv6 source_switch destination_switch source_ip destination_ip flow_label current_path nodes_to_avoid load_on_nodes_to_avoid (0-1)
    command = "Path-Detour-SRv6 %s %s %s %s %s %s %s %s" % (uriSrc, uriDst, src_ip, dst_ip, flow_label, path_updated, bad_switch_ids_to_avoid, bad_switch_loads_to_avoid)

    result = send_command(session, command)

    # Clean the message from ANSI escape sequences
    lines = result.strip().split('\n')
    msg = lines[3].strip()         # Strip trailing whitespace and newlines
    msg = strip_ansi_escape_sequences(msg)


    cmp = "Success"

    # print raw data bytes
    if msg.strip() != cmp.strip():
        code = 1

    #print("code:\t", code)
    #print("msg:\t", repr(msg.strip()))
    #print("cmp:\t", repr(cmp.strip()))
    return code, msg, srcID

def print_tags_fields(result):    
    for series in result.raw['series']:
        tags = series.get('tags')
        values = series.get('values')
        print(f"Tags: {tags}")
        for value in values:
            fields = {}
            for i, field_name in enumerate(series['columns']):
                if field_name != 'time':  # exclude 'time' column
                    fields[field_name] = value[i]
            print(f"Fields: {fields}")
        print("-----------------------")

def apply_query(query):
    # Connect to the InfluxDB client
    client = InfluxDBClient(host=host, database=dbname)
    
    # Execute the query
    result = client.query(query)
    
    # Close the connection
    client.close()

    return result

def normalize_value(value, min_value, max_value):
    normalized = (value - min_value) / (max_value - min_value)
    return round(normalized, 3)

def calculate_MCDA_loads(non_infra_switch, normalized_num_packets, normalized_avg_packet_size, normalized_avg_packet_procesing_time):
    # Create a numpy array for normalized values and weights
    normalized_values = np.array([
        non_infra_switch,
        normalized_num_packets,
        normalized_avg_packet_size,
        normalized_avg_packet_procesing_time
    ])
    
    weight_values = np.array([
        weights['is_infra_switch'],
        weights['num_packets'],
        weights['avg_packet_procesing_time'],
        weights['avg_packet_size']
    ])

    #print("Normalized values:", normalized_values)
    #print("Weight values:", weight_values)
    # Calculate the weighted sum (MCDA score)
    score = np.dot(normalized_values, weight_values)
    return round(score, 3)

def calculate_switches_load(stats_by_switch):
    switch_loads = []

    for series in stats_by_switch.raw['series']:
        tags = series.get('tags')
        values = series.get('values')       #[0][time, num_packets', 'average_latency', 'average_size]

        switch_id = int(tags['switch_id'])
        num_packets = values[0][1]                  #no decimals
        avg_packet_size = values[0][3]              #bytes
        avg_packet_procesing_time = values[0][2]    #nanoseconds

        non_infra_switch = 1
        if switch_id in static_infra_switches: non_infra_switch = 0   

        
        #print("-----------------------")
        #print(f"Switch ID: {switch_id}")
        #print(f"Number of packets: {num_packets}")
        #print(f"Average packet size: {avg_packet_size}")
        #print(f"Average packet processing time: {avg_packet_procesing_time}")
        
        

        # See if there is a need to update the max num_packets possible in the current time window
        #if num_packets > normalization_limits['num_packets'][1]:
        #    normalization_limits['num_packets'][1] = num_packets
        #    print("Updated max num_packets to:", num_packets)



        #--------------------Normalize the values and calculate the MCDA loads--------------------

        # Normalize the values
        normalized_num_packets               = normalize_value(num_packets,               normalization_limits['num_packets'][0],               normalization_limits['num_packets'][1])
        normalized_avg_packet_size           = normalize_value(avg_packet_size,           normalization_limits['packet_size'][0],           normalization_limits['packet_size'][1])
        normalized_avg_packet_procesing_time = normalize_value(avg_packet_procesing_time, normalization_limits['packet_procesing_time'][0], normalization_limits['packet_procesing_time'][1])
        

        #print("-----------------------")
        #print(f"Switch ID: {switch_id}")
        #print(f"Normalized Number of packets: {normalized_num_packets}")
        #print(f"Normalized Average packet size: {normalized_avg_packet_size}")
        #print(f"Normalized Average packet processing time: {normalized_avg_packet_procesing_time}")

        loads = calculate_MCDA_loads(non_infra_switch, normalized_num_packets, normalized_avg_packet_size, normalized_avg_packet_procesing_time)

        print(f'The MCDA loads for: {switch_id} \tis: {loads}')

        #add the switch_id and the load to the list
        switch_loads.append((switch_id, loads))

    #print the list of switch_id and loads
    #for switch in switch_loads:
    #    print(f"Switch ID: {switch[0]} \tLoad: {switch[1]}")

    return switch_loads

def get_wrost_flow_on_switch(switch_id):
    #--------Get the worst flow in the current switch
    wrost_flow = None
    wrost_load = -1

    #similar to global one, but this only considers the current switch
    #switch_normalization_limits = update_max_values_on_switch(switch_id) 
    #print("Switch normalization limits:", normalization_limits)

    #Get flow stats for the current switch
    query = f"""
        SELECT 
            COUNT("latency") AS num_packets_on_switch, 
            MEAN("size") AS avg_size,
            MEAN("latency") AS avg_latency
        FROM switch_stats 
        WHERE 
            time >= '{minutes_ago_str}' AND
            "switch_id" = '{switch_id}'
        GROUP BY "src_ip", "dst_ip", "flow_label"
    """

    result = apply_query(query)
    #print tags and fields
    #print_tags_fields(result)


    #There is already 2 checks done before this function call, so I can assume that there is data to analyze
    #print(result)

    #Get data from the tags and fields
    for series in result.raw['series']:
        tags = series.get('tags')
        values = series.get('values')       #[0][time, num_packets', 'average_latency', 'average_size]

        src_ip = tags['src_ip']
        dst_ip = tags['dst_ip']
        flow_label = tags['flow_label']

        num_packets = values[0][1]                  #no decimals
        avg_packet_size = values[0][2]              #bytes
        avg_packet_procesing_time = values[0][3]    #nanoseconds

        #See if the current switch is src or dst of the flow, if so skip it
        if compare_ipv6_segment(src_ip, 2, switch_id) or compare_ipv6_segment(dst_ip, 2, switch_id):
            #print("Switch is src or dst of the flow, skipping")
            continue

        #print("-----------------------")
        #print(f"Flow: {src_ip} -> {dst_ip} (Flow label: {flow_label})")
        #print(f"Number of packets: {num_packets}")
        #print(f"Average packet size: {avg_packet_size}")
        #print(f"Average packet processing time: {avg_packet_procesing_time}")

        # Normalize the values, using the global normalization limits (not switch specific)
        normalized_num_packets               = normalize_value(num_packets,               normalization_limits['num_packets'][0],           normalization_limits['num_packets'][1])
        normalized_avg_packet_size           = normalize_value(avg_packet_size,           normalization_limits['packet_size'][0],           normalization_limits['packet_size'][1])
        normalized_avg_packet_procesing_time = normalize_value(avg_packet_procesing_time, normalization_limits['packet_procesing_time'][0], normalization_limits['packet_procesing_time'][1])


        #print(f"Normalized Number of packets: {normalized_num_packets}")
        #print(f"Normalized Average packet size: {normalized_avg_packet_size}")
        #print(f"Normalized Average packet processing time: {normalized_avg_packet_procesing_time}")


        # Calculate the MCDA load
        load = calculate_MCDA_loads(1, normalized_num_packets, normalized_avg_packet_size, normalized_avg_packet_procesing_time)

        #print(f"Flow Load: {load}")

        if load > wrost_load:
            wrost_load = load
            wrost_flow = (src_ip, dst_ip, flow_label)
            #print("New wrost flow:", wrost_flow)

    #print("For switch", switch_id, "the wrost flow is:", wrost_flow)
    return wrost_flow





def search_no_longer_overloaded_switches(session, switch_loads):
    #--------Iterate through active_SRv6_rules and see if the switchs (keys) have their loads below the thresholds_no_overloaded
    #if so remove said rule via ONOS if all good remove from our list
    load = 0            #It will remain 0 if the switch is not in the list of switch_loads (there is no flow passing through it) 
    switch_marked_to_remove = []
    for switch_id, SRv6_rules in active_SRv6_rules.items():
        
        # Get the load value for the current switch responsible for the current SRv6 rules
        #List of tuples
        for load_id, load_value in switch_loads:
            if load_id == switch_id:
                load = load_value
                print(f"Switch ID: {switch_id}, Load: {load}")
                break

        if load > thresholds_no_overloaded:
            continue

        print(BLUE + "Switch" + str(switch_id) + " is no longer overloaded, removing SRv6 rule" + END)

        switch_marked_to_remove = remove_switch_SRv6_rules(session, switch_id, SRv6_rules, switch_marked_to_remove)

    #iterate the list of switches that had all of their rules removed and remove them from active_SRv6_rules
    for switch_id in switch_marked_to_remove:
        del active_SRv6_rules[switch_id]

def search_overloaded_switches(session, switch_loads):
    flows_alrady_demanded_detour_on_this_call = []             #to avoid overlaps ona single call (srcIP, dstIP, flow_label)
    #print("Searching for overloaded switches")

    #--------Iterate through switch_loads and see if the switchs have their loads above the thresholds_overloaded
    #if so detetct the heavies flow in the switch and ask ONOS to create a detour
    #BUT in the curennt function call we store which flows we did already detour, so if the current worst flow for the current switch is in said list we skip it, because this switch will already be contained in the detour info
    #store the end result in active_SRv6_rules, there is the chance that nothing is created if there is no alternative path
    #but I will always get message from ONOS with what was done

    
    # Create a list of bad switch loads (above the overloaded threshold)
    bad_switch_loads = []
    for switch_id, load_value in switch_loads:
        #print(f"Switch ID: {switch_id}, Load: {load_value}")
        if load_value >= thresholds_overloaded:                             
            bad_switch_loads.append((switch_id, load_value))

    #Got through the list of bad switches
    for switch_id, load_value in bad_switch_loads:
        print(f"Switch {switch_id} is overloaded, checking flows")

        #--------Get the heaviest flow in the current switch
        wrost = get_wrost_flow_on_switch(switch_id)

        if wrost is None:
            print(ORANGE + "No flow can be detoured on this switch\033[0m")
            continue
        if wrost in flows_alrady_demanded_detour_on_this_call:
            print("Flow already detoured on this call, skipping switch")
            continue

        current_path = get_current_path(wrost)
        code, result, srcSwitchID = request_SRv6_detour(session, wrost, current_path, bad_switch_loads)

        #store the flow that was requested to detoured
        flows_alrady_demanded_detour_on_this_call.append(wrost)

        print(result)
        if code != 0:
            continue
        else:
            #prepare info to store in active_SRv6_rules 
            print("Storing Detour info")

            devideID = srcSwitchID          #device that injects the SRv6 in the packet
            srcIP = wrost[0]                #source IP of the flow
            dstIP = wrost[1]                #destination IP of the flow
            flow_label = wrost[2]           #flow label of the flow
            src_mask = 128                  #source mask of the flow
            dst_mask = 128                  #destination mask of the flow
            flow_label_mask = 255           #flow label mask of the flow
            
            values = {'deviceID': devideID, 'srcIP': srcIP, 'dstIP': dstIP, 'flow_label': flow_label, 'src_mask': src_mask, 'dst_mask': dst_mask, 'flow_label_mask': flow_label_mask}

            #store the SRv6 rule in the dictionary active_SRv6_rules, switch_id is the switch that was overloaded
            store_SRv6_rule(switch_id, values)      

            print(CYAN + "created SRv6 rule:" + str(values) + END)



def get_stats_by_switch():
    global minutes_ago_str
    query = f"""
                SELECT 
                    COUNT("latency") AS num_packets, 
                    MEAN("latency") AS average_latency, 
                    MEAN("size") AS average_size
                FROM switch_stats 
                WHERE time >= '{minutes_ago_str}' 
                GROUP BY "switch_id"
                """

    result = apply_query(query)

    return result

def update_max_values_globaly():
    global minutes_ago_str
    global normalization_limits

    # Define normalization limits for each data type
    normalization_limits = {
        'num_packets': [0, -1],                                    # Min and max values for number of packets (no decimals)
        'packet_size': [0, network_MTU],                           # Min and max values for average packet size (bytes)
        'packet_procesing_time': [0, -1]                           # Min and max values for average packet processing time (nanoseconds), 
    }

    # Send query to DB so I can get the current max for packet_procesing_time and count how many packets, remove the -1
    query = f"""
                SELECT MAX("latency") AS MAX_latency
                FROM switch_stats 
                WHERE time >= '{minutes_ago_str}' 
                """
    result = apply_query(query)
    #if empty return False
    if not result: return False

    #extract the values from the query
    for series in result.raw['series']:
        values = series.get('values')       #[0][time, MAX_latency]
        max_latency = values[0][1]          #nanoseconds


    query = f"""
                SELECT COUNT("latency") AS total_num_packets
                FROM flow_stats 
                WHERE time >= '{minutes_ago_str}' 
                """
    result = apply_query(query)
    #if empty return False
    if not result: return False

    #extract the values from the query
    for series in result.raw['series']:
        values = series.get('values')       #[0][time, total_num_packets]
        num_packets = values[0][1]          #no decimals



    #--------------Store 2 values in the normalization_limits
    normalization_limits['num_packets'][1] = num_packets
    normalization_limits['packet_procesing_time'][1] = max_latency

    print("Updated global normalization limits:", normalization_limits)


    return True

def analyze(session, alternation_flag):
    global minutes_ago_str 

    # Get the current time and the time some minutes ago
    now = datetime.now(timezone.utc)
    minutes_ago = now - timedelta(minutes=analisy_window_minutes)
    
    # Format the timestamps, to the same in the DB
    minutes_ago_str = minutes_ago.strftime('%Y-%m-%dT%H:%M:%SZ')

    #---------------Get the stats by switch
    result = get_stats_by_switch()
    if not result:
        print("No data to analyze, sleeping for", sleep_time_seconds, "seconds")
        sleep(sleep_time_seconds)
        return alternation_flag

    #---------------Get current windows limit values for normalization
    with_data = update_max_values_globaly()
    if not with_data:
        print(GREEN+"No data to analyze, sleeping for", sleep_time_seconds, "seconds" + END)
        sleep(sleep_time_seconds)
        return alternation_flag

    switch_loads = calculate_switches_load(result)

    if alternation_flag:                                                #Search NO-LONGER overloaded switches
        search_no_longer_overloaded_switches(session, switch_loads)
        print(MAGENTA+'Active_SRv6_rules after search_no_longer_overloaded_switches:', active_SRv6_rules , END)
    else:                                                               #Search FOR overloaded switches
        search_overloaded_switches(session, switch_loads)
        print(MAGENTA+'Active_SRv6_rules after search_overloaded_switches:', active_SRv6_rules , END)

    alternation_flag = not alternation_flag
    print(GREEN+"Sleeping for", sleep_time_seconds, "seconds"+ END)
    sleep(sleep_time_seconds)

    return alternation_flag

def main():
    global current_iteration, active_SRv6_rules

    current_iteration = 1
    alternation_flag = False
    session = connect_to_onos()
    
    if not session:
        print("ONOS Session not established")
        exit()

    delete_old_log()

    if args.iterations_timer == 0:    # Infinite loop to analyze the data
        while True:
            alternation_flag = analyze(session, alternation_flag)
    
    while current_iteration <= args.num_iterations:
        start_iteration = datetime.now()
        print(f"Starting iteration {current_iteration} of {args.num_iterations} at {start_iteration}")

        # Only loop if not reach the time limit, taking into account the time it will take
        # to sleep next and get back here (we must be in sync with the start of the next iteration)
        while datetime.now() - start_iteration + timedelta(seconds=sleep_time_seconds) < timedelta(seconds=args.iterations_timer):
            alternation_flag = analyze(session, alternation_flag)
        
        #sleep for the remaining time of the iteration
        sync_sleep_seconds = args.iterations_timer - (datetime.now() - start_iteration).total_seconds()
        print(f"Waiting for {sync_sleep_seconds} seconds so be in sync with the next iteration's start")
        sleep(sync_sleep_seconds)

        #reset for the next iteration
        alternation_flag = False
        current_iteration += 1
        remove_all_active_SRv6_rules(session)

    session.close()


if __name__ == "__main__":
    parse_args()
    main()