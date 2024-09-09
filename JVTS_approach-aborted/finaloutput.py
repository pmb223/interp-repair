from collections import deque
import subprocess
import ast


def convert_to_state_series(nodes):
    state_to_index = {}
    indexed_states = {}
    state_series_str = []
    queue = deque()


    def make_hashable(state):
        return tuple((k, v) for k, v in sorted(state.items()) if k != "Valid Transitions")


    for node_id, node_data in nodes.items():
        for state in node_data["BDD States"]:
            state_hash = make_hashable(state)
            if state_hash not in state_to_index:
                index = len(state_to_index)
                state_to_index[state_hash] = index
                indexed_states[index] = {
                    "node_id": node_id,
                    "state_info": state,
                    "successors": set(),
                    "visited": False
                }
                queue.append((index, node_id, state))


    while queue:
        current_index, current_node_id, current_state = queue.popleft()
        if indexed_states[current_index]["visited"]:
            continue
        indexed_states[current_index]["visited"] = True


        for succ_node_id in current_state["Valid Transitions"]:
            for succ_state in nodes[succ_node_id]["BDD States"]:
                succ_state_hash = make_hashable(succ_state)
                if succ_state_hash not in state_to_index:
                    succ_index = len(state_to_index)
                    state_to_index[succ_state_hash] = succ_index
                    indexed_states[succ_index] = {
                        "node_id": succ_node_id,
                        "state_info": succ_state,
                        "successors": set(),
                        "visited": False
                    }
                else:
                    succ_index = state_to_index[succ_state_hash]
                indexed_states[current_index]["successors"].add(succ_index)
                if not indexed_states[succ_index]["visited"]:
                    queue.append((succ_index, succ_node_id, succ_state))


    for index, info in indexed_states.items():
        state = info["state_info"]
        successors = sorted(info["successors"])  
        state_str = f"State {index} (Node {info['node_id']}): {', '.join(f'{key}: {val}' for key, val in state.items() if key != 'Valid Transitions')}"


        if successors:
            state_str += f"\n\tWith successors: {', '.join(f'State {s} (Node {indexed_states[s]['node_id']})' for s in successors)}"
        else:
            state_str += "\n\tWith no successors."


        state_series_str.append(state_str)


    return state_series_str




def run_script_and_extract():
    jar_file_path = input("Enter the path to the JAR file you would like to run: ")
    result = subprocess.run(["python", "createdictionaryforgraph.py"], input=jar_file_path, capture_output=True, text=True)


    if result.returncode == 0:
        try:
            nodes = ast.literal_eval(result.stdout)
            state_series = convert_to_state_series(nodes)
            for state in state_series:
                print(state)
        except ValueError as e:
            print(f"Error parsing output: {e}")
        except SyntaxError as e:
            print(f"Syntax error in output: {e}")
    else:
        print("Error running createdictionaryforgraph.py:", result.stderr)


run_script_and_extract()
