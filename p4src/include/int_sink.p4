/* -*- P4_16 -*- */

control process_int_sink (
    inout headers hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    action int_sink() {
        // restore original headers
        hdr.ipv6.dscp = hdr.intl4_shim.udp_ip_dscp;
        // restore length fields of IPv6 header and UDP header
        bit<16> len_bytes = (((bit<16>)hdr.intl4_shim.len) << 2) + INT_SHIM_HEADER_SIZE;
        hdr.ipv6.payload_len = hdr.ipv6.payload_len - len_bytes;
        if(hdr.udp.isValid()) {
            hdr.udp.length_ = hdr.udp.length_ - len_bytes;
        }
        // remove all the INT information from the packet
        hdr.intl4_shim.setInvalid();
        hdr.int_header.setInvalid();
        hdr.int_switch_id.setInvalid();
        hdr.int_level1_port_ids.setInvalid();
        hdr.int_hop_latency.setInvalid();
        hdr.int_q_occupancy.setInvalid();
        hdr.int_ingress_tstamp.setInvalid();
        hdr.int_egress_tstamp.setInvalid();
        hdr.int_level2_port_ids.setInvalid();
        hdr.int_egress_tx_util.setInvalid();
        hdr.int_data.setInvalid();
    }

    table tb_int_sink {
        actions = {
            int_sink;
        }
        default_action = int_sink();
    }

    apply {
        tb_int_sink.apply();
    }
}

control process_int_report (
    inout headers hdr,
    inout local_metadata_t local_metadata,
    inout standard_metadata_t standard_metadata) {

    register<bit<22>>(1) seq_number;
    /********************** A C T I O N S **********************/

    action increment_counter() {
        bit<22> tmp;
        seq_number.read(tmp, 0);
        tmp = tmp + 1;
        seq_number.write(0, tmp);
    }

    action do_report_encapsulation( mac_t src_mac, 
                                    mac_t mon_mac, 
                                    ip_address_t src_ip,
                                    ip_address_t mon_ip, 
                                    l4_port_t mon_port) {

        // INT Raport structure
        // [Eth][IP][UDP][INT RAPORT HDR][ETH][IP][UDP/TCP][INT HDR][INT DATA]
        //Report Ethernet Header
        hdr.report_ethernet.setValid();
        hdr.report_ethernet.dst_addr = mon_mac;
        hdr.report_ethernet.src_addr = src_mac;
        hdr.report_ethernet.ether_type = ETH_TYPE_IPV6;


        //Report IPV6 Header
        hdr.report_ipv6.setValid();
        hdr.report_ipv6.version = IP_VERSION_6;
        hdr.report_ipv6.dscp = 6w0;
        hdr.report_ipv6.ecn = 2w0;
        hdr.report_ipv6.flow_label = 20w0;     //20w0 here is just a placeholder

        // The same length but for ipv6, the base header length does not count for the payload length
        hdr.report_ipv6.payload_len = //(bit<16>) IPV4_MIN_HEAD_LEN + 
                              (bit<16>) UDP_HEADER_LEN + 
                              (bit<16>) REPORT_GROUP_HEADER_LEN +
                              (bit<16>) REPORT_INDIVIDUAL_HEADER_LEN +
                              (bit<16>) ETH_HEADER_LEN + 
                              (bit<16>) IPV6_MIN_HEAD_LEN + 
                              (bit<16>) UDP_HEADER_LEN + 
                              INT_SHIM_HEADER_SIZE + (((bit<16>) hdr.intl4_shim.len)<< 2);
        hdr.report_ipv6.next_header = 8w0x11;        // a 32-bit unsigned number with hex value 11 (UDP)
        hdr.report_ipv6.hop_limit = REPORT_HDR_HOP_LIMIT;
        hdr.report_ipv6.src_addr = src_ip;
        hdr.report_ipv6.dst_addr = mon_ip;





        //Report UDP Header
        hdr.report_udp.setValid();
        hdr.report_udp.src_port = 1234;
        hdr.report_udp.dst_port = mon_port;
        hdr.report_udp.length_ = (bit<16>) UDP_HEADER_LEN + 
                                 (bit<16>) REPORT_GROUP_HEADER_LEN +
                                 (bit<16>) REPORT_INDIVIDUAL_HEADER_LEN +
                                 (bit<16>) ETH_HEADER_LEN + 
                                 (bit<16>) IPV6_MIN_HEAD_LEN + 
                                 (bit<16>) UDP_HEADER_LEN +
                                 INT_SHIM_HEADER_SIZE + (((bit<16>) hdr.intl4_shim.len)<< 2);
        
        hdr.report_group_header.setValid();
        hdr.report_group_header.ver = 2;
        hdr.report_group_header.hw_id = HW_ID;
        seq_number.read(hdr.report_group_header.seq_no, 0);
        increment_counter();
        hdr.report_group_header.node_id = local_metadata.int_meta.switch_id;

        /* Telemetry Report Individual Header */
        hdr.report_individual_header.setValid();
        hdr.report_individual_header.rep_type = 1;
        hdr.report_individual_header.in_type = 3;
        hdr.report_individual_header.rep_len = 0;
        hdr.report_individual_header.md_len = 0;
        hdr.report_individual_header.d = 0;
        hdr.report_individual_header.q = 0;
        hdr.report_individual_header.f = 1;
        hdr.report_individual_header.i = 0;
        hdr.report_individual_header.rsvd = 0;

        /* Individual report inner contents */

        hdr.report_individual_header.rep_md_bits = 0;
        hdr.report_individual_header.domain_specific_id = 0;
        hdr.report_individual_header.domain_specific_md_bits = 0;
        hdr.report_individual_header.domain_specific_md_status = 0;

        truncate((bit<32>)hdr.report_ipv6.payload_len + (bit<32>) ETH_HEADER_LEN);
    }

    table tb_generate_report {
        key = {              //needs at least a key
            16w0 : exact;    //dummy key to trigger the action
        }
        actions = {
            do_report_encapsulation;
            NoAction();
        }
        default_action = NoAction();
    }

    apply {
        tb_generate_report.apply();
    }
}
