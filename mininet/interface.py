
from mininet.cli import CLI


def print_menu():
    menu = """
    ONOS CLI Command Menu:
    0. Stop Mininet
    1. Mininet CLI
    2. Make all Hosts be detetced by ONOS
    """
    print(menu)

def detect_all_hosts(net):
    #Iteratte all hosts, so their source switch send their info to ONOS, on how to forward to them
    for host in net.hosts:
        print(f"Detecting host {host.name}")

        command = f"nohup ping -c 3 -i 0.001 -w 0.001 2001:1:1::1 &"      #use as dst IP any host in the network
        host.cmd(command)

def main_menu(net, choice):

    if   choice == 0:
        print("Stopping Mininet")
        net.stop()
        return False
    elif choice == 1:
        print("To leave the Mininet CLI, type 'exit' or 'quit'")
        CLI(net)
    elif choice == 2:
        print("Make all Hosts be detetced by ONOS")
        detect_all_hosts(net)
    else:
        print("Invalid choice")
    
    return True