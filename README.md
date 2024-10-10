# p4-srv6-INT
This work was performed in the scope of the project MH-SDVanet: Multihomed Software Defined Vehicular Networks (reference PTDC/EEI-COM/5284/2020).<br/>


Fork of the project [netgroup/p4-srv6](https://github.com/netgroup/p4-srv6), implements SRv6 in Mininet environment where the switches use P4.

This repository aims to expand that project by adding to it: <br/>
 * `In-band Network Telemetry (INT)`, selected data flows, defined by ONOS, will generate Telemetry. <br/>
 * `Grafana Dashboard`, using the collected INT in the DB, represent it via real-time graphs. <br/>
 * `INT Visualyzer`, a Python script that reads the INT data and represents the paths currently taken by the data flows in the topology. <br/>
 * `INT Analyzer`, a Python script that reads the INT data and tries to detected overloaded switches and cause data flow detours from said switches to less congestioned ones, by only using the SRv6 operations from ONOS, and disable said detours when no longer needed. <br/>
 * `Routing Methods`, the original project used static routing, we inteed to expand ONOS so it can calculate and push new routing rules to the switches on demand, specifically using the algorithms: K shortest path and ECMP. 


# Credits<br/>

The Topology Visualizer and the Grafana Dashboards were developed by [Tiago Mustra](https://github.com/TiagoMustra)

For the base INT implementation in P4, the collecting, and storing of data, we used the code from the project [ruimmpires/P4INT_Mininet](https://github.com/ruimmpires/P4INT_Mininet).

The KShort code for path calculations done by ONOS was based on project: [bmsousa/ONOS-Framework](https://github.com/bmsousa/ONOS-Framework)

The ECMP code for path calculations done by ONOS was based on project: [ruicao93/simple-ecmp](https://github.com/ruicao93/simple-ecmp/tree/master)


# Repository structure
This repository is structured as follows: <br/>
 * `setup/` Text files with the instruction to install the many componets of the project <br/>
 * `app/` ONOS app Java implementation <br/>
 * `config/` configuration files <br/>
 * `INT/` Contains all the programs that use the INT: `Grafana`, `Analyzer`, `Visualizer`, and the Python scripts used to `send/receive` the packets that generate telemetry <br/>
 * `mininet/` Mininet scripts to emulate a topology of `stratum_bmv2` devices <br/>
 * `images/` Contains the images used on this ReadMe file. <br/>
 * `p4src/` P4 implementation <br/>
  * `Commands/` Contains files with CLI commands for testing <br/>
 * `test/` some test scripts be runned directly on the hosts of the topology <br/>
 * `utils/` utilities include dockerfile and wireshark plugin <br/>
 * `tmp/` temporary directory that contains the logs from `ONOS` and the `P4 Switches`<br/>

# Architecture

![Architecture](./images/Project_Architecture.drawio.png "Architecture")

This repository is structured as follows: <br/>
 * `Docker` Runs 2 conrtainers, one for mininet with the topology, other for ONOS controller and it's CLI. The mininet switches have a direct connection to ONOS. <br/>
 * `ONOS` is the SDN controller used, contains a CLI to access it's opeartions. <br/>
 * `Mininet`, programm used to simulate the network topology, all switches use the same P4 code, and the used interfaces are native to the System that hosts the Docker engine. <br/>
 * `InfluxDB`, is the used database to store the collected INT data, for details see [Database](#Database) section. <br/>
 * `Grafana`, is tools used to visualize, in real-time, the collected telemetry in the form of graphs, for details see [Grafana](#Grafana) section. <br/>
 * `Visualizer`, python script that reads the Database and processes the data to represent, in real-time, which paths in our topology each data flow is taking. <br/>
 * `INT Analyzer`, python script that reads the Database to detected for overloaded switches and cause path detours by using ONOS's CLI., for details see [INT Analyzer](#INT-Analyzer) section. <br/>


## Database
descrever as os meassurements

## Grafana
descrever as os graficos

## INT Analyzer
descrever o funcionamento

# Setup

# Functionalities/Implementation


# TODO
explain the packet priority system
elaborar as coisas do config, es pecialmente o INT_Tables

The new stratum image is a modification of stratrum version: 2022-06-30
built from source by modifying the Dockerfile (see file Dockerfile) adding X11, pip3 and scapy to it
image compiled with name:
davidcc73/stratum_bmv2_x11_scapy_pip3

expandi os arg ue ativam o SRv6 e o INT?





# IPv6 Segment Routing SRv6  Original README.md<br/>
SRv6 is a network architecture that encodes a list of instructions in the IPv6 packet header to define a network wide packet processing program. <br/>
Each instruction defines a node to process the packet and the behavior to be applied to that packet by that node.<br/>
The [SRv6 network programming](https://tools.ietf.org/html/draft-ietf-spring-srv6-network-programming-24) framework is being defined in IETF.<br/>

# Implementation
In the project we provide an open source data plane of SRv6 in P4. We Leverage the Open Network Operating System (ONOS) for the control plane. <br/>
We augmented ONOS implementation with the necessary extensions to support SRv6. <br/>





## Usage 
TBD 
<!--
In the section we show the steps needed to run the SRv6 micro SID demo, starting from the downloaded VM. <br/>

The demo runs on a mininet topology made up of fourteen P4 enabled switches (based on [bmv2](https://github.com/p4lang/behavioral-model) P4 software implementation) and two hosts that represent Site A and Site B. For this demo we rely on static routing for simplicity. <br/>
The Onos controller is used to configure the P4 software switches with the various table entries, e.g. SRv6 Micro SID routes, L2 forwarding entries, etc. <br/>

## DEMO commands
To ease the execution of the commands needed to setup the required software, we make use of the Makefile prepared by the ONF for their [P4 tutorial](https://github.com/opennetworkinglab/ngsdn-tutorial). <br/>

```
| Make command        | Description                                            | <br/>
|---------------------|------------------------------------------------------- | <br/>
| `make start`        | Runs ONOS and Mininet containers                       | <br/>
| `make onos-cli`     | Access the ONOS command line interface (CLI)           | <br/>
| `make app-build`    | Builds the tutorial app and pipeconf                   | <br/>
| `make app-reload`   | Load the app in ONOS                                   | <br/>
| `make mn-cli`       | Access the Mininet CLI                                 | <br/>
| `make netcfg`       | Pushes netcfg.json file (network config) to ONOS       | <br/>
| `make stop`         | Resets the tutorial environment                        | <br/>
 ```
 
## Detailed DEMO description

### 1. Start ONOS
In a terminal window, start the ONOS main process by running and connect to the logs: <br/>
```bash <br/>
$> make start <br/>
$> make onos-log <br/>
``` 
### 2. Build and load the application 
An application is provided to ONOS as an executable in .oar format. To build the source code contained in `app/` issue the following command: <br/>
```bash <br/>
$> make app-build <br/>
``` 
This will create the `srv6-uSID-1.0-SNAPSHOT.oar` application binary in the `app/target/` folder. <br/>
Moreover, it will compile the p4 code contained in `p4src` creating two output files: <br/>
- `bmv2.json` is the JSON description of the dataplane programmed in P4; <br/>
- `p4info.txt` contains the information about the southbound interface used by the controller to program the switches. <br/>
These two files are symlinked inside the `app/src/main/resources/` folder and used to build the application. <br/>
After the creation of the binary, we have to load it inside ONOS: <br/>

```bash
$> make app-reload <br/>
```
The app should now be registered in ONOS. <br/>

### 3. Push the network configuration to ONOS
ONOS gets its global network view thanks to a JSON configuration file in which it is possible to encode several information about the switch configuration. <br/>
This file is parsed at runtime by the application and it is needed to configure, e.g. the MAC addresses, SID and uSID addresses assigned to each P4 switch. <br/>
Let's push it to ONOS by prompting the following command: <br/>
```bash <br/>
$> make netcfg 
```
Now ONOS knows how to connect to the switches set up in mininet. <br/>

### 4. Insert the SRv6 micro SID routing directives
In a new window open the ONOS CLI with the following command: <br/>
```bash <br/>
$> make onos-cli <br/>
```
For the purpose of this DEMO, we statically configured the IPv6 routes of each router inside the `config/routing_tables.txt` file consisting of a list of `route-insert` commands. Also the uA Instructions are contained in the `config/ua-config.txt` in the form of a list of `uA-insert` commands. Configure them inside the switches by sourcing this file inside the CLI: <br/>
```bash 
onos-cli> source /config/routing_tables.txt
onos-cli> source /config/ua_config.txt
```
Then, we can insert the uSID routing directive to the the two end routers, one for the path H1 ===> H2 and one for the reverse path H2 ===> H1: <br/>

```bash <br/>
onos-cli> srv6-insert device:r1 fcbb:bb00:8:7:2:fd00:: 2001:1:2::1 <br/>
onos-cli> srv6-insert device:r2 fcbb:bb00:7:8:1:fd00:: 2001:1:1::1 <br/>
```
Essentially, these commands specify to the end routers (R1 and R2) to insert an SRv6 header with a list of SIDs. The first represents the list of uSID that the packet must traverse while the last is the IPv6 address of the host the packet is destined to.  <br/>
### 6. Test
Test the communication between the two hosts with ping inside mininet. <br/>
```bash <br/>
$> make mn-cli <br/>
mininet> h2 ping h1 <br/>
mininet> h1 ping h2 <br/>
```
The first pings will not work since the switch will not know how to reach the host at L2 layer. After learning on both paths it will work. <br/>
It is also possible to have a graphical representation of the running topology thanks to the ONOS web UI. Type in a browser `localhost:8181/onos/ui` and enter as user `onos` with password `rocks`. It will display the graphical representation of the topology. <br/>
Now, let's make some faster pings: 
```bash 
mininet> h1 ping h2 -i 0.1 
``` 
Then, return to the UI and press <br/>
* `h` to show the hosts <br/>
* `l` to display the nodes labels <br/>
* `a` a few times until it displays link utilization in packets per second <br/>
-->
