import re
import itertools
import contextlib
import subprocess




def extract_parentheses_content(text, start_index):
    count = 0
    in_parentheses = False
    for i, char in enumerate(text[start_index:], start=start_index):
        if char == '(':
            count += 1
            in_parentheses = True
        elif char == ')':
            count -= 1
        if in_parentheses and count == 0:
            return text[start_index:i + 1]



def parse_logical_expression(expr):
    expr = expr.replace("=", "==")
    expr = re.sub(r"(\w+)==([a-zA-Z0-9]+)", r"\1=='\2'", expr)


    expr = transform_implications(expr)


    parts = re.split(" and ", expr)
    wrapped_parts = []
    for part in parts:
        if part.startswith("~"):  
            wrapped_parts.append(part)
        else:
            wrapped_parts.append(f"({part})")


    parsed_expr = " and ".join(wrapped_parts)
    print("Parsed Expression:", parsed_expr)


    parsed_expr = parsed_expr.replace("~(", "not (")


    return parsed_expr




def transform_implications(expr):
    stack = []
    i = 0
    while i < len(expr):
        if expr[i] == '(':
            stack.append(i)
        elif expr[i] == ')' and stack:
            start = stack.pop()
            sub_expr = expr[start + 1:i]
            if '->' in sub_expr:
                left_part, right_part = sub_expr.split('->', 1)
                replaced = f"(~({left_part.strip()}) or ({right_part.strip()}))"
                expr = expr[:start] + replaced + expr[i + 1:]
                i = start + len(replaced) - 1
        i += 1
    return expr


def parse_conditions(condition_text):
    """ Extract variable conditions from a string more robustly. """
    conditions = {}
    parts = condition_text.split('and')
    for part in parts:
        part = part.strip()
        if '=' in part:
            key_value = part.split('=', 1)  
            if len(key_value) == 2:
                key, value = key_value
                key = key.strip()
                value = value.strip()
                if value.lower() in ['true', 'false']:
                    conditions[key] = value.lower() == 'true'
                else:
                    conditions[key] = value  
            else:
                print(f"Warning: Unexpected format in conditions: {part}")
        elif '->' in part:
            key, value = part.split('->', 1)
            conditions[key.strip()] = eval(value.strip())  
    return conditions




def parse_transitions(trans_text):
    transitions = {}
    blocks = trans_text.split('destination node index = ')[1:]
    for block in blocks:
        lines = block.strip().split('\n')
        index = int(lines[0].strip())
        condition = ' '.join(lines[1:]).split(' = ')[1]


        condition = re.sub(r"\s*and\s+TRUE\s*", "", condition, flags=re.IGNORECASE)


        transitions[index] = condition
    return transitions




def prepare_state_for_eval(state):
    """ Convert state keys to Python-safe variable names and ensure values are correctly formatted for evaluation. """
    prepared_state = {}
    for key, value in state.items():
        new_key = key.replace('.', '_')
        if isinstance(value, str):
            prepared_state[new_key] = f"\"{value}\""
        else:
            prepared_state[new_key] = value
    return prepared_state




def validate_and_eval(condition, state):
    eval_state = {k: (str(v).lower() if isinstance(v, bool) else v) for k, v in state.items()}
    try:
        return eval(condition, {}, eval_state)
    except Exception as e:
        print(f"Exception while evaluating: {condition} - {e}")
        return False




def generate_states(variables, bdd_conditions):
    processed_conditions = parse_logical_expression(bdd_conditions)
    print("Processed Conditions:", processed_conditions)  


    all_states = [dict(zip(variables.keys(), vals)) for vals in itertools.product(*variables.values())]
    valid_states = []


    for state in all_states:
        if validate_and_eval(processed_conditions, state.copy()):
            valid_states.append(state)
            print(f"Valid state: {state}")


    print(f"Total valid states generated: {len(valid_states)}")
    return valid_states


def main(bdd_text, trans_text):
    print("Starting main function")
    variables = {
        'carA': [True, False],
        'carB': [True, False],
        'greenA': [True, False],
        'greenB': [True, False]
        # 'dockRequest': [True, False],
        # 'ready': [True, False],
        # 'docking': [True, False],
        # 'SYS_CONSTRAINT_0': ['S0', 'S1'],
        # 'ONCE_aux_1': ['true', 'false']
    }


    bdd_states = []
    transitions = {}
   
    for transition in trans_text:
        transitions.update(parse_transitions(transition))


    for bdd in bdd_text:
        bdd_states.extend(generate_states(variables, bdd))


    print(transitions)
    print("here")
    print(bdd_states)


    if not bdd_states:
        print("No valid states were generated for the BDD.")
    else:
        print(f"All valid BDD states: {bdd_states}")


    for index, trans_bdd_condition in transitions.items():
        print(f"\nDestination Node Index: {index}")
        valid_trans_states = generate_states(variables, trans_bdd_condition)


        if not valid_trans_states:
            print("No valid states were generated for this transition.")
        else:
            print(f"Valid transition states: {valid_trans_states}")


    valid_transitions = {str(state): [] for state in bdd_states}


    for index, trans_bdd_condition in transitions.items():
        print(f"\nDestination Node Index: {index}")
        valid_trans_states = generate_states(variables, trans_bdd_condition)


        if not valid_trans_states:
            print("No valid states were generated for this transition.")
        else:
            print(f"Valid transition states: {valid_trans_states}")


            for state in valid_trans_states:
                if str(state) not in valid_transitions:
                    valid_transitions[str(state)] = [index]  
                else:
                    valid_transitions[str(state)].append(index)  


    for state, transitions in valid_transitions.items():
        print(f"BDD State: {state}, Valid Transitions: {transitions}")


def extract_parentheses_content(text, start_index):
    count = 0
    in_parentheses = False
    for i, char in enumerate(text[start_index:], start=start_index):
        if char == '(':
            count += 1
            in_parentheses = True
        elif char == ')':
            count -= 1
        if in_parentheses and count == 0:
            return text[start_index:i + 1]
    return ""  




def parse(expression):
    """Parses the logical expression into a list of conditions."""
    return re.findall(r"\((.*?)\)", expression, re.DOTALL)


def extract_variables(conditions):
    """Extracts all variables, both primed and non-primed, from the conditions."""
    variables = {}
    for condition in conditions:
        parts = re.findall(r"(\w+'?=\w+)", condition)
        for part in parts:
            var, value = part.split('=')
            variables[var.strip()] = value.strip()
    return variables


def clean_expression(expression, variables):
    """Removes non-primed variables if a primed version exists and cleans up the expression."""
    updated_expression = expression
    for var in variables:
        if var.endswith("'") and var[:-1] in variables:
            non_primed_var = var[:-1]
            pattern = rf"{non_primed_var}=\w+ and "
            updated_expression = re.sub(pattern, "", updated_expression)
            updated_expression = updated_expression.replace(var, non_primed_var)
    return updated_expression


def simplify_expression(expression):
    conditions = parse(expression)
    variables = extract_variables(conditions)
    cleaned_expression = clean_expression(expression, variables)
    return cleaned_expression


# def clean_up_condition(condition):
#     condition = condition.replace("SYS_CONSTRAINT.0.pRespondsToS.state", "SYS_CONSTRAINT_0")
#     condition = re.sub(r"\s*and\s+TRUE\s*", "", condition, flags=re.IGNORECASE)
#     condition = condition.replace("  ", " ")
#     if "'" in condition:
#         condition = simplify_expression(condition)
#     return condition


def clean_up_condition(condition):
    condition = condition.replace("SYS_CONSTRAINT.0.pRespondsToS.state", "SYS_CONSTRAINT_0")
    condition = re.sub(r"\s*and\s+TRUE\s*", "", condition, flags=re.IGNORECASE)
    condition = condition.replace("  ", " ")
    expression = []
    if "'" in condition:
        primed_vars = list(re.findall(r"\b(\w+)'", condition))
        print(list(primed_vars))  # Convert to list and print
        if len(primed_vars) == 1:
            expression.append(simplify_expression(condition))
        else:
            conditions = parse(condition)
            variables = extract_variables(conditions)
            variants = remove_primed(condition, primed_vars, variables)      
            for expr in variants:
                expression.append(simplify_expression(expr))
            print(list(variants))
    else:
        expression.append(condition)
    return expression






def remove_primed(condition, primed_vars, stopping_variables):
    primed_vars = [f"{var}'" for var in primed_vars]
    stopping_variables['('] = "open bracket"
    stopping_variables[')'] = "close bracket"
    escaped_chars = [re.escape(char) for char in stopping_variables]
    stop_pattern = "|".join(escaped_chars)
    variants = []


    def remove_all_but(condition, primed_vars, keep_indices):
        modified_expr = condition
        for i, prime in enumerate(primed_vars):
            if i not in keep_indices:
                pattern = rf"{re.escape(prime)}.*?(?={stop_pattern})"
                modified_expr = re.sub(pattern, "", condition)
                modified_expr = re.sub(r"\s{2,}", " ", modified_expr)  
                modified_expr = modified_expr.strip()
        return modified_expr


    for keep_count in range(1, len(primed_vars) + 1):
        keep_indices = list(range(keep_count))
        modified_expr = remove_all_but(condition, primed_vars, keep_indices)
        variants.append(modified_expr)


    return variants


def parse_nodes_special(text):
    node_info = {}
    node_info['0'] = {}


    bdd_match = re.search(r'(BDD|bdd):\s*(\(.*?\))', text, re.DOTALL | re.IGNORECASE)
    if bdd_match:
        if 'BDD' not in node_info['0']:
            node_info['0']['BDD'] = []
            cleaned_conditions = clean_up_condition(extract_parentheses_content(text, bdd_match.start(2)))
            for condition in cleaned_conditions:
                node_info['0']['BDD'].append(condition)
            print("Current BDD data for node", '0', ":", node_info['0']['BDD'])
    return node_info


def parse_nodes(text):
    node_info = {}
    node_pattern = r'(INFO: Initial Node|Node) \(key: (\d+)\):([\s\S]*?)(?=Node \(key: |\Z)'
    for match in re.finditer(node_pattern, text):
        node_key = match.group(2)
        node_content = match.group(3).strip()


        node_info[node_key] = {}


        bdd_match = re.search(r'(BDD|bdd):\s*(\(.*?\))', node_content, re.DOTALL | re.IGNORECASE)
        if bdd_match:
            if 'BDD' not in node_info[node_key]:
                node_info[node_key]['BDD'] = []
            cleaned_conditions = clean_up_condition(extract_parentheses_content(node_content, bdd_match.start(2)))
            for condition in cleaned_conditions:
                node_info[node_key]['BDD'].append(condition)
            print("Current BDD data for node", node_key, ":", node_info[node_key]['BDD'])


        transitions_bdd_match = re.search(r'transitions bdd:\s*(\(.*?\))', node_content, re.DOTALL | re.IGNORECASE)
        if transitions_bdd_match:
            if 'Transitions BDD' not in node_info[node_key]:
                node_info[node_key]['Transitions BDD'] = []
            cleaned_conditions = clean_up_condition(extract_parentheses_content(node_content, transitions_bdd_match.start(1)))
            for condition in cleaned_conditions:
                node_info[node_key]['Transitions BDD'].append(condition)
                print("Current BDD data for node", node_key, ":", node_info[node_key]['Transitions BDD'])


        trans_matches = re.finditer(r"destination node index = (\d+).*?transBDD = \((.*?)\)\s+Invariant:", node_content, re.DOTALL)
        transitions = []
        for trans_match in trans_matches:
            index = int(trans_match.group(1))
            trans_content = extract_parentheses_content(node_content, trans_match.start(2) - 1)
            cleaned_conditions = clean_up_condition(trans_content)
            for condition in cleaned_conditions:
                transition_entry = f"destination node index = {index}\ntransBDD = {condition}"
                transitions.append(transition_entry)
        if transitions:
            node_info[node_key]['Transitions'] = transitions
    return node_info


def extract_valid_states(output):
    """Parses the output of main to extract the valid states."""
    match = re.search(r"All valid BDD states: (.*)", output)
    if match:
        states_str = match.group(1)
        return eval(states_str)  
    else:
        return []  




def run_script_and_extract():
    jar_file_path = input("Enter the path to the JAR file you would like to run: ")


    result = subprocess.run(
    ["python", "parsingthejaroutput.py"],
    input=jar_file_path,  
    capture_output=True,
    text=True,
    )
    if result.returncode == 0:
        output = result.stdout
        if "initial state can force sys to violate safety" in output:
            print("Different approach required")
            special = True
            handle_script(output, special)
        else:
            handle_script(output)
    else:
        print("error")


def handle_script(nodes_text, special=False):
    if special == True:
        nodes_data = parse_nodes_special(nodes_text)
        print(nodes_data)
    else:      
        nodes_data = parse_nodes(nodes_text)
    transitions = ""
    transbdd = ""
    for key, data in nodes_data.items():
        print(f"Node {key}:")
        if 'BDD' in data:
            print("BDD:")
            print(data['BDD'])
            bdd = data['BDD']
        if 'Transitions BDD' in data:
            print("Transitions BDD:")
            print(data['Transitions BDD'])
            transitions = data['Transitions BDD']
        if 'Transitions' in data:
            print("Transitions:")
            print(data['Transitions'])
            transbdd = data['Transitions']
        main(bdd, transbdd)
        print("\n")
        print("TRANS BDD SECTION")
        if transitions:
            main(transitions, transbdd)
        print("\n")


run_script_and_extract()
