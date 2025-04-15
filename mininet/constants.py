CPU_PORT = 255

network_config = {
    "INFRA_INFRA": {               # OM4 Fiber Cable, 150 m
        "bw": 100000,              # Bandwidth (Mbps)
        'max_queue': 1000,         # Assumed Maximum queue size (packets)
        "delay": "30.6ms",         # Delay
        'jitter': "5ms",           # Jitter
        'loss': 0.1,               # Loss (%)
    },
    "INFRA_VEHICULE": {            # 5G cellular towers, 10 km
        "bw": 64,                  # Bandwidth (Mbps)
        'max_queue': 1000,         # Assumed Maximum queue size (packets)
        "delay": "13ms",           # Delay
        'jitter': "2.725ms",       # Jitter
        'loss': 0.1,               # Loss (%)
    },
    "VEHICULE_VEHICULE": {         # 5G between cars, max 100 meters
        "bw": 64,                  # Bandwidth (Mbps)
        'max_queue': 800,          # Assumed Maximum queue size (packets)
        "delay": "10ms",           # Delay
        'jitter': "2ms",           # Jitter
        'loss': 0.1,               # Loss (%)
    },
    "HOST_VEHICULE": {             # Devices inside the car, 1 meter WiFi6
        "bw": 88,                  # Bandwidth (Mbps)
        'max_queue': 1000,         # Assumed Maximum queue size (packets)
        "delay": "30ms",           # Delay
        'jitter': "0.179ms",       # Jitter
        'loss': 0.5,               # Loss (%)
    }
}


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