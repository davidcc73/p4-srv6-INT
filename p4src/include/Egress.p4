#ifndef __EGRESS__
#define __EGRESS__

//new includes for the INT usage
#include "define.p4"
#include "int_transit.p4"
#include "int_sink.p4"


/*************************************************************************
****************  E G R E S S   P R O C E S S I N G   ******************** (TRANSIT AND SINK NODE)
*************************************************************************/

control EgressPipeImpl (inout parsed_headers_t hdr,
                        inout local_metadata_t local_metadata,
                        inout standard_metadata_t standard_metadata) {
    apply {
        //-----------------Restore packet standard_metadata from clones
        if (standard_metadata.instance_type == PKT_INSTANCE_TYPE_INGRESS_CLONE){
            if(local_metadata.perserv_CPU_meta.to_CPU == true) {
                // restore the standard_metadata values that were perserved by the clone_preserving_field_list
                standard_metadata.egress_port = local_metadata.perserv_CPU_meta.egress_port;
                standard_metadata.ingress_port = local_metadata.perserv_CPU_meta.ingress_port;
            }
            else {
                log_msg("Detected report clone");
                //-------------Restore data from the clone
                // it may not be needed to restore all this information, just for the new data and the report
                standard_metadata.ingress_port = local_metadata.perserv_meta.ingress_port;      //prepare info for report
                //standard_metadata.egress_port = local_metadata.perserv_meta.egress_port;      //we will use the REPORT_MIRROR_SESSION_ID one
                standard_metadata.deq_qdepth = local_metadata.perserv_meta.deq_qdepth;
                standard_metadata.ingress_global_timestamp = local_metadata.perserv_meta.ingress_global_timestamp;
                
                //-------------If packet contains headers used for SRv6, it must be removed
                if(hdr.ipv6.next_header == PROTO_IPV6){ //See what is after the outer IPv6 header
                    //-----Prepare data for the recirculation
                    log_msg("Seting to recirculate to remove headers used by SRv6, and terminating egress processing");
                    local_metadata.perserv_meta.egress_spec = standard_metadata.egress_port;    //store the current egress port as spec to later on set back to egress_port  
                    recirculate_preserving_field_list(CLONE_FL_1);                              //packet state is the same as at deparser's end
                    return;                                                                    //do nothing else, just recirculate  
                }    
            }
        }

        //-----------------Standard packet forwarding
        if (standard_metadata.egress_port == CPU_PORT) {
            hdr.packet_in.setValid();
            hdr.packet_in.ingress_port = standard_metadata.ingress_port;		
        }
        if (local_metadata.is_multicast == true && standard_metadata.ingress_port == standard_metadata.egress_port) {
            mark_to_drop(standard_metadata);
        }

        //-----------------INT processing portion
        if(hdr.int_header.isValid()) {
            log_msg("at egress INT header detected");

            //prepare data for report, it's meant to be report if the packet recirculated or is a clone that got here
            if(standard_metadata.instance_type == PKT_INSTANCE_TYPE_INGRESS_CLONE || local_metadata.recirculated_srv6_flag == true) {     //prepare report's info
                standard_metadata.ingress_port = local_metadata.perserv_meta.ingress_port;
                standard_metadata.egress_port = local_metadata.perserv_meta.egress_port;
                standard_metadata.deq_qdepth = local_metadata.perserv_meta.deq_qdepth;
                standard_metadata.ingress_global_timestamp = local_metadata.perserv_meta.ingress_global_timestamp;
            }
            log_msg("adding my INT stats");
            process_int_transit.apply(hdr, local_metadata, standard_metadata);   //(transit) INFO ADDED TO PACKET AT DEPARSER

            if (standard_metadata.instance_type == PKT_INSTANCE_TYPE_INGRESS_CLONE || local_metadata.recirculated_srv6_flag == true) {   //prepare report
                // create int report 
                log_msg("creating INT report");
                process_int_report.apply(hdr, local_metadata, standard_metadata);

            }else if (local_metadata.int_meta.sink == true) {
                // restore packet to original state
                log_msg("restoring packet to original state");
                process_int_sink.apply(hdr, local_metadata, standard_metadata);
            }
        }
    }
}

#endif