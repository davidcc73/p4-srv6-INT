#!/usr/bin/env python3

import sys
import os
import threading
from scapy.all import sniff
from influxdb import InfluxDBClient
from colllector import *
import threading

stop_sniffing = False

INFLUX_HOST = 'localhost'
INFLUX_DB = 'int'

def handle_pkt(pkt,c):   #individually triggered by each sniffed packet
    print("got a TCP/UDP packet")
    if INTREP in pkt :
        print("\n\n********* Receiving Telemetry Report ********")
        flow_info = c.parser_int_pkt(pkt)
        flow_info.show()
        c.export_influxdb(flow_info)

def sniff_interface(iface,c ):
    print("Sniffing on interface:", iface)
    sniff(iface = iface,filter='inbound and tcp or udp', prn = lambda x: handle_pkt(x, c), stop_filter=lambda _: stop_sniffing)

def main():
    try:
        influx_client = InfluxDBClient(host=INFLUX_HOST, database=INFLUX_DB)
        influx_client.ping()  # Check if connection is successful
        print("Connected to InfluxDB successfully.")
    except Exception as e:
        print("Failed to connect to InfluxDB:", e)
        sys.exit(1)  # Terminate the script with a non-zero exit code

    c = Collector(influx_client)
    print(influx_client)

    global stop_sniffing
    ifaces = [i for i in os.listdir('/sys/class/net/') if i.endswith('900')]

    if ifaces:
        print("Sniffing on interfaces:", ifaces)
        threads = []
        try:
            for iface in ifaces:
                sys.stdout.flush()
                t = threading.Thread(target=sniff_interface, args=(iface, c))
                threads.append(t)
                t.start()

            # Wait for all threads to finish
            for t in threads:
                t.join()
        except KeyboardInterrupt:
            stop_sniffing = True
            print("\nCtrl+C detected. Terminating...")
            sys.exit(0)
    else:
        print("No interfaces ending with '900' found.")




if __name__ == '__main__':
    main()
