#ifndef __INGRESS__
#define __INGRESS__

#include "include/header.p4"
#include "include/define.p4"


// Ingress control with Netronome-specific optimizations
control MyIngress(inout headers_t hdr,
                  inout metadata_t meta,
                  inout standard_metadata_t standard_metadata) {
    
    // Netronome-specific register declarations
    @resources("mem_packet_buffer")
    register<bit<32>>(1024) flow_stats;
    
    // Classification actions
    @resources("multiplier")
    action classify_traffic(bit<8> class_id) {
        meta.traffic_class = class_id;
        meta.netro.traffic_class = class_id;
    }
    
    // Netronome-optimized tables
    @max_size(1024)
    @resources("exact_match")
    table traffic_classifier {
        key = {
            hdr.ipv4.srcAddr: exact;
            hdr.ipv4.dstAddr: exact;
            hdr.ipv4.protocol: exact;
        }
        actions = {
            classify_traffic;
            NoAction;
        }
        default_action = NoAction;
    }

    apply {
        if (hdr.ipv4.isValid()) {
            traffic_classifier.apply();
            
            // Netronome-specific packet marking
            if (meta.traffic_class != 0) {
                hdr.ipv4.diffserv = meta.traffic_class;
            }
        }
    }
}





#endif