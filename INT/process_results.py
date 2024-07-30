import argparse
import csv
import os
import sys
import ast

from pprint import pprint
from influxdb import InfluxDBClient
from openpyxl import Workbook, load_workbook
from openpyxl.utils import get_column_letter
from openpyxl.styles import Font

# Define the directory path
result_directory = "results"
analyzer_directory = "analyzer"
final_file = "final_results.xlsx"
current_directory = os.path.dirname(os.path.abspath(__file__)) 

args = None
results = {}

# Define DB connection parameters
host='localhost'
dbname='int'
# Connect to the InfluxDB client
client = InfluxDBClient(host=host, database=dbname)

# SRv6 logs
log_file = "ECMP-SRv6 rules.log"

def parse_args():
    global args

    parser = argparse.ArgumentParser(description='process parser')
    parser.add_argument('--f', help='CSV files to be processed',
                        type=str, action="store", required=True, nargs='+')
    
    # 2 arguments with multiple values, the nº of elements must be the same between them and the files
    parser.add_argument('--start', help='Timestamp (RFC3339 format) of when each test started (1 peer file)',
                        type=str, action="store", required=True, nargs='+')
    parser.add_argument('--end', help='Timestamp (RFC3339 format) of when each test ended (1 peer file)',
                        type=str, action="store", required=True, nargs='+')
    
    # 2 Optional argument, that must be used simultaneously
    parser.add_argument('--SRv6_index', help='Indexs of the files that have SRv6 logs associated to them (starting from 0)',
                        type=int, action="store", required=False, nargs='+')
    parser.add_argument('--SRv6_logs', help='Names of the log files with the SRv6 rules from previous argument (must match the order)',
                        type=str, action="store", required=False, nargs='+')

    args = parser.parse_args()
    
    # Check if the number of elements start/end is the same
    if len(args.start) != len(args.end):
        parser.error("The number of elements in --start and --end must be the same")
    if len(args.start) != len(args.f):
        parser.error("The number of elements in --start and --end must be the same as the number of files")


    # Check if args pairs SRv6_index and SRv6_logs are used together
    if (args.SRv6_index and not args.SRv6_logs) or (not args.SRv6_index and args.SRv6_logs):
        parser.error("Both --SRv6_index and --SRv6_logs must be used together")

    # Check if the number of elements is the same in SRv6_index and SRv6_logs
    if args.SRv6_index and args.SRv6_logs:
        if len(args.SRv6_index) != len(args.SRv6_logs):
            parser.error("The number of elements in --SRv6_index and --SRv6_logs must be the same")

    # If index of SRv6 is given, check if it is valid
    if args.SRv6_index:
        for index in args.SRv6_index:
            if index < 0 or index >= len(args.f):
                parser.error("The SRv6_index: "+ str(index) +" is invalid. It must be between 0 and the number of files-1")

def apply_query(query):
    global client
    try:
        # Execute the query
        result = client.query(query)
    except Exception as error:
        # handle the exception
        print("An exception occurred:", error)

    

    return result

def extract_Is_values(line, iteration, flow, Is):
    line = line + [Is]
    # Is values, all in the current line
    for key, Is_value in results[iteration][flow][Is].items():
        if key == "extra":
            for extra in Is_value:
                line = line + list(extra.values())
        else:
            line = line + [Is_value]

    return line

def adjust_columns_width():
    global final_file
    #open the workbook
    dir_path = os.path.join(current_directory, result_directory)
    file_path = os.path.join(dir_path, final_file)
    workbook = load_workbook(file_path)

    # Adjust column widths to fit the text
    for sheetname in workbook.sheetnames:
        print(f"Adjusting columns width for sheet {sheetname}")
        sheet = workbook[sheetname]
        for column_cells in sheet.columns:
            length = max(len(str(cell.value)) for cell in column_cells)
            sheet.column_dimensions[get_column_letter(column_cells[0].column)].width = length + 1
    
    # Save the workbook
    workbook.save(file_path)

def get_pkt_size_dscp(flow):
    #reads the INT DB and sets the pkt size and DSCP collumns

    query = f"""
        SELECT dscp, size
        FROM flow_stats
        WHERE   src_ip = '{flow[0]}'
        AND     dst_ip = '{flow[1]}'
        AND     flow_label = '{flow[2]}'
        ORDER BY time DESC
        LIMIT 1
    """
    
    r = apply_query(query)
    
    dscp = r.raw["series"][0]["values"][0][1]
    size = r.raw["series"][0]["values"][0][2]

    #print(dscp)
    #print(size)

    return dscp, size

def read_raw_results(row):
    global results
    iteration = row[0]
    flow = (row[1], row[2], int(row[3]))
    Is = row[4]
    number_of_packets = int(row[5])
    first_packet_time = row[6]
    values_end_points = {}
    values_end_points["num_pkt"] = number_of_packets
    values_end_points["time"] = float(first_packet_time)


    if Is == "receiver":
        number_out_of_order_packets = int(row[7])
        out_of_order_packets = row[8]

        extra1 = {"num_out_of_order_pkt": number_out_of_order_packets}
        extra2 = {"out_of_order_pkt": out_of_order_packets}

        values_end_points["extra"] = [extra1, extra2]

    # Check if the iteration is already in the results dictionary
    if iteration not in results:
        dscp, pkt_size = get_pkt_size_dscp(flow)            #Get the flows info
        values_flow = {Is: values_end_points, "DSCP": dscp, "Packet Size": pkt_size}
        values_iteration = {flow: values_flow}
        results[iteration] = values_iteration
    else:
        # Check if the flow is already in the results dictionary
        if flow not in results[iteration]:
            dscp, pkt_size = get_pkt_size_dscp(flow)            #Get the flows info
            values_flow = {Is: values_end_points, "DSCP": dscp, "Packet Size": pkt_size}
            results[iteration][flow] = values_flow
        else:
            # Add currect Is to the flow
            results[iteration][flow][Is] = values_end_points

def read_csv_files(filename):
    first_row = True

    res_path = os.path.join(current_directory, result_directory) 
    file_path = os.path.join(res_path, filename)

    # Check if the directory exists
    if not os.path.isdir(res_path):
        sys.exit(1)

    #check file exists
    if not os.path.isfile(file_path):
        print(f"File {filename} not found in {file_path}")
        sys.exit(1)
        
    print(f"Reading files: {filename}")

    try:
        with open(file_path, 'r', newline='') as csvfile:
            reader = csv.reader(csvfile)
            for row in reader:
                #Skip the header, first row
                if first_row:
                    first_row = False
                    continue
                read_raw_results(row)
    except Exception as e:
        print(f"An error occurred while reading {filename}: {e}")
    print("Done reading file")
    #pprint(results)

def read_SRv6_line(line):
    global results
    #print(f"Reading SRv6 line: {line}")

    new_content = {}
    part1 = line.split(" - ")
    part2 = part1[2].split(" => ")
    operation = part2[0]
    part3 = part2[1].split(": ")
    iteration = part1[0]
    timestamp = part1[1]
    operation = part2[0]
    responsible_switch = int(part3[0])
    rule_elemets_str = line.split("{")[1]
    rule_elemets = ast.literal_eval("{"+rule_elemets_str)

    # Convert numebrs in the dictionary to integers
    rule_elemets["deviceID"] = int(rule_elemets["deviceID"])
    rule_elemets["flow_label"] = int(rule_elemets["flow_label"])
    rule_elemets["src_mask"] = int(rule_elemets["src_mask"])
    rule_elemets["dst_mask"] = int(rule_elemets["dst_mask"])
    rule_elemets["flow_label_mask"] = int(rule_elemets["flow_label_mask"])

    # Extract some values from the dictionary
    src_mask = rule_elemets["src_mask"]
    dst_mask = rule_elemets["dst_mask"]
    flow_label_mask = rule_elemets["flow_label_mask"]

    if src_mask != 128 or dst_mask != 128 or flow_label_mask != 255:
        print(f"Error in the masks in the rule: {rule_elemets}, we only work with flow specific rules")
        sys.exit(1)


    #---------------Create the new operation for the dictionary
    new_content["responsible_switch"] = responsible_switch    # Which switch the load induced the operation
    new_content["operation"] = operation
    new_content["timestamp"] = timestamp
    new_content["rule"] = rule_elemets

    #print("--------------------------------------------------------")
    #print(f"Iteration: {iteration}, Timestamp: {timestamp}, Operation: {operation}, Responsible switch: {responsible_switch}")
    #pprint(rule_elemets)
    #pprint(results)
    #pprint(new_content)


    # Add the results to the dictionary
    # At this point the iteration is already in the dictionary
    results_ite = results[iteration]
    
    # Check if the operation if "SRv6_Operations" is already in the current iteration
    if "SRv6_Operations" not in results_ite:
        results_ite["SRv6_Operations"] = [new_content]   # as a list since there can be multiple operations
    else:
        # Add the operation to the dictionary
        results_ite["SRv6_Operations"].append(new_content)

    #pprint(results)

def read_SRv6_log(file_index):
    analyzer_logs_dir = os.path.join(current_directory, analyzer_directory)
    
    #Get the position of the given file_index in args.SRv6_index
    log_index = args.SRv6_index.index(file_index)

    # Read the SRv6 logs
    log_file = args.SRv6_logs[log_index]
    log_file_path = os.path.join(analyzer_logs_dir, log_file)

    print(f"Reading SRv6 logs from file: {log_file}")

    # Read the SRv6 logs
    with open(log_file_path, 'r') as file:
        for line in file:
            read_SRv6_line(line)

def check_files_exist():
    # Check if the directory/files exist
    full_res_path = os.path.join(current_directory, result_directory) 

    if not os.path.isdir(full_res_path):  # Correct condition to check if the directory does not exist
        print(f"Directory {result_directory} does not exist.")
        sys.exit(1)

    for filename in args.f:
        file_path = os.path.join(full_res_path, filename)
        # Check if the file exists
        if not os.path.isfile(file_path):
            print(f"File {filename} not found in {file_path}")
            sys.exit(1)


    #---------------Check if the SRv6 logs exist

    full_analy_path = os.path.join(current_directory, analyzer_directory) 

    # Check if the directory exists
    if not os.path.isdir(full_analy_path):
        print(f"Directory {analyzer_directory} does not exist.")
        sys.exit(1)
    
    # Check if the log files exist
    if args.SRv6_logs is not None:
        for log_file in args.SRv6_logs:
            log_file_path = os.path.join(full_analy_path, log_file)
            #print(f"Checking SRv6 log file: {log_file_path}")

            if not os.path.isfile(log_file_path):
                print(f"File {log_file} not found in {log_file_path}")
                sys.exit(1)

def export_SRv6_rules(sheet, iteration):
    sheet.append([""])

    # Add the "SRv6 Operations" header
    header = ["SRv6 Operations"]
    new_next_row = sheet.max_row + 1
    for col_num, value in enumerate(header, 1):
        cell = sheet.cell(row=new_next_row, column=col_num, value=value)
        cell.font = Font(bold=True)
    
    # Add the detailed headers for SRv6 operations
    header = ["Timestamp", "Operation", "Responsible Switch", "Source", "Destination", "Flow Label"]
    new_next_row = sheet.max_row + 1
    for col_num, value in enumerate(header, 1):
        cell = sheet.cell(row=new_next_row, column=col_num, value=value)
        cell.font = Font(bold=True)

    # Add the SRv6 operations
    for operation in results[iteration]["SRv6_Operations"]:
        new_next_row = sheet.max_row + 1
        rule_src_IP = operation["rule"]["srcIP"]
        rule_dst_IP = operation["rule"]["dstIP"]
        rule_flow_label = operation["rule"]["flow_label"]
        line = [operation["timestamp"], operation["operation"], operation["responsible_switch"], rule_src_IP, rule_dst_IP, rule_flow_label]
        sheet.append(line)

    sheet.append([""])

def export_results(OG_file):
    global results
    # Get the sheet name from filename before (_)
    sheet_name = OG_file.split("_")[0]

    dir_path = os.path.join(current_directory, result_directory)

    # Ensure the result directory exists
    if not os.path.exists(dir_path):
        os.makedirs(dir_path)
    
    # Check if the file exists
    file_path = os.path.join(dir_path, final_file)
    if os.path.exists(file_path):
        # Load the existing workbook
        workbook = load_workbook(file_path)
        if sheet_name in workbook.sheetnames:
            sheet = workbook[sheet_name]
        else:
            sheet = workbook.create_sheet(title=sheet_name)
    else:
        # Create a new workbook and sheet
        workbook = Workbook()
        sheet = workbook.active
        sheet.title = sheet_name
    
    # Write the header
    header = ["Flow src", "Flow dst", "Flow Label", "DSCP", "Packet Size (Bytes)", "Is", "Nº of packets", "1º Packet Timestamp(seconds,microseconds)", "Nº of out of order packets", "Out of order packets"]
    for col_num, value in enumerate(header, 1):
        cell = sheet.cell(row=1, column=col_num, value=value)
        cell.font = Font(bold=True)

    # Write results, iteration by iteration
    for iteration in results:
        
        # Write key
        sheet.append([f""])
        sheet.append([None])  # This adds an empty row
        cell = sheet.cell(row=sheet.max_row, column=1)  # Get the last row and column 1
        cell.value = f"Iteration - {iteration}"
        cell.font = Font(bold=True)
        # Flow by flow
        for flow in results[iteration]:
            if flow == "SRv6_Operations":       #It is not a flow,
                continue
            keys = list(results[iteration][flow].keys())

            DSCP = results[iteration][flow]["DSCP"]
            pkt_size =  results[iteration][flow]["Packet Size"]

            # Check if both types of Is are keys in the dictionary
            if "sender" not in keys:
                print(f"Sender not found in iteration {iteration} flow {flow}")
                sys.exit(1)
            if "receiver" not in keys:
                print(f"Receiver not found in iteration {iteration} flow {flow}")
                sys.exit(1)

            # Is by Is, sender must be the 1º
            OG_line = list(flow) + [DSCP, pkt_size]
            line = extract_Is_values(OG_line, iteration, flow, "sender")
            sheet.append(line)

            OG_line = list(flow) + [DSCP, pkt_size]
            line = extract_Is_values(OG_line, iteration, flow, "receiver")
            sheet.append(line)

        # Write the SRv6 operations of this Iteration if they exist
        if "SRv6_Operations" in results[iteration]:
            export_SRv6_rules(sheet, iteration)

    # Save the workbook
    workbook.save(file_path)

def set_pkt_loss():
    # Configure each sheet
    dir_path = os.path.join(current_directory, result_directory)
    file_path = os.path.join(dir_path, final_file)
    workbook = load_workbook(file_path)

    # Set formula for each sheet
    for sheet in workbook.sheetnames:
        sheet = workbook[sheet]

        #Set new headers
        sheet['L1'] = "Packet Loss"
        sheet['M1'] = "Packet Loss (%)"

        sheet['L1'].font = Font(bold=True)
        sheet['M1'].font = Font(bold=True)

        no_formula_section = False

        # Set collumn J to contain a formula to be the subtraction of values of collum E of the current pair of lines
        for row in sheet.iter_rows(min_row=2, max_row=sheet.max_row, min_col=1, max_col=3):
            
            # Skip the SRv6 Operations section
            if row[0].value == "SRv6 Operations":
                no_formula_section = True
            elif row[0].value and row[0].value.startswith("Iteration -"):
                no_formula_section = False
            if no_formula_section:
                continue

            #if cell from collumn A does not contain an IPv6 address, skip
            if row[0].value is None or ":" not in row[0].value:
                skip = True
                continue
            if skip:            #not in the right line of the pair
                skip = False
                continue
            #print(row)
            
            # Set the formula, pkt loss, -1 is sender, 0 is receiver
            sheet[f'L{row[0].row}'] = f'=G{row[0].row-1}-G{row[0].row}'     
            sheet[f'M{row[0].row}'] = f'=ROUND((L{row[0].row}/G{row[0].row-1})*100, 3)'

            skip = True

    # Save the workbook
    workbook.save(file_path)

def set_fist_pkt_delay():
    # Configure each sheet
    dir_path = os.path.join(current_directory, result_directory)
    file_path = os.path.join(dir_path, final_file)
    workbook = load_workbook(file_path)
    
    # Set formula for each sheet
    for sheet in workbook.sheetnames:
        sheet = workbook[sheet]

        #Set new headers as bold text
        sheet['N1'] = "1º Packet Delay (miliseconds)"
        sheet['N1'].font = Font(bold=True)

        no_formula_section = False
        
        # Set collumn L to contain a formula to be the subtraction of values of collum F of the current pair of lines
        for row in sheet.iter_rows(min_row=2, max_row=sheet.max_row, min_col=1, max_col=3):

            # Skip the SRv6 Operations section
            if row[0].value == "SRv6 Operations":
                no_formula_section = True
            elif row[0].value and row[0].value.startswith("Iteration -"):
                no_formula_section = False
            if no_formula_section:
                continue

            #if cell from collumn A does not contain an IPv6 address, skip
            if row[0].value is None or ":" not in row[0].value:
                skip = True
                continue
            if skip:            #not in the right line of the pair
                skip = False
                continue
            #print(row)
            
            # Set the formula, pkt loss, -1 is sender, 0 is receiver
            sheet[f'N{row[0].row}'] = f'=ROUND((H{row[0].row}-H{row[0].row-1})*1000, 3)'     

            skip = True

    # Save the workbook
    workbook.save(file_path)

def set_caculations():
    # Configure each sheet
    dir_path = os.path.join(current_directory, result_directory)
    file_path = os.path.join(dir_path, final_file)
    workbook = load_workbook(file_path)

    # Set formula for each sheet
    for sheet in workbook.sheetnames:
        sheet = workbook[sheet]

        #Pass the last line with data, and leave 2 empty lines
        last_line = sheet.max_row + 4

        #Set new headers
        sheet[f'A{last_line}'] = "Calculations"
        sheet[f'A{last_line + 1}'] = "AVG Out of Order Packets (Nº)"
        sheet[f'A{last_line + 2}'] = "AVG Packet Loss (Nº)"
        sheet[f'A{last_line + 3}'] = "AVG Packet Loss (%)"
        sheet[f'A{last_line + 4}'] = "AVG 1º Packet Delay (miliseconds)"
        sheet[f'B{last_line}'] = "Values"

        sheet[f'A{last_line}'].font = Font(bold=True)
        sheet[f'A{last_line + 1}'].font = Font(bold=True)
        sheet[f'A{last_line + 2}'].font = Font(bold=True)
        sheet[f'A{last_line + 3}'].font = Font(bold=True)
        sheet[f'A{last_line + 4}'].font = Font(bold=True)
        sheet[f'B{last_line}'].font = Font(bold=True)

        # on the next line for each column, set the average of the column, ignore empty cells
        sheet[f'B{last_line + 1}'] = f'=ROUND(AVERAGEIF(I:I, "<>", I:I), 3)'
        sheet[f'B{last_line + 2}'] = f'=ROUND(AVERAGEIF(L:L, "<>", L:L), 3)'
        sheet[f'B{last_line + 3}'] = f'=ROUND(AVERAGEIF(M:M, "<>", M:M), 3)'
        sheet[f'B{last_line + 4}'] = f'=ROUND(AVERAGEIF(N:N, "<>", N:N), 3)'


    # Save the workbook
    workbook.save(file_path)

def get_total_count(start, end):
    query = f"""
        SELECT COUNT("latency") AS total_count
        FROM flow_stats
        WHERE time >= '{start}' AND time <= '{end}'
    """
    result = apply_query(query)
    return result.raw["series"][0]["values"][0][1]  # Extract total_count from the result

def get_switch_counts(start, end):
    query = f"""
        SELECT COUNT("latency") AS switch_count
        FROM switch_stats
        WHERE time >= '{start}' AND time <= '{end}'
        GROUP BY switch_id
    """
    result = apply_query(query)
    
    list = []
    for row in result.raw["series"]:
        #tuple pair: id, count
        list.append((int(row["tags"]["switch_id"]), row["values"][0][1]))
    #print(list)
    return list  # Extract switch counts from the result

def calculate_percentages(total_count, switch_counts):
    percentages = []
    for row in switch_counts:
        switch_id = row[0]
        switch_count = row[1]
        percentage = (switch_count / total_count) * 100
        percentages.append({'switch_id': switch_id, 'percentage': percentage})
    return percentages

def write_INT_results(file_path, workbook, sheet, AVG_flows_latency, AVG_hop_latency, percentages):
    # Write the results in the sheet
    last_line = sheet.max_row + 1

    # Set new headers
    sheet[f'A{last_line + 0}'] = "AVG Flows Latency (miliseconds)"
    sheet[f'A{last_line + 1}'] = "AVG Hop Latency (miliseconds)"
    sheet[f'A{last_line + 2}'] = "% of packets to each switch"
    sheet[f'B{last_line + 2}'] = "Switch IDs"
    sheet[f'C{last_line + 2}'] = "Percentage"

    sheet[f'A{last_line + 0}'].font = Font(bold=True)
    sheet[f'A{last_line + 1}'].font = Font(bold=True)
    sheet[f'A{last_line + 2}'].font = Font(bold=True)
    sheet[f'B{last_line + 2}'].font = Font(bold=True)
    sheet[f'C{last_line + 2}'].font = Font(bold=True)


    # Write the values
    sheet[f'B{last_line + 0}'] = AVG_flows_latency
    sheet[f'B{last_line + 1}'] = AVG_hop_latency

    # Write the percentages
    for i, row in enumerate(percentages):
        sheet[f'B{last_line + 3 + i}'] = f"Switch {row['switch_id']}"
        #limit to 3 decimal places
        sheet[f'C{last_line + 3 + i}'] = round(row['percentage'], 3)

    # Save the workbook
    workbook.save(file_path)

def set_INT_results():
    # For each sheet and respectice file, see the time interval given, get the values from the DB, and set the values in the sheet
        
    # Configure each sheet
    dir_path = os.path.join(current_directory, result_directory)
    file_path = os.path.join(dir_path, final_file)
    workbook = load_workbook(file_path)

    # Get nº each sheet
    for i, sheet in enumerate(workbook.sheetnames):
        print(f"Processing sheet {sheet}, index {i}")
        sheet = workbook[sheet]

        # Get the start and end times
        start = args.start[i]
        end = args.end[i]

        # Get the results from the DB
        # We need AVG Latency of ALL flows combined (NOT distinguishing between flows)
        query = f"""
                    SELECT MEAN("latency")
                    FROM  flow_stats
                    WHERE time >= '{start}' AND time <= '{end}'
                """
        result = apply_query(query)
        AVG_flows_latency = round(result.raw["series"][0]["values"][0][1], 3)         #miliseconds

        # We need AVG Latency for processing of ALL packets (NOT distinguishing between switches/flows) 
        query = f"""
                    SELECT MEAN("latency")
                    FROM  switch_stats
                    WHERE time >= '{start}' AND time <= '{end}'
                """
        result = apply_query(query)
        AVG_hop_latency = round(result.raw["series"][0]["values"][0][1], 3)         #miliseconds

        # % of packets that went to each individual switch (switch_id)
        total_count = get_total_count(start, end)
        switch_counts = get_switch_counts(start, end)
        percentages = calculate_percentages(total_count, switch_counts)

        #print("AVG_flows_latency: ", AVG_flows_latency)
        #print("AVG_hop_latency: ", AVG_hop_latency)
        #print("Percentages: ", percentages)

        write_INT_results(file_path, workbook, sheet, AVG_flows_latency, AVG_hop_latency, percentages)

def configure_final_file():
    set_pkt_loss()
    set_fist_pkt_delay()
    set_caculations()
    set_INT_results()

def main():
    global args, client
    
    check_files_exist()

    # Read the CSV and SRv6 files
    for file_index, filename in enumerate(args.f):
        #print(f"file_index: {file_index}, Filename: {filename}")
        read_csv_files(filename)
        if args.SRv6_index is not None and file_index in args.SRv6_index:
            #Retrive the SRv6 logs data for the current file
            read_SRv6_log(file_index)

        export_results(filename)  
    
    configure_final_file()
    adjust_columns_width()
    
    client.close()


if __name__ == "__main__":
    parse_args()
    main()

