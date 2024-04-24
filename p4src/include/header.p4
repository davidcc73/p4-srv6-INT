/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __HEADER__
#define __HEADER__

#include "define.p4"

#define MAX_HOPS 4

@controller_header("packet_in")
header packet_in_header_t {
    port_num_t ingress_port;
    bit<7> _pad;
}

@controller_header("packet_out")
header packet_out_header_t {
    port_num_t egress_port;
    bit<7> _pad;
}

header ethernet_t {
    mac_addr_t dst_addr;
    mac_addr_t src_addr;
    bit<16> ether_type;
}
const bit<8> ETH_HEADER_LEN = 14;

header ipv4_t {
    bit<4> version;
    bit<4> ihl;
    bit<6> dscp;
    bit<2> ecn;
    bit<16> total_len;
    bit<16> identification;
    bit<3> flags;
    bit<13> frag_offset;
    bit<8> ttl;
    bit<8> protocol;
    bit<16> hdr_checksum;
    bit<32> src_addr;
    bit<32> dst_addr;
}

header ipv6_t {
    bit<4> version;
    bit<6> dscp;
    bit<2> ecn;
    bit<20> flow_label;
    bit<16> payload_len;
    bit<8> next_header;
    bit<8> hop_limit;
    bit<128> src_addr;
    bit<128> dst_addr;
}
const bit<8> IPV6_MIN_HEAD_LEN = 40;

header srv6h_t {
    bit<8> next_header;
    bit<8> hdr_ext_len;
    bit<8> routing_type;
    bit<8> segment_left;
    bit<8> last_entry;
    bit<8> flags;
    bit<16> tag;
}

header srv6_list_t {
    bit<128> segment_id;
}

header arp_t {
    bit<16> hw_type;
    bit<16> proto_type;
    bit<8> hw_addr_len;
    bit<8> proto_addr_len;
    bit<16> opcode;
}

header tcp_t {
    bit<16> src_port;
    bit<16> dst_port;
    bit<32> seq_no;
    bit<32> ack_no;
    bit<4>  data_offset;
    bit<3>  res;
    bit<3>  ecn;
    bit<6>  ctrl;
    bit<16> window;
    bit<16> checksum;
    bit<16> urgent_ptr;
}

header udp_t {
    bit<16> src_port;
    bit<16> dst_port;
    bit<16> length_;
    bit<16> checksum;
}
const bit<8> UDP_HEADER_LEN = 8;

header icmp_t {
    bit<8> type;
    bit<8> icmp_code;
    bit<16> checksum;
    bit<16> identifier;
    bit<16> sequence_number;
    bit<64> timestamp;
}

header icmpv6_t {
    bit<8> type;
    bit<8> code;
    bit<16> checksum;
}

header ndp_t {
    bit<32> flags;
    bit<128> target_addr;
}

header ndp_option_t {
    bit<8> type;
    bit<8> length;
    bit<48> value;
}


struct int_metadata_t {
    switch_id_t switch_id;
    bit<16> new_bytes;
    bit<8>  new_words;
    bool  source;
    bool  sink;
    bool  transit;
    bit<8> intl4_shim_len;
    bit<16> int_shim_len;
}


const bit<8> CLONE_FL_1  = 1;
const bit<8> CLONE_FL_clone3  = 3;

struct preserving_metadata_t {
    @field_list(CLONE_FL_1)
    bit<9> ingress_port;
    bit<9> egress_spec;
    bit<9> egress_port;
    bit<32> clone_spec;
    bit<32> instance_type;
    bit<1> drop;
    bit<16> recirculate_port;
    bit<32> packet_length;
    bit<32> enq_timestamp;
    bit<19> enq_qdepth;
    bit<32> deq_timedelta;
    @field_list(CLONE_FL_1)
    bit<19> deq_qdepth;
    @field_list(CLONE_FL_1)
    bit<48> ingress_global_timestamp;
    bit<48> egress_global_timestamp;
    bit<32> lf_field_list;
    bit<16> mcast_grp;
    bit<32> resubmit_flag;
    bit<16> egress_rid;
    bit<1> checksum_error;
    bit<32> recirculate_flag;
}
 
struct preserving_metadata_CPU_t {
    @field_list(CLONE_FL_clone3)
    bit<9> ingress_port;
    @field_list(CLONE_FL_clone3)
    bit<9> egress_port;
    @field_list(CLONE_FL_clone3)
    bool to_CPU;                 //true when the packet is cloned to CPU/Controller, default is false, so non-CPU cloned packets will still have see the correct value (false)
}

//Custom metadata definition
struct local_metadata_t {
    bool is_multicast;
    bool skip_l2;
    bool xconnect;
    ipv6_addr_t next_srv6_sid;
    ipv6_addr_t ua_next_hop;
    bit<8> ip_proto;
    bit<8> icmp_type;
    l4_port_t l4_src_port;
    l4_port_t l4_dst_port;
    bool ipv4_update;

    int_metadata_t int_meta;                    //used by INT
    preserving_metadata_t perserv_meta;         //used by INT
    preserving_metadata_CPU_t perserv_CPU_meta; //to migrate from clone3() to clone_preserving() in the clone_to_CPU scenario
    //digest_t              mac_learn_digest; //ARP used for the original project with ipv4 INT packets
}


#endif



//------------------------------majoraty of the content from int_headers.p4 is restrained here---------------------------------

#ifndef __INT_HEADERS__
#define __INT_HEADERS__

// INT shim header for TCP/UDP  (contains addicional INT indormation??)
header intl4_shim_t {
    bit<4> int_type;                // Type of INT Header
    bit<2> npt;                     // Next protocol type
    bit<2> rsvd;                    // Reserved
    bit<8> len;                     // Length of INT Metadata header and INT stack in 4-byte words, not including the shim header (1 word)
    bit<6> udp_ip_dscp;            // depends on npt field. either original dscp, ip protocol or udp dest port
    bit<10> udp_ip;                // depends on npt field. either original dscp, ip protocol or udp dest port
}

const bit<16> INT_SHIM_HEADER_SIZE = 4;

// INT header (contains INT instructions??)
header int_header_t {
    bit<4>   ver;                    // Version
    bit<1>   d;                      // Discard
    bit<1>  e;
    bit<1>  m;
    bit<12>  rsvd;
    bit<5>  hop_metadata_len;
    bit<8>  remaining_hop_cnt;
    bit<4>  instruction_mask_0003; /* split the bits for lookup */
    bit<4>  instruction_mask_0407;
    bit<4>  instruction_mask_0811;
    bit<4>  instruction_mask_1215;
    bit<16>  domain_specific_id;     // Unique INT Domain ID
    bit<16>  ds_instruction;         // Instruction bitmap specific to the INT Domain identified by the Domain specific ID
    bit<16>  ds_flags;               // Domain specific flags
}

const bit<16> INT_HEADER_SIZE = 12;

const bit<16> INT_TOTAL_HEADER_SIZE = INT_HEADER_SIZE + INT_SHIM_HEADER_SIZE;


// INT meta-value headers - different header for each value type
header int_switch_id_t {
    bit<32> switch_id;
}
header int_level1_port_ids_t {
    bit<16> ingress_port_id;
    bit<16> egress_port_id;
}
header int_hop_latency_t {
    bit<32> hop_latency;
}
header int_q_occupancy_t {
    bit<8> q_id;
    bit<24> q_occupancy;
}
header int_ingress_tstamp_t {
    bit<64> ingress_tstamp;
}
header int_egress_tstamp_t {
    bit<64> egress_tstamp;
}
header int_level2_port_ids_t {
    bit<32> ingress_port_id;
    bit<32> egress_port_id;
}

// these two not implemented yet
header int_egress_port_tx_util_t {
    bit<32> egress_port_tx_util;
}
header int_buffer_t {
    bit<8> buffer_id;
    bit<24> buffer_occupancy;
}

header int_data_t {
    // Maximum int metadata stack size in bits:
    // (0x3F - 3) * 4 * 8 (excluding INT shim header and INT header)
    varbit<1920> data;
}


// Report Telemetry Headers
header report_group_header_t {
    bit<4>  ver;
    bit<6>  hw_id;
    bit<22> seq_no;
    bit<32> node_id;
}

const bit<8> REPORT_GROUP_HEADER_LEN = 8;

header report_individual_header_t {
    bit<4>  rep_type;
    bit<4>  in_type;
    bit<8>  rep_len;
    bit<8>  md_len;
    bit<1>  d;
    bit<1>  q;
    bit<1>  f;
    bit<1>  i;
    bit<4>  rsvd;
    // Individual report inner contents for Reptype 1 = INT
    bit<16> rep_md_bits;
    bit<16> domain_specific_id;
    bit<16> domain_specific_md_bits;
    bit<16> domain_specific_md_status;
}
const bit<8> REPORT_INDIVIDUAL_HEADER_LEN = 12;

// Telemetry drop report header
header drop_report_header_t {
    bit<32> switch_id;
    bit<16> ingress_port_id;
    bit<16> egress_port_id;
    bit<8>  queue_id;
    bit<8>  drop_reason;
    bit<16> pad;
}
const bit<8> DROP_REPORT_HEADER_LEN = 12;



struct parsed_headers_t {
    ethernet_t ethernet;
    ipv6_t ipv6;
    ipv6_t ipv6_inner;
    ipv4_t ipv4;
    srv6h_t srv6h;
    srv6_list_t[MAX_HOPS] srv6_list;
    arp_t arp;
    tcp_t tcp;
    udp_t udp;
    icmp_t icmp;
    icmpv6_t icmpv6;
    ndp_t ndp;
    ndp_option_t ndp_option;
    packet_out_header_t packet_out;
    packet_in_header_t packet_in;

        // INT Report Encapsulation
    ethernet_t                  report_ethernet;
    ipv6_t                      report_ipv6;
    udp_t                       report_udp;

    // INT Headers
    intl4_shim_t                intl4_shim;
    int_header_t                int_header;

    //INT Metadata
    int_switch_id_t             int_switch_id;
    int_level1_port_ids_t       int_level1_port_ids;
    int_hop_latency_t           int_hop_latency;
    int_q_occupancy_t           int_q_occupancy;
    int_ingress_tstamp_t        int_ingress_tstamp;
    int_egress_tstamp_t         int_egress_tstamp;
    int_level2_port_ids_t       int_level2_port_ids;
    int_egress_port_tx_util_t   int_egress_tx_util;
    int_data_t                  int_data;

    //INT Report Headers
    report_group_header_t       report_group_header;
    report_individual_header_t  report_individual_header;
    drop_report_header_t        drop_report_header;
}


#endif
