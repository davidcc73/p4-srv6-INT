from time import sleep
from influxdb import InfluxDBClient
from datetime import datetime, timedelta, timezone
import numpy as np

analisy_window_minutes = 5
static_infra_switches = [9, 10, 11, 12, 13, 14]              #lsit of the switch's id that belong to the static infrastructure
thresholds_overloaded = 0.7                                  #percentage threshold to consider a switch as overloaded

# Define weights for each variable
weights = {
    'is_infra_switch': 0.6,            # Weight for switch type
    'num_packets': 0.7,                # Weight for number of packets
    'avg_packet_procesing_time': 0.80, # Weight for average packet processing time
    'avg_packet_size': 0.20            # Weight for average packet size
}


def print_query(result):    
    # Get the points from the result
    points = list(result.get_points())
    
    # Print the results
    for point in points:
        print(point)

def query_switch_stats_last_minutes(host, dbname):
    # Connect to the InfluxDB client
    client = InfluxDBClient(host=host, database=dbname)
    
    # Get the current time and the time some minutes ago
    now = datetime.now(timezone.utc)
    five_minutes_ago = now - timedelta(minutes=analisy_window_minutes)
    
    # Format the timestamps, to the same in the DB
    five_minutes_ago_str = five_minutes_ago.strftime('%Y-%m-%dT%H:%M:%SZ')
    
    # Construct the query
    query = f"SELECT switch_id,* FROM switch_stats WHERE time >= '{five_minutes_ago_str}'"
    
    # Execute the query
    result = client.query(query)
    
    # Close the connection
    client.close()

    return result

def calculate_averages_bySwitch(result):
    # Get the points from the result
    points = list(result.get_points())
    
    # Dictionary to hold avg latency and count of points for each switch
    stats_by_switch = {}
    
    #---------Prepare info peer switch
    for point in points:
        switch_id = point['switch_id']
        latency = point['latency']
        size = point['size']
        
        if switch_id not in stats_by_switch:
            stats_by_switch[switch_id] = {'num_packets': 0, 'average_latency': 0, 'average_size': 0}
        
        stats_by_switch[switch_id]['num_packets'] += 1
        stats_by_switch[switch_id]['average_latency'] += latency      
        stats_by_switch[switch_id]['average_size'] += size        
    
    #---------Calculate averages by switch
    for switch_id in stats_by_switch:
        num_packets = stats_by_switch[switch_id]['num_packets']
        total_latency = stats_by_switch[switch_id]['average_latency']
        total_size = stats_by_switch[switch_id]['average_size']
        
        stats_by_switch[switch_id]['average_latency'] = round(total_latency / num_packets, 2)
        stats_by_switch[switch_id]['average_size'] = round(total_size / num_packets, 2)
    
    '''
    print("Full dictionary of averagesby switch:", stats_by_switch)
    for switch_id, values in stats_by_switch.items():
        print(f"Switch:{switch_id}\tAverage Latency:{values['average_latency']} ms\tAverage Packet Size:{values['average_size']} Bytes")
    '''      
    return stats_by_switch

def search_for_overloaded_switches(stats_by_switch):
    overloaded_switches = []
    for switch_id, values in stats_by_switch.items():
        is_infra_switch = 0
        if switch_id in static_infra_switches: is_infra_switch = 1
        num_packets = values['num_packets']                             #no decimals
        avg_packet_procesing_time = values['average_latency']           #miliseconds
        avg_packet_size = values['average_size']                        #bytes



    #print(f'The MCDA score is: {score}')



    
    return overloaded_switches

if __name__ == "__main__":
    result = query_switch_stats_last_minutes(host='localhost', dbname='int')
    #print_query(result)

    stats_by_switch = calculate_averages_bySwitch(result)

    #while True:
    search_for_overloaded_switches(stats_by_switch)
        #print("Sleeping for", analisy_window_minutes, "minutes")
        #sleep(analisy_window_minutes * 60)

