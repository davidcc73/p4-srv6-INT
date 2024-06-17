
package org.onosproject.srv6_usid.cli;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.srv6_usid.PacketPriorityComponent;



/**
 *  Packet Priority rules Insert Command
 *  Not used in the final version of the project, because priority can be set by reading the 3 leftmost bits of the DSCP field
 */
@Service
@Command(scope = "onos", name = "Packet_Priority-set",
        description = "Insert the table entries that will map the packets DSCP to priority levels that each device will use to process packet")
public class PacketPriorityInsert extends AbstractShellCommand{

    //calling the name on the cli will push all the rules at /config/DSCP-Precedence_Values.txt rules to all devices
    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        PacketPriorityComponent app = get(PacketPriorityComponent.class);
        String filename = "/config/DSCP-Precedence_Values.txt";
        int key, arg;
        String line = null;
        String[] parts;

        print("Setting Packet Priority for all devices...");
        for(int num = 1; num <= 14; num++) { //send to the 14 devices
            String uri = "device:r" + num;
            String result="placeholder";
            Device device = deviceService.getDevice(DeviceId.deviceId(uri));
            if (device == null) {
                    print("Device \"%s\" is not found", uri);
                    continue;
            }
            
            try (BufferedReader br = new BufferedReader(new FileReader(filename))) {//open the file and peer each line individually do a call
                while ((line = br.readLine()) != null) {
                    if(line.startsWith("//") || line.trim().isEmpty()){continue;}   
                    parts = line.split("\\s+"); // Split based on any amount of whitespace characters


                    if (parts[0].equals("Packet_Priority-insert")) {
                        key = Integer.parseInt(parts[1]);      //convert to hexadecimal
                        arg = Integer.parseInt(parts[2]);
                        //print("Installing rule on device %s", uri);
                        result = app.insertRule_SetDSCP_Priority(device.id(), key,  arg);

                        if(result != "Success"){print(result);}
                    } 
                    else{
                        System.out.println("Unsupported operation at file: " + filename + " operation: " + parts[0]);
                        continue;
                    }
                }
            } catch (FileNotFoundException e) {
                print("File not found %s", uri);
                print(e.getMessage());
                continue;
            } catch (IOException e) {
                print("Error acessing the existing file for %s", uri);
                e.printStackTrace(); 
                continue;
            } catch (Exception e) {
                    print("ERROR at line: %s", line);
                    e.printStackTrace();
                    continue; 
            } 
        }
    }
}