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

#ifndef __CHECKSUM__
#define __CHECKSUM__

control ComputeChecksumImpl(inout parsed_headers_t hdr,
                            inout local_metadata_t meta)
{
    apply {
        update_checksum(hdr.ndp_n.isValid(),   //may need adjustements
            {
                hdr.ipv6.src_addr,
                hdr.ipv6.dst_addr,
                hdr.ipv6.payload_len,
                24w0,                       // 3 bytes of 0
                hdr.ipv6.next_header,
                hdr.icmpv6.type,
                hdr.icmpv6.code,
                16w0,                       // Placeholder for checksum
                hdr.ndp_n.flags,
                hdr.ndp_n.target_addr,
                hdr.ndp_option.type,
                hdr.ndp_option.length,
                hdr.ndp_option.value
            },
            hdr.icmpv6.checksum,
            HashAlgorithm.csum16
        );
        
        update_checksum(hdr.ndp_rs.isValid(),    //may need adjustements
            {
                hdr.ipv6.src_addr,
                hdr.ipv6.dst_addr,
                hdr.ipv6.payload_len,
                24w0,
                hdr.ipv6.next_header,
                hdr.icmpv6.type,
                hdr.icmpv6.code,
                16w0,
                hdr.ndp_rs.flags,
                hdr.ndp_option.type,
                hdr.ndp_option.length,
                hdr.ndp_option.value
            },
            hdr.icmpv6.checksum,
            HashAlgorithm.csum16
        );

        update_checksum(hdr.ndp_ra.isValid(),
            {
                // IPv6 Pseudo-header fields
                hdr.ipv6.src_addr,             // IPv6 source address
                hdr.ipv6.dst_addr,             // IPv6 destination address
                hdr.ipv6.payload_len,          // IPv6 payload length
                24w0,                          // 3 bytes of zero
                hdr.ipv6.next_header,          // Next Header (58 for ICMPv6)
                
                // ICMPv6 fields (header and body)
                hdr.icmpv6.type,               // ICMPv6 type RA
                hdr.icmpv6.code,               // ICMPv6 code 
                16w0,                          // checksum field placeholder (zeroed out)
                hdr.ndp_ra.cur_hop_limit,      // Router Advertisement hop limit
                hdr.ndp_ra.auto_config_flags,  // Router Advertisement auto configuration flags
                hdr.ndp_ra.router_lifetime,    // Router Advertisement lifetime
                hdr.ndp_ra.reachable_time,     // Router Advertisement reachable time
                hdr.ndp_ra.retrans_timer,      // Router Advertisement retransmission timer
                hdr.ndp_option                 // Contains 3 flags
            },
            hdr.icmpv6.checksum,               // The field to store the calculated checksum
            HashAlgorithm.csum16               // Use the 16-bit checksum algorithm
        );


        update_checksum(meta.ipv4_update, 
            {
                hdr.ipv4.version,
                hdr.ipv4.ihl,
                hdr.ipv4.dscp,
                hdr.ipv4.ecn,
                hdr.ipv4.total_len,
                hdr.ipv4.identification,
                hdr.ipv4.flags,
                hdr.ipv4.frag_offset,
                hdr.ipv4.ttl,
                hdr.ipv4.protocol,
                16w0,
                hdr.ipv4.src_addr,
                hdr.ipv4.dst_addr
            }, 
            hdr.ipv4.hdr_checksum, 
            HashAlgorithm.csum16
        );
    }
}

control VerifyChecksumImpl(inout parsed_headers_t hdr,
                           inout local_metadata_t meta)
{
    apply {}
}

#endif
