import subprocess
import os

# Define the scripts to be launched
scripts = ['INT/receive/collector_influxdb.py', 'INT/visualizer/visualizer.py']

def main():
    # Verify if x-terminal-emulator is available
    if not shutil.which("x-terminal-emulator"):
        print("x-terminal-emulator is not available. Please install a terminal emulator or update the script to use your preferred terminal.")
        return
    
    # Launch the scripts in new terminal tabs
    for script in scripts:
        script_path = os.path.abspath(script)
        print(f"Starting {script} in a new terminal window...")
        subprocess.Popen(['x-terminal-emulator', '-e', f"bash -c 'python3 {script_path}; exec bash'"])

if __name__ == "__main__":
    main()
