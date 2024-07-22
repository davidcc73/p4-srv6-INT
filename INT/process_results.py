import argparse
import csv
import os
import sys
from pprint import pprint
from openpyxl import Workbook, load_workbook
from openpyxl.utils import get_column_letter
from openpyxl.styles import Font

# Define the directory path
result_directory = "results"
final_file = "final_results.xlsx"
current_directory = os.path.dirname(os.path.abspath(__file__)) 
args = None
results = {}

def parse_args():
    global args

    parser = argparse.ArgumentParser(description='process parser')
    parser.add_argument('--f', help='CSV files to be processed',
                        type=str, action="store", required=True, nargs='+')
    
    args = parser.parse_args()

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
    header = ["Flow src", "Flow dst", "Flow Label", "Is", "Nº of packets", "First Packet TimeStamp", "Nº of out of order packets", "Out of order packets"]
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
        sheet['J1'] = "Packet Loss"
        sheet['K1'] = "Packet Loss (%)"

        sheet['J1'].font = Font(bold=True)
        sheet['K1'].font = Font(bold=True)

        # Set collumn J to contain a formula to be the subtraction of values of collum E of the current pair of lines
        for row in sheet.iter_rows(min_row=2, max_row=sheet.max_row, min_col=1, max_col=3):
            #if cell from collumn A does not contain an IPv6 address, skip
            if row[0].value is None or ":" not in row[0].value:
                skip = True
                continue
            if skip:            #not in the right line of the pair
                skip = False
                continue
            #print(row)
            
            # Set the formula, pkt loss, -1 is sender, 0 is receiver
            sheet[f'J{row[0].row}'] = f'=E{row[0].row-1}-E{row[0].row}'     
            sheet[f'K{row[0].row}'] = f'=ROUND(J{row[0].row}/E{row[0].row-1}*100, 3)'

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
        sheet['L1'] = "1º Packet Delay"
        sheet['L1'].font = Font(bold=True)
        
        # Set collumn L to contain a formula to be the subtraction of values of collum F of the current pair of lines
        for row in sheet.iter_rows(min_row=2, max_row=sheet.max_row, min_col=1, max_col=3):
            #if cell from collumn A does not contain an IPv6 address, skip
            if row[0].value is None or ":" not in row[0].value:
                skip = True
                continue
            if skip:            #not in the right line of the pair
                skip = False
                continue
            #print(row)
            
            # Set the formula, pkt loss, -1 is sender, 0 is receiver
            sheet[f'L{row[0].row}'] = f'=F{row[0].row}-F{row[0].row-1}'     

            skip = True

    # Save the workbook
    workbook.save(file_path)

def set_averages():
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
        sheet[f'A{last_line}'] = "Averages"
        sheet[f'A{last_line + 1}'] = "Values"
        sheet[f'B{last_line}'] = "Out of Order Packets (Nº)"
        sheet[f'C{last_line}'] = "Packet Loss (Nº)"
        sheet[f'D{last_line}'] = "Packet Loss (%)"
        sheet[f'E{last_line}'] = "1º Packet Delay"

        sheet[f'A{last_line}'].font = Font(bold=True)
        sheet[f'A{last_line+1}'].font = Font(bold=True)
        sheet[f'B{last_line}'].font = Font(bold=True)
        sheet[f'C{last_line}'].font = Font(bold=True)
        sheet[f'D{last_line}'].font = Font(bold=True)
        sheet[f'E{last_line}'].font = Font(bold=True)

        # on the next line for each column, set the average of the column, ignore empty cells
        sheet[f'B{last_line + 1}'] = f'=ROUND(AVERAGEIF(G:G, "<>", G:G), 3)'
        sheet[f'C{last_line + 1}'] = f'=ROUND(AVERAGEIF(J:J, "<>", J:J), 3)'
        sheet[f'D{last_line + 1}'] = f'=ROUND(AVERAGEIF(K:K, "<>", K:K), 3)'
        sheet[f'E{last_line + 1}'] = f'=ROUND(AVERAGEIF(L:L, "<>", L:L), 3)'


    # Save the workbook
    workbook.save(file_path)


def configure_final_file():
    set_pkt_loss()
    set_fist_pkt_delay()
    set_averages()

def main():
    global args

    check_files_exist()

    # Read the CSV files
    for filename in args.f:
        read_csv_files(filename)
        export_results(filename)
        configure_final_file()
    
    adjust_columns_width()


if __name__ == "__main__":
    parse_args()
    main()

