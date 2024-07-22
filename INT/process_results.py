import argparse
import csv
import os
from pprint import pprint
import sys

# Define the directory path
result_directory = "results"
current_directory = os.path.dirname(os.path.abspath(__file__)) 
args = None
results = {}

def store_results(row):
    global results
    iteration = row[0]
    flow = (row[1], row[2], row[3])
    Is = row[4]
    number_of_packets = row[5]
    first_packet_time = row[6]          #microseconds

    if Is == "receiver":
        number_out_of_order_packets = row[7]
        out_of_order_packets = row[8]
        receiver_data = (number_out_of_order_packets, out_of_order_packets)
        values_end_points = {number_of_packets, first_packet_time, number_of_packets, receiver_data}
    else:
        values_end_points = {number_of_packets, first_packet_time, number_of_packets}


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
                store_results(row)
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

def main():
    global args

    check_files_exist()

    # Read the CSV files
    for filename in args.f:
        read_csv_files(filename)

if __name__ == "__main__":
    parse_args()
    main()

