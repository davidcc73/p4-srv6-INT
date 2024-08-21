import subprocess
import os

# Define the scripts to be launched
scripts = ['INT/receive/collector_influxdb.py', 'INT/visualizer/visualizer.py']

def main():
    # Launch the scripts in the background
    for script in scripts:
        script_path = os.path.abspath(script)
        print(f"Starting {script}...")
        process = subprocess.Popen(['python3', script_path], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        
        # Print any output for debugging
        stdout, stderr = process.communicate()
        if stdout:
            print(f"Output from {script}:\n{stdout.decode()}")
        if stderr:
            print(f"Error from {script}:\n{stderr.decode()}")

if __name__ == "__main__":
    main()
