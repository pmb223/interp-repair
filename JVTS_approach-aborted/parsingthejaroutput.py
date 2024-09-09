import subprocess
import re


def parse_bdd_output(output):
    flag_pattern = "FINE: initial state can force sys to violate safety"
    node_pattern = re.compile(
        r"printJVGraph\s+(INFO: Initial Node \(key: 0\):.*?Transitions to:\s+no node)",
        re.DOTALL
    )


    init_node_pattern = re.compile(
        r"(FINE: START - node =.*?type = INIT_NODE.*?)(?=FINE: (START|END))",
        re.DOTALL
    )


    if flag_pattern in output:
        match = init_node_pattern.search(output)
        if match:
            matched_text = match.group(0)
            joined_output = flag_pattern + "\n" + matched_text
            return joined_output
        else:
            return flag_pattern


    match = node_pattern.search(output)
    if match:
        return match.group(1)


    return "No specified pattern found in the output."


def run_script_and_extract():
    jar_file = input("Enter the path to the JAR file you would like to run: ")


    result = subprocess.run(["java", "-jar", jar_file], capture_output=True, text=True)


    if result.returncode == 0:
        output = result.stdout
        new_output = parse_bdd_output(output)
        print(new_output)
    else:
        print("Error running JAR file:", result.stderr)


run_script_and_extract()
