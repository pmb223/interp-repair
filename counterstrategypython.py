import random
from path import State, Path

class CounterstrategyState:

    def __init__(self, name: str, inputs: dict, outputs: dict, successors=[], is_initial= False, is_dead = False):
        self.name = name
        self.inputs = inputs
        self.outputs = outputs
        self.influential_outputs = dict()
        self.successors = successors
        self.is_initial = is_initial
        self.is_dead = is_dead

    def add_successor(self, state_name):
        self.successors.append(state_name)
    
    def __str__(self):
        return f"State: {self.name}\n" + \
                f"Inputs: {self.inputs}\n" + \
                f"Outputs: {self.outputs}\n" + \
                f"Influential outputs: {self.influential_outputs}\n" + \
                f"Successors: {self.successors}\n" + \
                f"Initial: {self.is_initial}\n" + \
                f"Dead: {self.is_dead}"


class Counterstrategy:

    def __init__(self, states = dict(), use_influential=True):
        self.states = states
        self.use_influential = use_influential

        if self.use_influential:
            for state in self.states.values():
                self.compute_influentials(state)
        self.num_states = len(self.states)

    def __str__(self):
        state_strs = [str(state) + "\n" for state in self.states.values()]
        return "\n".join(state_strs)

    def add_state(self, state):
        self.states[state.name] = state

    def get_state(self, name):
        return self.states.get(name)

    def compute_influentials(self, state: CounterstrategyState):
        successors = [succ for succ in state.successors if "Sf" not in succ]

        different_out_vars = []
        for out_var in state.outputs:
            can_be_1, can_be_0 = False, False
            for succ in successors:
                if self.states[succ].outputs[out_var] is True:
                    can_be_1 = True
                if self.states[succ].outputs[out_var] is False:
                    can_be_0 = True
                if can_be_1 and can_be_0:
                    different_out_vars.append(out_var)
                    break

        for succ in successors:
            for out_var in different_out_vars:
                self.states[succ].influential_outputs[out_var] = self.states[succ].outputs[out_var]

    def getValuation(self, state):
        literals = []
        for varname in state.inputs:
            if state.inputs[varname] is True:
                literals.append(varname)
            else:
                literals.append("!"+varname)
        if self.use_influential:
            outputs = state.influential_outputs
        else:
            outputs = state.outputs
        for varname in outputs:
            if state.outputs[varname] is True:
                literals.append(varname)
            else:
                literals.append("!"+varname)
        return literals    

    def extractRandomPath(self):
        """Extracts randomly a path from the counterstrategy"""

        # Perform a random walk from the initial state until hitting a failing state (no successors)
        # or completing a loop (which happens when visiting a state already visited in the walk)
        visited_states = []
        looping = False
        loop_startindex = None

        successors = [state_name for state_name in self.states if self.states[state_name].is_initial and not "Sf" in state_name]

        # # Check if the initial state is a dead state with no successors
        # if len(successors) == 1:
        #     initial_state_name = successors[0]
        #     initial_state = self.states[initial_state_name]  # Retrieve the actual CounterstrategyState object
        #     if not initial_state.successors:  # Check if there are no successors
        #         print("Initial state is dead with no successors. Creating a trivial path.")
        #         path = Path(initial_state, [])  # Creating a trivial path with the State object
        #         print("HERE")
        #         print(path)  # This will use the __str__ method of Path to print the path
        #         print("HERE")
        #         return path

        print("=== SUCCESSORS ===")
        print(successors)

        while successors != [] and not looping:
            
            curr_state = random.choice(successors)
            # curr_state = successors[0]
            print(curr_state)
            if curr_state in visited_states:
                looping = True
                loop_startindex = visited_states.index(curr_state)
                print(looping)
                print(loop_startindex)
            else:
                successors = [state_name for state_name in self.states[curr_state].successors if not "Sf" in state_name]
                visited_states.append(curr_state)
                print(visited_states)

        if visited_states == []:
            visited_states.append(random.choice([state for state in self.states if self.states[state].is_initial]))

        initial_state = State(visited_states[0])
        for var in self.getValuation(self.states[visited_states[0]]):
            initial_state.add_to_valuation(var)

        if len(visited_states)>1:
            initial_state.set_successor(visited_states[1])
            transient_states = []

            if not looping:

                # If the path is not looping, all the remaining states are transient
                for i,state in enumerate(visited_states[1:]):
                    i = i + 1 # Indices start from 1, since we iterate from the second element
                    new_state = State(state)
                    if i < len(visited_states)-1:
                        new_state.set_successor(visited_states[i+1])

                    for var in self.getValuation(self.states[state]):
                        new_state.add_to_valuation(var)

                    transient_states.append(new_state)

                # If the path is not looping, it ends in a failing state
                successors = self.states[transient_states[-1].id_state].successors
                while successors != []:
                    
                    failing_state_name = random.choice(successors)
                    failing_state = State(failing_state_name)
                
                    for var in self.getValuation(self.states[failing_state_name]):
                        failing_state.add_to_valuation(var)
                    transient_states[-1].set_successor(failing_state_name)
                    transient_states.append(failing_state)

                    successors = self.states[transient_states[-1].id_state].successors

                looping_states = None

            else:
                # In case the path is looping, the transient states go from index 1 to index loop_startindex-1
                # and the remaining ones are the looping states
                for i, state in enumerate(visited_states[1:loop_startindex]):
                    i = i + 1  # Indices start from 1, since we iterate from the second element
                    new_state = State(state)
                    if i < len(visited_states) - 1:
                        new_state.set_successor(visited_states[i + 1])

                    for var in self.getValuation(self.states[state]):
                        new_state.add_to_valuation(var)

                    transient_states.append(new_state)

                visited_states.append(visited_states[loop_startindex])
                looping_states = []
                for i,state in enumerate(visited_states[loop_startindex:-1]):
                    i = i + loop_startindex # Subarray starts at position loop_startindex
                    new_state = State(state)
                    new_state.set_successor(visited_states[i + 1])
                    for var in self.getValuation(self.states[state]):
                        new_state.add_to_valuation(var)
                    looping_states.append(new_state)

        else:

            if not looping:
                transient_states = []
                print("HERE")
                successors = self.states[initial_state.id_state].successors
                print("HERE")
                # if not successors: 
                #     print("Generating trivial path")
                #     path = Path(initial_state, [])
                #     return path
                if successors:
                    failing_state_name = random.choice(successors)
                    print("HERE")
                    initial_state.set_successor(failing_state_name)
                    failing_state = State(failing_state_name)

                    for var in self.getValuation(self.states[failing_state_name]):
                        failing_state.add_to_valuation(var)
                    transient_states.append(failing_state)
                
                    successors = self.states[transient_states[-1].id_state].successors
                while successors != []:
                    
                    failing_state_name = random.choice(successors)
                    failing_state = State(failing_state_name)
                
                    for var in self.getValuation(self.states[failing_state_name]):
                        failing_state.add_to_valuation(var)
                    transient_states[-1].set_successor(failing_state_name)
                    transient_states.append(failing_state)
                    
                    successors = self.states[transient_states[-1].id_state].successors

                looping_states = None
            
            else:
                initial_state.set_successor(initial_state.id_state)
                path = Path(initial_state, [], [initial_state])
                # path.unroll()
                return path

        # print()
        # print("=== INI ===")
        # print(self.states[initial_state.id_state])
        # print("\n=== TRANSIENT ===")
        # for state in transient_states:
        #     print(self.states[state.id_state])
        # print("\n=== LOOPING ===")
        # if looping_states is not None:
        #     for state in looping_states:
        #         print(self.states[state.id_state])

        return Path(initial_state,transient_states,looping_states)

    def extract_single_path(self):
        """
        Extracts a single path from the counterstrategy.
        The path may include transient states and a loop.
        """

        # Identify the initial state
        initial_state_name = next((name for name, state in self.states.items() if state.is_initial), None)
        if not initial_state_name:
            raise ValueError("No initial state found in the counterstrategy.")
        
        visited_states = []
        curr_state_name = initial_state_name
        loop_start_index = None
        looping = False

        while curr_state_name:
            if curr_state_name in visited_states:
                looping = True
                loop_start_index = visited_states.index(curr_state_name)
                break
            else:
                visited_states.append(curr_state_name)
                successors = self.states[curr_state_name].successors
                curr_state_name = successors[0] if successors else None

        # Construct the initial state
        initial_state = State(visited_states[0])
        for var in self.getValuation(self.states[visited_states[0]]):
            initial_state.add_to_valuation(var)

        # Initialize transient and looping states lists
        transient_states = []
        looping_states = []

        if looping:
            # All states up to and including the loop start index are part of the loop
            for i, state_name in enumerate(visited_states[:loop_start_index]):
                state = State(state_name)
                for var in self.getValuation(self.states[state_name]):
                    state.add_to_valuation(var)
                state.set_successor(visited_states[i + 1])
                transient_states.append(state)

            for i, state_name in enumerate(visited_states[loop_start_index:]):
                state = State(state_name)
                next_state_name = visited_states[loop_start_index + (i + 1) % len(visited_states[loop_start_index:])]
                state.set_successor(next_state_name)
                for var in self.getValuation(self.states[state_name]):
                    state.add_to_valuation(var)
                looping_states.append(state)

        else:
            # If no loop, all states are transient (not expected in this scenario)
            for i, state_name in enumerate(visited_states[1:], 1):
                state = State(state_name)
                for var in self.getValuation(self.states[state_name]):
                    state.add_to_valuation(var)
                if i < len(visited_states) - 1:
                    state.set_successor(visited_states[i + 1])
                transient_states.append(state)

        # Return the constructed Path object
        return Path(initial_state, transient_states, looping_states)

