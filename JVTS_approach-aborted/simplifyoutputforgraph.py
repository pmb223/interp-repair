import subprocess


def extract_and_group_lines(output):
    """ Groups lines containing node numbers, 'BDD State:', 'TRANS BDD SECTION', and 'Destination Node Index:' under a single node heading without repetitions. """
    grouped_lines = {}
    seen_lines = {}
    current_node = None


    for line in output.split('\n'):
        if line.startswith('Node '):
            new_node = line.split(':')[0]  
            if new_node != current_node:
                if current_node is not None:
                    print(f"{current_node}:")
                    for item in grouped_lines[current_node]:
                        print(f"  {item}")
                current_node = new_node
                grouped_lines[current_node] = []
                seen_lines[current_node] = set()  
        if 'BDD State:' in line or 'TRANS BDD SECTION' in line or "Destination Node Index: " in line:
            if current_node:
                if line not in seen_lines[current_node]:
                    seen_lines[current_node].add(line)  
                    grouped_lines[current_node].append(line)  


    if current_node is not None:
        print(f"{current_node}:")
        for item in grouped_lines[current_node]:
            print(f"  {item}")


def run_script_and_extract():
    result = subprocess.run(["python", "mainparsingscriptforgraph.py"], capture_output=True, text=True)
    if result.returncode == 0:
        output = result.stdout
        extract_and_group_lines(output)
    else:
        print("Error running mainparsingscriptforgraph.py:", result.stderr)


run_script_and_extract()
