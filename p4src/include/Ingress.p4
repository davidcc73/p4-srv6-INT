#ifndef __INGRESS__
#define __INGRESS__

//new includes for the INT usage
#include "define.p4"
#include "int_source.p4"

#define CPU_CLONE_SESSION_ID 99
#define UN_BLOCK_MASK     0xffffffff000000000000000000000000

/*************************************************************************
****************  I N G R E S S   P R O C E S S I N G   ****************** (SOURCE/SINK NODE)
*************************************************************************/

control IngressPipeImpl (inout parsed_headers_t hdr,
                         inout local_metadata_t local_metadata,
                         inout standard_metadata_t standard_metadata) {

    action drop() {
        mark_to_drop(standard_metadata);
    }

    action set_output_port(port_num_t port_num) {
        standard_metadata.egress_spec = port_num;
    }
    action set_multicast_group(group_id_t gid) {
        log_msg("Multicast group set to:{}", {gid});
        standard_metadata.mcast_grp = gid;
        local_metadata.is_multicast = true;
    }

    direct_counter(CounterType.packets_and_bytes) unicast_counter; 
    table unicast {
        key = {
            hdr.ethernet.dst_addr: exact; 
        }
        actions = {
            set_output_port;
            drop;
            NoAction;
        }
        counters = unicast_counter;
        default_action = NoAction();
    }

    direct_counter(CounterType.packets_and_bytes) multicast_counter;
    table multicast {
        key = {
            hdr.ethernet.dst_addr: ternary;
        }
        actions = {
            set_multicast_group;
            drop;
        }
        counters = multicast_counter;
        const default_action = drop;
    }

    direct_counter(CounterType.packets_and_bytes) l2_firewall_counter;
    table l2_firewall {
	    key = {
	        hdr.ethernet.dst_addr: exact;
	    }
	    actions = {
	        NoAction;
	    }
    	counters = l2_firewall_counter;
    }

    action set_next_hop(mac_addr_t next_hop) {
	    hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
	    hdr.ethernet.dst_addr = next_hop;
	    hdr.ipv6.hop_limit = hdr.ipv6.hop_limit - 1;
    }

    //K-Shortest Path Routing Table
    direct_counter(CounterType.packets_and_bytes) routing_v6_kShort_counter;
    table routing_v6_kShort {            
	    key = {
	        hdr.ipv6.dst_addr: lpm;
        }
        actions = {
	        set_next_hop;
        }
        counters = routing_v6_kShort_counter;
    }

    //ECMP Path Routing Table, ternary match so i can abstract the hosts to their switchs (we use a maks to match the first 64 bits of the address)
    //action_selector(HashAlgorithm.crc16, 32w64, 32w10) ip6_ECMP_selector;
    direct_counter(CounterType.packets_and_bytes) routing_v6_ECMP_counter;
    table routing_v6_ECMP {
        key = {
            hdr.ipv6.src_addr   : ternary;
            hdr.ipv6.dst_addr   : ternary;
            hdr.ipv6.flow_label : exact;
        }
        actions = {
            set_next_hop;
        }
        counters = routing_v6_ECMP_counter;
        //implementation = ip6_ECMP_selector;
    }

    // TODO calc checksum
    action set_next_hop_v4(mac_addr_t next_hop) {
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = next_hop;
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
        local_metadata.ipv4_update = true;
    }

    direct_counter(CounterType.packets_and_bytes) routing_v4_counter;
    table routing_v4 {
        key = {
            hdr.ipv4.dst_addr: lpm;
        }
        actions = {
            set_next_hop_v4;
        }
        counters = routing_v4_counter;
    }

    /*
     * NDP reply table and actions.
     * Handles NDP router solicitation message and send router advertisement to the sender.
     */
    action ndp_ns_to_na(mac_addr_t target_mac) {
        hdr.ethernet.src_addr = target_mac;
        hdr.ethernet.dst_addr = IPV6_MCAST_01;
        bit<128> host_ipv6_tmp = hdr.ipv6.src_addr;
        hdr.ipv6.src_addr = hdr.ndp.target_addr;
        hdr.ipv6.dst_addr = host_ipv6_tmp;
        hdr.icmpv6.type = ICMP6_TYPE_NA;
        hdr.ndp.flags = NDP_FLAG_ROUTER | NDP_FLAG_OVERRIDE;
        hdr.ndp_option.setValid();
        hdr.ndp_option.type = NDP_OPT_TARGET_LL_ADDR;
        hdr.ndp_option.length = 1;
        hdr.ndp_option.value = target_mac;
        hdr.ipv6.next_header = PROTO_ICMPV6;
        standard_metadata.egress_spec = standard_metadata.ingress_port;
        local_metadata.skip_l2 = true;
    }

    direct_counter(CounterType.packets_and_bytes) ndp_reply_table_counter;
    table ndp_reply_table {
        key = {
            hdr.ndp.target_addr: exact;
        }
        actions = {
            ndp_ns_to_na;
        }
        counters = ndp_reply_table_counter;
    }

    action srv6_end() {}

    action srv6_usid_un() {
        log_msg("srv6_usid_un action");
        hdr.ipv6.dst_addr = (hdr.ipv6.dst_addr & UN_BLOCK_MASK) | ((hdr.ipv6.dst_addr << 16) & ~((bit<128>)UN_BLOCK_MASK));
    }

    action srv6_usid_ua(ipv6_addr_t next_hop) {
        log_msg("srv6_usid_ua action");
        hdr.ipv6.dst_addr = (hdr.ipv6.dst_addr & UN_BLOCK_MASK) | ((hdr.ipv6.dst_addr << 32) & ~((bit<128>)UN_BLOCK_MASK));
        local_metadata.xconnect = true;

        local_metadata.ua_next_hop = next_hop;
    }

    action srv6_end_x(ipv6_addr_t next_hop) {
        hdr.ipv6.dst_addr = (hdr.ipv6.dst_addr & UN_BLOCK_MASK) | ((hdr.ipv6.dst_addr << 32) & ~((bit<128>)UN_BLOCK_MASK));
        local_metadata.xconnect = true;

        local_metadata.ua_next_hop = next_hop;
    }

    action srv6_end_dx6() {   //no more SRv6 steps, OG packet was IPv6
        hdr.ipv6.version = hdr.ipv6_inner.version;
        hdr.ipv6.dscp = hdr.ipv6_inner.dscp;
        hdr.ipv6.ecn = hdr.ipv6_inner.ecn;
        hdr.ipv6.flow_label = hdr.ipv6_inner.flow_label;
        hdr.ipv6.payload_len = hdr.ipv6_inner.payload_len;    //restore packet size (containing INT if used, INT SORCE AND TRANSIT must keep it updated)
        hdr.ipv6.next_header = hdr.ipv6_inner.next_header;
        hdr.ipv6.hop_limit = hdr.ipv6_inner.hop_limit;
        hdr.ipv6.src_addr = hdr.ipv6_inner.src_addr;
        hdr.ipv6.dst_addr = hdr.ipv6_inner.dst_addr;

        hdr.ipv6_inner.setInvalid();
        hdr.srv6h.setInvalid();
        hdr.srv6_list[0].setInvalid();
    }

    action srv6_end_dx4()  {   //no more SRv6 steps, OG packet was IPv4
        hdr.srv6_list[0].setInvalid();
        hdr.srv6h.setInvalid();
        hdr.ipv6.setInvalid();
        hdr.ipv6_inner.setInvalid();

        hdr.ethernet.ether_type = ETHERTYPE_IPV4;
    } 

    direct_counter(CounterType.packets_and_bytes) srv6_localsid_table_counter;
    table srv6_localsid_table {
        key = {
            hdr.ipv6.dst_addr: lpm;
        }
        actions = {
            srv6_end;
            srv6_end_x;
            srv6_end_dx6;
            srv6_end_dx4;
            srv6_usid_un;
            srv6_usid_ua;
            NoAction;
        }
        default_action = NoAction;
        counters = srv6_localsid_table_counter;
    }

    action xconnect_act(mac_addr_t next_hop) {
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = next_hop;
    }

    direct_counter(CounterType.packets_and_bytes) xconnect_table_counter;
    table xconnect_table {
        key = {
            local_metadata.ua_next_hop: lpm;
        }
        actions = {
            xconnect_act;
            NoAction;
        }
        default_action = NoAction;
        counters = xconnect_table_counter;
    }

    action usid_encap_1(ipv6_addr_t src_addr, ipv6_addr_t s1) { //only one segment, so the SRH is not used (we change the IPv6 header), only the ipv6_inner to save the OG info
        hdr.ipv6_inner.setValid();

        hdr.ipv6_inner.version = 6;
        hdr.ipv6_inner.ecn = hdr.ipv6.ecn;
        hdr.ipv6_inner.dscp = hdr.ipv6.dscp;
        hdr.ipv6_inner.flow_label = hdr.ipv6.flow_label;
        hdr.ipv6_inner.payload_len = hdr.ipv6.payload_len;
        hdr.ipv6_inner.next_header = hdr.ipv6.next_header;
        hdr.ipv6_inner.hop_limit = hdr.ipv6.hop_limit;
        hdr.ipv6_inner.src_addr = hdr.ipv6.src_addr;
        hdr.ipv6_inner.dst_addr = hdr.ipv6.dst_addr;

        hdr.ipv6.payload_len = hdr.ipv6.payload_len + 40;
        hdr.ipv6.next_header = PROTO_IPV6;
        hdr.ipv6.src_addr = src_addr;                            //uN of the current device
        hdr.ipv6.dst_addr = s1;
    }

    action usid_encap_2(ipv6_addr_t src_addr, ipv6_addr_t s1, ipv6_addr_t s2) { //two segments, so the SRH is used to store future ones
        hdr.ipv6_inner.setValid();

        hdr.ipv6_inner.version = 6;
        hdr.ipv6_inner.ecn = hdr.ipv6.ecn;
        hdr.ipv6_inner.dscp = hdr.ipv6.dscp;
        hdr.ipv6_inner.flow_label = hdr.ipv6.flow_label;
        hdr.ipv6_inner.payload_len = hdr.ipv6.payload_len;
        hdr.ipv6_inner.next_header = hdr.ipv6.next_header;
        hdr.ipv6_inner.hop_limit = hdr.ipv6.hop_limit;
        hdr.ipv6_inner.src_addr = hdr.ipv6.src_addr;
        hdr.ipv6_inner.dst_addr = hdr.ipv6.dst_addr;

        hdr.ipv6.payload_len = hdr.ipv6.payload_len + 40 + 24;
        hdr.ipv6.next_header = PROTO_SRV6;
        hdr.ipv6.src_addr = src_addr;                            //uN of the current device
        hdr.ipv6.dst_addr = s1;

        hdr.srv6h.setValid();
        hdr.srv6h.next_header = PROTO_IPV6;         //change to what ipv6 used to point as next
        hdr.srv6h.hdr_ext_len = 0x2;
        hdr.srv6h.routing_type = 0x4;
        hdr.srv6h.segment_left = 0;
        hdr.srv6h.last_entry = 0;
        hdr.srv6h.flags = 0;
        hdr.srv6h.tag = 0;

        hdr.srv6_list[0].setValid();
        hdr.srv6_list[0].segment_id = s2;
    }

    direct_counter(CounterType.packets_and_bytes) srv6_encap_table_counter;
    table srv6_encap {      //when the OG packet is IPv6
        key = {
           hdr.ipv6.dst_addr: lpm;       
        }
        actions = {
            usid_encap_1;
            usid_encap_2;
            NoAction;
        }
        default_action = NoAction;
        counters = srv6_encap_table_counter;
    }

    action usid_encap_1_v4(ipv6_addr_t src_addr, ipv6_addr_t s1) {
        hdr.ipv6.setValid();

        hdr.ipv6.version = 6;
        hdr.ipv6.dscp = hdr.ipv4.dscp; 
        hdr.ipv6.ecn = hdr.ipv4.ecn; 
        hash(hdr.ipv6.flow_label, 
                HashAlgorithm.crc32, 
                (bit<20>) 0, 
                { 
                    hdr.ipv4.src_addr,
                    hdr.ipv4.dst_addr,
                    local_metadata.ip_proto,
                    local_metadata.l4_src_port,
                    local_metadata.l4_dst_port
                },
                (bit<20>) 1048575);
        hdr.ipv6.payload_len = hdr.ipv4.total_len;
        hdr.ipv6.next_header = PROTO_IP_IN_IP;
        hdr.ipv6.hop_limit = hdr.ipv4.ttl;
        hdr.ipv6.src_addr = src_addr;
        hdr.ipv6.dst_addr = s1;

        hdr.ethernet.ether_type = ETHERTYPE_IPV6;
    }

    action usid_encap_2_v4(ipv6_addr_t src_addr, ipv6_addr_t s1, ipv6_addr_t s2) {
        hdr.ipv6.setValid();

        hdr.ipv6.version = 6;
        hdr.ipv6.ecn = hdr.ipv4.ecn;
        hdr.ipv6.dscp = hdr.ipv4.dscp;
        hash(hdr.ipv6.flow_label, 
                HashAlgorithm.crc32, 
                (bit<20>) 0, 
                { 
                    hdr.ipv4.src_addr,
                    hdr.ipv4.dst_addr,
                    local_metadata.ip_proto,
                    local_metadata.l4_src_port,
                    local_metadata.l4_dst_port
                },
                (bit<20>) 1048575);        
        hdr.ipv6.payload_len = hdr.ipv4.total_len + 24;
        hdr.ipv6.next_header = PROTO_SRV6;
        hdr.ipv6.hop_limit = hdr.ipv4.ttl;
        hdr.ipv6.src_addr = src_addr;
        hdr.ipv6.dst_addr = s1;

        hdr.srv6h.setValid();
        hdr.srv6h.next_header = PROTO_IP_IN_IP;   //since is encapsulating ipv4 the next one is always IPv4
        hdr.srv6h.hdr_ext_len = 0x2;
        hdr.srv6h.routing_type = 0x4;
        hdr.srv6h.segment_left = 0;
        hdr.srv6h.last_entry = 0;
        hdr.srv6h.flags = 0;
        hdr.srv6h.tag = 0;

        hdr.srv6_list[0].setValid();
        hdr.srv6_list[0].segment_id = s2;

        hdr.ethernet.ether_type = ETHERTYPE_IPV6;
    }

    // create one group 
    action_selector(HashAlgorithm.crc16, 32w64, 32w10) ecmp_selector;
    direct_counter(CounterType.packets_and_bytes) srv6_encap_v4_table_counter;
    table srv6_encap_v4 {          //when the OG packet is IPv4
        key = {
            hdr.ipv4.dscp: exact;
            hdr.ipv4.dst_addr: lpm;

            hdr.ipv4.src_addr: selector;
            hdr.ipv4.dst_addr: selector;
            local_metadata.ip_proto: selector;
            local_metadata.l4_src_port: selector;
            local_metadata.l4_dst_port: selector;
        }
        actions = {
            usid_encap_1_v4;
            usid_encap_2_v4;
            NoAction;
        }
        default_action = NoAction;
        implementation = ecmp_selector;
        counters = srv6_encap_v4_table_counter;
    }

    action set_priority_value(bit<3> value) {
        standard_metadata.priority = value;
    }
    // compare the DSCP value to set the packet priority between 0-7 
    table set_priority_from_dscp{
        key = {
            local_metadata.OG_dscp: exact;
        }
        actions = {
            set_priority_value;
            NoAction;
        }
    }


    /*
     * ACL table  and actions.
     * Clone the packet to the CPU (PacketIn) or drop.
     */
    action clone_to_cpu() {
        local_metadata.perserv_CPU_meta.ingress_port = standard_metadata.ingress_port;
        local_metadata.perserv_CPU_meta.egress_port = CPU_PORT;                         //the packet only gets the egress right before egress, so we use CPU_PORT value
        local_metadata.perserv_CPU_meta.to_CPU = true;
        clone_preserving_field_list(CloneType.I2E, CPU_CLONE_SESSION_ID, CLONE_FL_clone3);
    }

    direct_counter(CounterType.packets_and_bytes) acl_counter;
    table acl {
        key = {
            standard_metadata.ingress_port: ternary;
            hdr.ethernet.dst_addr: ternary;
            hdr.ethernet.src_addr: ternary;
            hdr.ethernet.ether_type: ternary;
            local_metadata.ip_proto: ternary;
            local_metadata.icmp_type: ternary;
            local_metadata.l4_src_port: ternary;
            local_metadata.l4_dst_port: ternary;
        }
        actions = {
            clone_to_cpu;
            drop;
        }
        counters = acl_counter;
    }

    apply {
        //-----------------Set packet priority, local_metadata.OG_dscp is 0 by default which means priority 0 (best effort)
        if(hdr.intl4_shim.isValid())     {local_metadata.OG_dscp = hdr.intl4_shim.udp_ip_dscp;} //when INT is used, the OG DSCP value is in the shim header
        else if(hdr.ipv6_inner.isValid()){local_metadata.OG_dscp = hdr.ipv6_inner.dscp;}        //for SRv6 used, except encapsulation of IPv4 with just one segemnt
        else if(hdr.ipv6.isValid())      {local_metadata.OG_dscp = hdr.ipv6.dscp;}              //no SRv6 or encapsulation of IPv4 with just one segemnt
        else if(hdr.ipv4.isValid())      {local_metadata.OG_dscp = hdr.ipv4.dscp;}              //no encapsulation of IPv4 
        //the value is 0 by default (best effort)

        //TODO: it can be more efficient the IP Precedence (priority) is always the 3 leftmost bits of the DSCP value
        set_priority_from_dscp.apply();                       //set the packet priority based on the DSCP value
        if(standard_metadata.priority != 0){log_msg("Packet priority changed to:{}", {standard_metadata.priority});}

        //-----------------See if packet should be droped by it's priority and % of queue filled (current size/max size) 
        //if yes, we can just mark to drop and do exit to terminate the packet processing
        /* 
        THIS IS IMPLEMENTATION DEPENDENT, IN MININET IT IS USELESS
        if      (standard_metadata.deq_qdepth/max > 0.95 && standard_metadata.priority < 7)  {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        else if (standard_metadata.deq_qdepth/max > 0.90 && standard_metadata.priority < 6)  {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        else if (standard_metadata.deq_qdepth/max > 0.85 && standard_metadata.priority < 5)  {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        else if (standard_metadata.deq_qdepth/max > 0.80 && standard_metadata.priority < 4)  {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        else if (standard_metadata.deq_qdepth/max > 0.75 && standard_metadata.priority < 3)  {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        else if (standard_metadata.deq_qdepth/max > 0.70 && standard_metadata.priority < 2)  {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        else if (standard_metadata.deq_qdepth/max > 0.65 && standard_metadata.priority == 0) {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        else if (standard_metadata.deq_qdepth/max > 0.60 && standard_metadata.priority == 1) {mark_to_drop(); log_msg("Dropped packet with priority:{}",{standard_metadata.priority}); exit();}
        */



        if (hdr.packet_out.isValid()) {
            standard_metadata.egress_spec = hdr.packet_out.egress_port;
            hdr.packet_out.setInvalid();
            exit;
        }

        //-----------------Forwarding NDP packets
        if (hdr.icmpv6.isValid() && hdr.icmpv6.type == ICMP6_TYPE_NS) {
            ndp_reply_table.apply();
        }

	    if (hdr.ipv6.hop_limit == 0) {
	        drop();
	    }

	    if (l2_firewall.apply().hit) {                      //checks if hdr.ethernet.dst_addr is listed in the table (only contains myStationMac)
            switch(srv6_localsid_table.apply().action_run) { //uses hdr.ipv6.dst_addr to decided the action, use next segment or end SRv6
                srv6_end: {
                    // support for reduced SRH
                    if (hdr.srv6h.segment_left > 0) {
                        // set destination IP address to next segment
                        hdr.ipv6.dst_addr = local_metadata.next_srv6_sid;
                        // decrement segments left
                        hdr.srv6h.segment_left = hdr.srv6h.segment_left - 1;
                    } else {
                        // set destination IP address to next segment
                        hdr.ipv6.dst_addr = hdr.srv6_list[0].segment_id;
                    }
                }
                srv6_end_dx4: {
                    routing_v4.apply();
                }
            }
            // SRv6 Encapsulation
            if (hdr.ipv4.isValid() && !hdr.ipv6.isValid()) {
                srv6_encap_v4.apply();
            } else {
                srv6_encap.apply(); //uses hdr.ipv6.dst_addr and compares to this nodes rules to decide if it encapsulates it or not
            }

            //-----------------Forwarding by IP
            if (!local_metadata.xconnect) {       //No SRv6 ua_next_hop 
                //first we try doing ECMP routing, if it fails we do kShortestPath
                //uses hdr.ipv6.dst_addr (and others) to set hdr.ethernet.dst_addr
                if(!routing_v6_ECMP.apply().hit){
                    if(!routing_v6_kShort.apply().hit){
                        log_msg("No route found for IPv6 packet!");
                    }
                }
	        } else {                              //SRv6 ua_next_hop
                xconnect_table.apply();           //uses local_metadata.ua_next_hop to set hdr.ethernet.dst_addr
            }
        }
	    if (!local_metadata.skip_l2) {            //the egress_spec of the next hop was already defined by ndp_reply_table
            if(hdr.ethernet.ether_type == ETHERTYPE_LLDP && hdr.ethernet.dst_addr == 1652522221582){ //skip it
                log_msg("It's an LLDP multicast packet, not meant to be forwarded");
            }
            else{
                if(!unicast.apply().hit){            //uses hdr.ethernet.dst_addr to set egress_spec
                    if(hdr.ethernet.ether_type == ETHERTYPE_IPV6 || hdr.ethernet.ether_type == ETHERTYPE_LLDP){  //we only care about IPv6 broadcasts to check the table (Neighbor/Router solicitation)
                        multicast.apply();
                    }
                }
	        }
	    }

        //-----------------Decide if packet must be clone to CPU
        acl.apply();

        //-----------------INT processing portion        
        if(hdr.udp.isValid() || hdr.tcp.isValid()) {        //just track higer level connections. set if current hop is source or sink to the packet
            process_int_source_sink.apply(hdr, local_metadata, standard_metadata);
        }
        
        if (local_metadata.int_meta.source == true) {       //(source) INSERT INT INSTRUCTIONS HEADER
            log_msg("I am INT source for this packet origin, checking flow");
            hdr.intl4_shim.setInvalid(); 
            

            process_int_source.apply(hdr, local_metadata);     
            if(hdr.int_header.isValid()){
                log_msg("packet flow monitored");
            }
        }

        if (local_metadata.int_meta.sink == true && hdr.int_header.isValid()) { //(sink) and the INT header is valid
            // clone packet for Telemetry Report Collector
            log_msg("I am sink of this packet and i will clone it");
            local_metadata.perserv_meta.ingress_port = standard_metadata.ingress_port;      //prepare info for report
            local_metadata.perserv_meta.deq_qdepth = standard_metadata.deq_qdepth;
            local_metadata.perserv_meta.ingress_global_timestamp = standard_metadata.ingress_global_timestamp;

            clone_preserving_field_list(CloneType.I2E, REPORT_MIRROR_SESSION_ID, CLONE_FL_1);
        }
    }
}

#endif