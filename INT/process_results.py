import argparse
import csv
import os
import sys
from pprint import pprint
from openpyxl import Workbook, load_workbook
from openpyxl.utils import get_column_letter



# Define the directory path
result_directory = "results"
final_file = "final_results.xlsx"
current_directory = os.path.dirname(os.path.abspath(__file__)) 
args = None
results = {}

def read_raw_results(row):
    global results
    iteration = row[0]
    flow = (row[1], row[2], int(row[3]))
    Is = row[4]
    number_of_packets = int(row[5])
    first_packet_time = row[6]          #microseconds
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
        values_flow = {Is: values_end_points}
        values_iteration = {flow: values_flow}
        results[iteration] = values_iteration
    else:
        # Check if the flow is already in the results dictionary
        if flow not in results[iteration]:
            values_flow = {Is: values_end_points}
            results[iteration][flow] = values_flow
        else:
            # Add currect Is to the flow
            results[iteration][flow][Is] = values_end_points

def read_csv_files(filename):
    first_row = True
    print(f"Reading files: {filename}")

    full_path = os.path.join(current_directory, result_directory) 

    file_path = os.path.join(full_path, filename)
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
    pprint(results)

def parse_args():
    global args

    parser = argparse.ArgumentParser(description='process parser')
    parser.add_argument('--f', help='CSV files to be processed',
                        type=str, action="store", required=True, nargs='+')
    
    args = parser.parse_args()

def check_files_exist():
    # Check if the directory/files exist
    full_path = os.path.join(current_directory, result_directory) 

    if not os.path.isdir(full_path):  # Correct condition to check if the directory does not exist
        print(f"Directory {result_directory} does not exist.")
        sys.exit(1)

    for filename in args.f:
        file_path = os.path.join(full_path, filename)
        #print(f"Checking file: {file_path}")  # Debug print
        # Check if the file exists
        if not os.path.isfile(file_path):
            print(f"File {filename} not found in {file_path}")
            sys.exit(1)

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
    sheet.append([f"Flow src", "Flow dst", "Flow Label", "Is", "Number of packets", "First packet time", "Number of out of order packets", "Out of order packets"])
    
    # Write results, iteration by iteration
    for iteration in results:
        # Write key
        sheet.append([f""])
        sheet.append([f"Iteration: {iteration}"])

        # Flow by flow
        for flow in results[iteration]:
            line1 = list(flow)

            # Is by Is
            for Is, Is_values in results[iteration][flow].items():
                OG_line = line1 + [Is]
                line = OG_line
                
                # Is values, all in the current line
                for key, Is_value in results[iteration][flow][Is].items():
                    if key == "extra":
                        for extra in Is_value:
                            line = line + list(extra.values())
                    else:
                        line = line + [Is_value]
                
                sheet.append(line)

    # Adjust column widths to fit the text
    for column_cells in sheet.columns:
        length = max(len(str(cell.value)) for cell in column_cells)
        sheet.column_dimensions[get_column_letter(column_cells[0].column)].width = length + 1
    

    # Save the workbook
    workbook.save(file_path)

def main():
    global args

    check_files_exist()

    # Read the CSV files
    for filename in args.f:
        read_csv_files(filename)
        export_results(filename)


if __name__ == "__main__":
    parse_args()
    main()

