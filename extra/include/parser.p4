#ifndef __PARSER__
#define __PARSER__

#include "include/header.p4"
#include "include/define.p4"


// Parser with Netronome-specific considerations
parser MyParser(packet_in pkt,
                out headers_t hdr,
                inout metadata_t meta,
                inout standard_metadata_t standard_metadata) {
    
    state start {
        // Netronome-specific initialization
        meta.netro.timestamp = standard_metadata.ingress_global_timestamp;
        transition parse_ethernet;
    }

    state parse_ethernet {
        pkt.extract(hdr.ethernet);
        transition select(hdr.ethernet.ethType) {
            0x0800: parse_ipv4;
            default: accept;
        }
    }

    state parse_ipv4 {
        pkt.extract(hdr.ipv4);
        meta.netro.pkt_len = hdr.ipv4.totalLen;
        transition accept;
    }
}

// Deparser with Netronome optimization
control MyDeparser(packet_out pkt, in headers_t hdr) {
    @resources("deparser_engine")
    apply {
        // Emit headers in order
        pkt.emit(hdr.ethernet);
        if (hdr.ipv4.isValid()) {
            pkt.emit(hdr.ipv4);
        }
    }
}



#endif
