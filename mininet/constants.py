CPU_PORT = 255

BW_INFRA_INFRA = 900                 #Bandwith   (Mbps)              Glass Fiber cable, 10 km
DL_INFRA_INFRA = 2                   #Delay      (ms) 

BW_INFRA_VEHICULE = 700              #Bandwith   (Mbps)              5G cellular towers, 10 km
DL_INFRA_VEHICULE = 20               #Delay      (ms)                (10-30 ms)

BW_VEHICULE_VEHICULE = 700           #Bandwith   (Mbps)              5G between cars, max 100 meters
DL_VEHICULE_VEHICULE = 7             #Delay      (ms)                (1-10 ms)


host_IPs = {
    'h1_1': '2001:1:1::1/64',
    'h1_2': '2001:1:1::2/64',
    
    'h2_1': '2001:1:2::1/64',
    'h2_2': '2001:1:2::2/64',

    'h3_1': '2001:1:3::1/64',

    'h5_1': '2001:1:5::1/64',

    'h7_1': '2001:1:7::1/64',
    'h7_2': '2001:1:7::2/64',
    'h7_3': '2001:1:7::3/64',

    'h8_1': '2001:1:8::1/64',
    'h8_2': '2001:1:8::2/64',
    'h8_3': '2001:1:8::3/64',
    'h8_4': '2001:1:8::4/64'
}