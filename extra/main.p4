
#include "include/header.p4"
#include "include/parser.p4"
#include "include/checksum.p4"
#include "include/Ingress.p4"
#include "include/Egress.p4"

// Netronome-specific includes
#include <core.p4>
#include <v1model.p4>
#include <netro.p4>    // Netronome specific header

// Main switch implementation
@chip_model("NFP-6xxx")
V1Switch(
    MyParser(),
    MyVerifyChecksum(),
    MyIngress(),
    MyEgress(),
    MyComputeChecksum(),
    MyDeparser()
) main;