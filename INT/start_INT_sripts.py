import subprocess
# Define the scripts to be launched
scripts = ['INT/receive/collector_influxdb.py', 'INT/visualizer/visualizer.py']

def main():
    # Launch the scripts in new terminal tabs
    for script in scripts:
        print(f"Starting {script} in a new terminal Window...")
        subprocess.Popen(['gnome-terminal', '--tab', '--', 'bash', '-c', f"python3 {script}; exec bash"])

if __name__ == "__main__":
    main()
