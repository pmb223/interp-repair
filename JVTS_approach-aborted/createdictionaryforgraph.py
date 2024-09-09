import re
import subprocess


def parse_bdd_output(text):
    node_pattern = re.compile(r"Node (\d+):.*?(?=Node \d+:|$)", re.DOTALL)
    bdd_state_pattern = re.compile(r"BDD State: (\{.*?\}), Valid Transitions: (\[.*?\])")
   
    nodes = {}


    for node_match in node_pattern.finditer(text):
        node_id = int(node_match.group(1))
        node_text = node_match.group(0).strip()  
       
        bdd_states = []


        for bdd_match in bdd_state_pattern.finditer(node_text):
            state_text = bdd_match.group(1)
            transitions_text = bdd_match.group(2)
            state = eval(state_text)  
            state['Valid Transitions'] = eval(transitions_text)  
            bdd_states.append(state)


        nodes[node_id] = {
            "BDD States": bdd_states
        }


    return nodes


def run_script_and_extract():
    result = subprocess.run(["python", "simplifyoutputforgraph.py"], capture_output=True, text=True)
    if result.returncode == 0:
        output = result.stdout
        new_output = parse_bdd_output(output)
        print(new_output)
    else:
        print("Error running simplifyoutputforgraph.py:", result.stderr)


run_script_and_extract()


