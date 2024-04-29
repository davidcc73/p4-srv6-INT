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

#ifndef __PARSER__
#define __PARSER__

#include "define.p4"

parser ParserImpl (packet_in packet,
                   out parsed_headers_t hdr,
                   inout local_metadata_t local_metadata,
                   inout standard_metadata_t standard_metadata){
    state start {
        transition select(standard_metadata.ingress_port) {
            CPU_PORT: parse_packet_out;
            default: parse_ethernet;
        }
    }

    state parse_packet_out {
        packet.extract(hdr.packet_out);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.ether_type){
            ETHERTYPE_ARP: parse_arp;
            ETHERTYPE_IPV4: parse_ipv4;
            ETHERTYPE_IPV6: parse_ipv6;
            default: accept;
        }
    }

    state parse_ipv6 {
        packet.extract(hdr.ipv6);
        local_metadata.ip_proto = hdr.ipv6.next_header;
        transition select(hdr.ipv6.next_header) {
            PROTO_TCP: parse_tcp;
            PROTO_UDP: parse_udp;
            PROTO_ICMPV6: parse_icmpv6;
            PROTO_SRV6: parse_srv6;
            PROTO_IPV6: parse_ipv6_inner;
            PROTO_IP_IN_IP: parse_ipv4;
            default: accept;
        }
    }

    state parse_srv6 {
        packet.extract(hdr.srv6h);
        transition parse_srv6_list;
    }
    state parse_srv6_list {
        packet.extract(hdr.srv6_list.next);
        bool next_segment = (bit<32>)hdr.srv6h.segment_left - 1 == (bit<32>)hdr.srv6_list.lastIndex;
        transition select(next_segment) {
            true: mark_current_srv6;
            _: check_last_srv6;
        }
    }

    state mark_current_srv6 {
        // current metadata
        local_metadata.next_srv6_sid = hdr.srv6_list.last.segment_id;
        transition check_last_srv6;
    }

    state check_last_srv6 {
        // working with bit<8> and int<32> which cannot be cast directly; using bit<32> as common intermediate type for comparision
        bool last_segment = (bit<32>)hdr.srv6h.last_entry == (bit<32>)hdr.srv6_list.lastIndex;
        transition select(last_segment) {
           true: parse_srv6_next_hdr;
           false: parse_srv6_list;
        }
    }
    state parse_srv6_next_hdr {
        transition select(hdr.srv6h.next_header) {
            PROTO_TCP: parse_tcp;
            PROTO_UDP: parse_udp;
            PROTO_ICMPV6: parse_icmpv6;
            PROTO_IPV6: parse_ipv6_inner;
            PROTO_IP_IN_IP: parse_ipv4;
            default: accept;
        }
    }

    state parse_ipv4 {
        packet.extract(hdr.ipv4);
        local_metadata.ip_proto = hdr.ipv4.protocol;
        //Need header verification?
        transition select(hdr.ipv4.protocol) {
            PROTO_TCP: parse_tcp;
            PROTO_UDP: parse_udp;
            PROTO_ICMP: parse_icmp;
            default: accept;
        }
    }

    state parse_ipv6_inner {
        packet.extract(hdr.ipv6_inner);

        transition select(hdr.ipv6_inner.next_header) {
            PROTO_TCP: parse_tcp;
            PROTO_UDP: parse_udp;
            PROTO_ICMPV6: parse_icmpv6;
            PROTO_SRV6: parse_srv6;
            default: accept;
        }
    }

    state parse_arp {
        packet.extract(hdr.arp);
        transition accept;
    }

    state parse_tcp {
        packet.extract(hdr.tcp);
        local_metadata.l4_src_port = hdr.tcp.src_port;
        local_metadata.l4_dst_port = hdr.tcp.dst_port;
        transition select(hdr.ipv6.dscp) {
            DSCP_INT &&& DSCP_MASK: parse_intl4_shim;
            default: accept;
        }
    }

    state parse_udp {
        packet.extract(hdr.udp);
        local_metadata.l4_src_port = hdr.udp.src_port;
        local_metadata.l4_dst_port = hdr.udp.dst_port;
        transition select(hdr.ipv6.dscp) {
            DSCP_INT &&& DSCP_MASK: parse_intl4_shim;
            default: accept;
        }
    }

    state parse_icmp {
        packet.extract(hdr.icmp);
        local_metadata.icmp_type = hdr.icmp.type;
        transition accept;
    }

    state parse_icmpv6 {
        packet.extract(hdr.icmpv6);
        local_metadata.icmp_type = hdr.icmpv6.type;
        transition select(hdr.icmpv6.type) {
            ICMP6_TYPE_NS: parse_ndp;
            ICMP6_TYPE_NA: parse_ndp;
            default: accept;
        }

    }

    state parse_ndp {
        packet.extract(hdr.ndp);
        transition parse_ndp_option;
    }

    state parse_ndp_option {
        packet.extract(hdr.ndp_option);
        transition accept;
    }

    state parse_intl4_shim {
        packet.extract(hdr.intl4_shim);
        local_metadata.int_meta.intl4_shim_len = hdr.intl4_shim.len;  //all INT but shim not included
        transition parse_int_header;
    }

    state parse_int_header {
        packet.extract(hdr.int_header); //parse INT instructions
        transition parse_int_data;
    }

    state parse_int_data {
        // Parse INT metadata stack, extract all before body
        packet.extract(hdr.int_data, ((bit<32>) (local_metadata.int_meta.intl4_shim_len - INT_HEADER_WORD)) << 5);
        transition accept;
    }
}

control DeparserImpl(packet_out packet, in parsed_headers_t hdr) {
    apply {
        // report headers
        packet.emit(hdr.report_ethernet);
        packet.emit(hdr.report_ipv6);
        packet.emit(hdr.report_udp);
        packet.emit(hdr.report_group_header);
        packet.emit(hdr.report_individual_header);

        //ORIGINAL(SRv6) PACKET headers
        packet.emit(hdr.packet_in);
        packet.emit(hdr.ethernet);
        packet.emit(hdr.arp);
        packet.emit(hdr.ipv6);
        packet.emit(hdr.srv6h);
        packet.emit(hdr.srv6_list);
        packet.emit(hdr.ipv6_inner);
        packet.emit(hdr.ipv4);
        packet.emit(hdr.tcp);
        packet.emit(hdr.udp);
        packet.emit(hdr.icmp);
        packet.emit(hdr.icmpv6);
        packet.emit(hdr.ndp);
        packet.emit(hdr.ndp_option);
        
        // int header
        packet.emit(hdr.intl4_shim);        //extra int data
        packet.emit(hdr.int_header);        //the instructions
        // hop metadata                     //the generated INT statistics at the current hop
        packet.emit(hdr.int_switch_id);
        packet.emit(hdr.int_level1_port_ids);
        packet.emit(hdr.int_hop_latency);
        packet.emit(hdr.int_q_occupancy);
        packet.emit(hdr.int_ingress_tstamp);
        packet.emit(hdr.int_egress_tstamp);
        packet.emit(hdr.int_level2_port_ids);
        packet.emit(hdr.int_egress_tx_util);

        // int data
        packet.emit(hdr.int_data);          //the generated INT statistics from the previous hops
    }
}

#endif
