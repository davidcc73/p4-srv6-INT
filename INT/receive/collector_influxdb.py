#!/usr/bin/env python3

import sys
from scapy.all import sniff
from influxdb import InfluxDBClient
from colllector import *

INFLUX_HOST = 'localhost'
INFLUX_DB = 'int'

def handle_pkt(pkt,c):
    if INTREP in pkt :
        print("\n\n********* Receiving Telemetry Report ********")
        flow_info = c.parser_int_pkt(pkt)
        flow_info.show()
        c.export_influxdb(flow_info)

def main():
    iface = 's.*-eth2'
    print("sniffing on %s" % iface)
    sys.stdout.flush()

    influx_client = InfluxDBClient(host=INFLUX_HOST,database=INFLUX_DB)
    print(influx_client)
    c = Collector(influx_client)
    sniff(iface = iface,filter='inbound and tcp or udp',
        prn = lambda x: handle_pkt(x,c))

if __name__ == '__main__':
    main()
