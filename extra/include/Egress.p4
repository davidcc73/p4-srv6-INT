#ifndef __EGRESS__
#define __EGRESS__

#include "include/header.p4"
#include "include/define.p4"


// Egress control with Netronome features
control MyEgress(inout headers_t hdr,
                 inout metadata_t meta,
                 inout standard_metadata_t standard_metadata) {
    
    // Netronome-specific counter
    @resources("stats_engine")
    counter(256, CounterType.packets_and_bytes) egress_counter;
    
    // Netronome-specific meter
    @resources("meter_engine")
    meter(32, MeterType.bytes) traffic_meter;
    
    // Action to update statistics
    @resources("stats_engine")
    action update_stats(bit<8> index) {
        egress_counter.count((bit<32>)index);
        traffic_meter.execute_meter((bit<32>)index, meta.netro.traffic_class);
    }
    
    // Netronome-optimized egress table
    @max_size(256)
    @resources("exact_match")
    table egress_stats {
        key = {
            meta.traffic_class: exact;
            standard_metadata.egress_port: exact;
        }
        actions = {
            update_stats;
            NoAction;
        }
        default_action = NoAction;
    }

    apply {
        if (hdr.ipv4.isValid()) {
            // Update statistics
            egress_stats.apply();
            
            // Netronome-specific timestamp
            meta.netro.timestamp = standard_metadata.egress_global_timestamp;
        }
    }
}


#endif