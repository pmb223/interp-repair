package tau.smlab.syntech.games.rabin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.lang.RuntimeException;
import java.lang.InterruptedException;
import java.util.concurrent.ExecutionException;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import net.sf.javabdd.BDD.BDDIterator;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.games.AbstractGamesException;
import tau.smlab.syntech.games.GamesStrategyException;
import tau.smlab.syntech.games.controller.enumerate.ConcreteControllerConstruction;
import tau.smlab.syntech.games.controller.enumerate.EnumStateI;
import tau.smlab.syntech.games.controller.enumerate.EnumStrategyI;
import tau.smlab.syntech.games.controller.enumerate.EnumStrategyImpl;
import tau.smlab.syntech.games.controller.enumerate.printers.SimpleTextPrinter;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import java.util.Comparator;
import java.util.Map.Entry;

public class TraceGenerator extends ConcreteControllerConstruction {

    protected RabinMemory mem;
    private Map<BDD, LazyResponseGenerator> generatorCache = new HashMap<>();
    public int counterTraceCount = 0;
    private StringBuilder globalTraceOutput = new StringBuilder();
    protected List<ModuleBDDField> varsToRemove;
    private EnumStateI[] cachedInitialStates = null;
    private BDD cachedAllIni = null;
    private BDD cachedSysDeadOrEnvWin = null;
    private boolean foundTrace = false;
    private List<BDD> previousVisited = new ArrayList<>();
    private BDD previousCurrent = null;
    private int currentIndex = -1;
    private int numberInitial = 0;
    private int numberIndexes = 0;
    private BDD auxState = null; 
 
    public TraceGenerator(RabinMemory mem, GameModel m) {
        super(mem, m);
        this.mem = mem;
        this.varsToRemove = new ArrayList<>();
    }

    public TraceGenerator(RabinMemory mem, GameModel m, List<ModuleBDDField> varsToRemove) {
        super(mem, m);
        this.mem = mem;
        this.varsToRemove = varsToRemove;
    }

    @Override
    public String generateTrace() throws AbstractGamesException {
        return this.generateTrace(false);
    }

    public String generateTrace(boolean calcLongestSimplePath) throws AbstractGamesException {
        System.out.println("extract strategy - Start");
    
        globalTraceOutput.setLength(0);
        foundTrace = false;

        if (mem.getWin() == null || mem.getWin().isFree()) {
            throw new GamesStrategyException("BDD of winning states invalid.");
        }
        EnumStrategyI aut = new EnumStrategyImpl(calcLongestSimplePath);
    
        if (cachedSysDeadOrEnvWin == null) {
            cachedSysDeadOrEnvWin = this.sys.initial().imp(this.mem.getWin());
        }

        if (cachedAllIni == null) {
            cachedAllIni = this.env.initial().id().andWith(cachedSysDeadOrEnvWin.forAll(sysModuleUnprimeVars()));
        }
        if (cachedAllIni.isZero()) {
            throw new GamesStrategyException("No environment winning states.");
        }

        if (cachedInitialStates == null) {
            Map<EnumStateI, Integer> initialStateSuccessors = new HashMap<>();
            Set<EnumStateI> initialStatesSet = new HashSet<>();
        
            BDD ini = (BDD) cachedAllIni.iterator(env.moduleUnprimeVars()).next();
            cachedAllIni.free();
            ini.andWith(sys.initial().id());

            for (BDDIterator iter = ini.iterator(allUnprime()); iter.hasNext();) {
                BDD cand = iter.nextBDD();
                int iniZ = mem.getZRank(cand);
                int iniK = 0;
                int iniX = mem.getXRank(iniZ, iniK, cand);
                RabinRankInfo iniR = new RabinRankInfo(iniZ, iniK, iniX);
                EnumStateI state = aut.getStateWithoutAdding(cand, iniR);
                initialStatesSet.add(state);
            }

            cachedInitialStates = initialStatesSet.toArray(new EnumStateI[0]);
            numberInitial = cachedInitialStates.length;
        }

        numberIndexes = numberInitial - 1;

        if (previousCurrent != null) {
            BDD current = previousCurrent;
            int iniZ = mem.getZRank(current);
            int iniK = 0;
            int iniX = mem.getXRank(iniZ, iniK, current);
            RabinRankInfo iniR = new RabinRankInfo(iniZ, iniK, iniX);
            EnumStateI currentState = aut.getStateWithoutAdding(current, iniR);
            getCounterstrategyTrace(currentState.getData(), aut);
        } else {
            System.out.println("Visited all states from " + currentIndex);
            currentIndex++;

            if (currentIndex > numberIndexes) {
                return "There are no more states to explore";
            }
            EnumStateI state = cachedInitialStates[currentIndex];
            getCounterstrategyTrace(state.getData(), aut);
        }
        return globalTraceOutput.toString();
    }

    public int getInitialStateCount() {
        return numberInitial;
    }

    private void getCounterstrategyTrace(BDD currentBDD, EnumStrategyI aut) throws AbstractGamesException {
        if (foundTrace) {
            return;
        }
        
        if (previousVisited.contains(currentBDD)) {
            System.out.println("Cycle detected!");
            System.out.println(previousVisited);
            addTrace(previousVisited, true, currentBDD);
            foundTrace = true;
            previousCurrent = previousVisited.get(previousVisited.size() - 1);
            previousVisited.remove(previousVisited.size() - 1);
            return;
        }
        
        previousVisited.add(currentBDD);

        int iniZ = mem.getZRank(currentBDD);
        int iniK = 0;
        int iniX = mem.getXRank(iniZ, iniK, currentBDD);
        RabinRankInfo iniR = new RabinRankInfo(iniZ, iniK, iniX);
        EnumStateI currentState = aut.getStateWithoutAdding(currentBDD, iniR);

        LazyResponseGenerator generator = getSysStepsToEnvChoice(currentState);
        if (generator == null) {
            System.out.println("Skipping state due to initialization timeout.");
            previousVisited.remove(currentState.getData());
            return;
        }

        if (generator.hasNoValidSuccessorsInitially()) {
            System.out.println("No successors found!");
            addTrace(previousVisited, false, null);
            foundTrace = true;
            if (previousVisited.size() == 1) {
                previousVisited.remove(previousVisited.size() - 1);
            } else {
                previousVisited.remove(previousVisited.size() - 1);
                previousCurrent = previousVisited.get(previousVisited.size() - 1);
                previousVisited.remove(previousVisited.size() - 1);
            }
            return;
        }

        Map<BDD, RabinRankInfo> next;
        while (!(next = generator.getNext()).isEmpty()) {
            for (Map.Entry<BDD, RabinRankInfo> entry : next.entrySet()) {
                BDD bdd = entry.getKey();
                RabinRankInfo rank = entry.getValue();
                if (bdd != null && rank != null) {
                    EnumStateI newState = aut.getStateWithoutAdding(bdd, rank);
                    BDD newBDD = newState.getData();
                    if (newState != null) {
                        getCounterstrategyTrace(newBDD, aut);
                        if (foundTrace) {
                            return;
                        }
                    }
                }
            }
        }

        System.out.println("EXHAUSTED SUCCESSORS FOR " + currentState.getData());

        if (previousVisited.size() == 0) {
            previousCurrent = null; 
            return; 
        }

        if (previousVisited.size() == 1) {
            previousVisited.remove(previousVisited.size() - 1);
            previousCurrent = null;
            return;
        }

        previousVisited.remove(previousVisited.size() - 1);
        BDD newCurrent = previousVisited.get(previousVisited.size() - 1);
        previousVisited.remove(previousVisited.size() - 1);
        getCounterstrategyTrace(newCurrent, aut);  
    }

    public class LazyResponseGenerator {
        private final BDD sysSuccs;
        private final RabinRankInfo rri;
        private final int kind;
        private final EnumStateI initialState;
        private final BDDIterator iterator;
        private final boolean noValidSuccessorsInitially;
        private Integer successorCount = null;

        public LazyResponseGenerator(EnumStateI initialState, RabinRankInfo rri, BDD sysSucc, int kind) {
            this.initialState = initialState;
            this.rri = rri;
            this.kind = kind;
            this.sysSuccs = sysSucc;
            iterator = sysSuccs.iterator(allUnprime());
            this.noValidSuccessorsInitially = !iterator.hasNext();
        }

        public boolean hasNoValidSuccessorsInitially() {
            return noValidSuccessorsInitially;
        }

        public int getSuccessorCount() {
            if (successorCount == null) {
                int count = 0;
                BDDIterator tempIterator = sysSuccs.iterator(allUnprime());
                while (tempIterator.hasNext()) {
                    tempIterator.nextBDD();
                    count++;
                }
                successorCount = count;
            }
            return successorCount;
        }

        public Map<BDD, RabinRankInfo> getNext() {
            if (iterator.hasNext()) {
                Map<BDD, RabinRankInfo> ret = new HashMap<>();
                BDD cand = iterator.nextBDD();
                int nextZ, nextK, nextX;
                RabinRankInfo nextR;
                nextZ = mem.getZRank(cand);
                if (nextZ > rri.get_row_count())
                    return null;
                if (nextZ < rri.get_row_count()) {
                    nextK = 0;
                    nextX = mem.getXRank(nextZ, nextK, cand);
                    nextR = new RabinRankInfo(nextZ, nextK, nextX);
                    ret.put(cand, nextR);
                    return ret;
                }
                if (kind <= 1)
                    return null;
                if (rri.get_env_goal_count() == 0) {
                    nextK = (rri.get_env_goal() + 1) % env.justiceNum();
                    nextX = mem.getXRank(nextZ, nextK, cand);
                    nextR = new RabinRankInfo(nextZ, nextK, nextX);
                    ret.put(cand, nextR);
                    return ret;
                }
                if (kind <= 2)
                    return null;
                nextK = rri.get_env_goal();
                nextX = mem.getXRank(nextZ, nextK, cand);
                if (nextX >= rri.get_env_goal_count())
                    return null;
                nextR = new RabinRankInfo(nextZ, nextK, nextX);
                ret.put(cand, nextR);
                return ret;
            }
            return Collections.emptyMap();
        }
    }

    private class StateData {
        public String stateType;
        public String stateLabel; 
        public String successorState = null; 
        public String stateData; 

        public StateData(String stateType, String state, String successorState, String stateData) {
            this.stateType = stateType;
            this.stateLabel = state;
            this.successorState = successorState; 
            this.stateData = stateData; 
        }
    }

    private void addTrace(List<BDD> visited, boolean cycle, BDD cycleBDD) throws AbstractGamesException {
        int stateIndex = 0;
        String cycleStateName = null;
        int lastIndex = visited.size() - 1;
        System.out.println(cycle);
        System.out.println(cycleBDD);
        System.out.println(visited);
        List<StateData> stateData = new ArrayList<>();

        counterTraceCount++;

        for (int i = 0; i < visited.size(); i++) {
            BDD bdd = visited.get(i);
            if (bdd != null) {
                String currentStateName = "S" + stateIndex;
                String type = null; 

                if (bdd.equals(cycleBDD) && cycleStateName == null) {
                    cycleStateName = currentStateName;
                }

                if (stateIndex == 0 && lastIndex == 0 && !cycle) {
                    type = "Initial Dead State ";
                    currentStateName = "Sf" + stateIndex; 
                } else if (stateIndex == 0) {
                    type = "Initial State ";
                } else if (stateIndex == lastIndex && !cycle) {
                    type = "Dead State ";
                    currentStateName = "Sf" + stateIndex; 
                } else {
                    type = "State ";
                }

                String bddDescription = bdd.toStringWithDomains(Env.stringer);
                StateData current = new StateData(type, currentStateName, null, bddDescription); 
                stateData.add(current);

                if (stateIndex > 0) {
                    StateData previous = stateData.get(stateIndex - 1);
                    previous.successorState = currentStateName; 
                }

                if (stateIndex == lastIndex && cycle) { 
                    current.successorState = cycleStateName; 
                }

                if (stateIndex == lastIndex && !cycle) {
                    BDD auxDead = addAuxDead(visited); 
                    if (auxDead != null) {
                        String auxType = "Dead State ";
                        int index = stateIndex + 1;
                        String name = "Sf" + index;
                        String description = auxDead.toStringWithDomains(Env.stringer);
                        StateData aux = new StateData(auxType, name, null, description);
                        stateData.add(aux);
                        StateData prev = stateData.get(index - 1);
                        prev.successorState = name;
                    }
                }

                stateIndex++;
            }
        }

        for (StateData state: stateData) {
            globalTraceOutput.append(state.stateType).append(state.stateLabel).append(" ").append(state.stateData).append("\n");
            if (state.successorState != null) {
                globalTraceOutput.append("\tWith successors : ").append(state.successorState).append("\n");
            } else {
                globalTraceOutput.append("\tWith no successors.\n");
            }
        }
    }

    private BDD addAuxDead(List<BDD> visited) throws AbstractGamesException { 
        BDD cand = visited.get(visited.size() - 1);
        int iniZ = mem.getZRank(cand);
        int iniK = 0;
        int iniX = mem.getXRank(iniZ, iniK, cand);
        RabinRankInfo iniR = new RabinRankInfo(iniZ, iniK, iniX);
        EnumStrategyI aut = new EnumStrategyImpl(false);
        EnumStateI state = aut.getStateWithoutAdding(cand, iniR);
        BDD envSuccs = env.succ(state.getData());
        BDDIterator iter = envSuccs.iterator(env.moduleUnprimeVars());
        BDD envSucc = iter.next();
        EnumStateI newSt = aut.addSuccessorState(state, envSucc, null);
        auxState = newSt.getData();
        return auxState;
    }

    private LazyResponseGenerator getSysStepsToEnvChoice(EnumStateI state) throws AbstractGamesException {
    BDD data = state.getData();
    if (generatorCache.containsKey(data)) {
        return generatorCache.get(data);
    }
    if (!(state.get_rank_info() instanceof RabinRankInfo)) {
        throw new GamesStrategyException("Cannot build Rabin automaton for Streett state");
    }
    RabinRankInfo rri = (RabinRankInfo) state.get_rank_info();

    BDD envSuccs = env.succ(state.getData());
    BDD bestSuccs = Env.FALSE();
    int bestKind = 4;
    BDD sysSuccessors = sys.succ(state.getData());

    for (BDDIterator iter = envSuccs.iterator(env.moduleUnprimeVars()); iter.hasNext();) {
        BDD envSucc = iter.next();
        BDD sysSuccs = sysSuccessors.and(envSucc);
        BDD succSysWin = sysSuccs.and(mem.getWin().not());
        if (!succSysWin.isZero()) {
            succSysWin.free();
            continue;  // Not one of the relevant environment choices
        }
        succSysWin.free();

        int kind = getBestKind(state, rri, sysSuccs);
        if (kind == -1) {
            continue;  // Not a relevant env choice - one of the sys choices leads to a higher z-rank
        }
        if (kind == 1) {
            LazyResponseGenerator ret = new LazyResponseGenerator(state, rri, sysSuccs, kind);
            generatorCache.put(state.getData(), ret);
            return ret;
        }
        if (kind < bestKind) {
            bestKind = kind;
            bestSuccs = sysSuccs;
        }
    }
    if (bestKind == -1) {
        throw new GamesStrategyException("No environment successor found from state " + state.getData().toString());
    }
    LazyResponseGenerator ret = new LazyResponseGenerator(state, rri, bestSuccs, bestKind);
    generatorCache.put(state.getData(), ret);
    return ret;
}

private LazyResponseGenerator getSysStepsToEnvChoiceWithTimeout(EnumStateI state) throws AbstractGamesException {
    long timeout = 30000;
    long startTime = System.currentTimeMillis();
    LazyResponseGenerator result = null;
    while ((System.currentTimeMillis() - startTime) < timeout) {
        try {
            result = getSysStepsToEnvChoice(state);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            throw new GamesStrategyException("An error occurred while generating successors for state: " + state.getData(), e);
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new GamesStrategyException("Thread was interrupted while waiting for getSysStepsToEnvChoice to complete");
        }
    }
    System.out.println("Timeout reached while generating successors for state: " + state.getData());
    return null;
}

private int getBestKind(EnumStateI state, RabinRankInfo rri, BDD succs) {
    int retKind = 1;
    for (BDDIterator iter = succs.iterator(allUnprime()); iter.hasNext();) {
        BDD cand = iter.nextBDD();
        int nextZ = mem.getZRank(cand);
        if (nextZ > rri.get_row_count()) {
            return -1;
        }
        if (nextZ < rri.get_row_count()) {
            continue;
        }
        if (rri.get_env_goal_count() == 0) {
            if (retKind <= 2) {
                retKind = 2;
            }
            continue;
        }
        int nextK = rri.get_env_goal();
        int nextX = mem.getXRank(nextZ, nextK, cand);
        if (nextX >= rri.get_env_goal_count()) {
            return -1;
        }
        retKind = 3;
    }
    return retKind;
}


private BDDVarSet sysModuleUnprimeVars() {
    BDDVarSet mUV = this.sys.moduleUnprimeVars();
    for (ModuleBDDField field : this.varsToRemove) {
        mUV = mUV.minus(field.support());
    }
    return mUV;
}

private BDDVarSet allUnprime() {
    return sysModuleUnprimeVars().union(this.env.moduleUnprimeVars());
}

@Override
public EnumStrategyI calculateConcreteControllerNormal() throws AbstractGamesException {
    return null;
}

@Override
public String calculateConcreteController() throws AbstractGamesException {
    return null;
}

}
