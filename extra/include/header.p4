#ifndef __HEADER__
#define __HEADER__

#include "include/define.p4"

// Netronome-specific metadata
@chip_model("NFP-6xxx")
@chip_count(1)
struct netro_metadata_t {
    bit<16> pkt_len;
    bit<8>  in_port;
    bit<8>  out_port;
    bit<32> timestamp;
    bit<8>  traffic_class;
    bit<16> vf_id;     // Virtual Function ID
}

// Main headers remain similar
header ethernet_t {
    bit<48> dstAddr;
    bit<48> srcAddr;
    bit<16> ethType;
}

header ipv4_t {
    bit<4>  version;
    bit<4>  ihl;
    bit<8>  diffserv;
    bit<16> totalLen;
    bit<16> identification;
    bit<3>  flags;
    bit<13> fragOffset;
    bit<8>  ttl;
    bit<8>  protocol;
    bit<16> hdrChecksum;
    bit<32> srcAddr;
    bit<32> dstAddr;
}

// Netronome-specific struct definitions
struct metadata_t {
    netro_metadata_t netro;
    bit<8>  traffic_class;
    bit<32> flow_id;
}

struct headers_t {
    ethernet_t ethernet;
    ipv4_t ipv4;
}




#endif