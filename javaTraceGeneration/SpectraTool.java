package counterstrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.checks.CouldAsmHelp;
import tau.smlab.syntech.checks.ddmin.AbstractDdmin;
import tau.smlab.syntech.cores.DdminUnrealizableVarsCore;
import tau.smlab.syntech.cores.util.RealizabilityCheck;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationException;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gameinputtrans.translator.DefaultTranslators;
import tau.smlab.syntech.gameinputtrans.translator.Translator;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.gamemodel.ModuleException;
import tau.smlab.syntech.gamemodel.PlayerModule;
import tau.smlab.syntech.gamemodel.util.SysTraceInfoBuilder;
import tau.smlab.syntech.gamemodel.util.TraceIdentifier;
import tau.smlab.syntech.games.AbstractGamesException;
import tau.smlab.syntech.games.gr1.wellseparation.WellSeparationChecker;
import tau.smlab.syntech.games.rabin.RabinGame;
import tau.smlab.syntech.games.controller.enumerate.ConcreteControllerConstruction;
import tau.smlab.syntech.games.rabin.RabinConcreteControllerConstruction;
import tau.smlab.syntech.games.rabin.RabinConcreteControllerConstructionExp;
import tau.smlab.syntech.games.rabin.RabinConcreteControllerConstructionTrace;
import tau.smlab.syntech.jtlv.BDDPackage;
import tau.smlab.syntech.jtlv.BDDPackage.BBDPackageVersion;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProviderNoIDE;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;
import tau.smlab.syntech.vacuity.SatisfiabilityCheck;
import tau.smlab.syntech.vacuity.Vacuity;
import tau.smlab.syntech.vacuity.VacuityType;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SpectraTool {

    static boolean jtlv = false;
    static boolean grouping = true;
    static boolean optimize = true;
    static boolean verbose = false;
    static SpectraInputProviderNoIDE sip = new SpectraInputProviderNoIDE();
    static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Map<String, GameData> gameDataCache = new HashMap<>();

    private static class GameData {
        GameModel gameModel;
        RabinGame rabinGame;
        TraceGenerator traceGenerator;
        List<ModuleBDDField> varsToRemove;

        GameData(GameModel gameModel, RabinGame rabinGame, TraceGenerator traceGenerator, List<ModuleBDDField> varsToRemove) {
            this.gameModel = gameModel;
            this.rabinGame = rabinGame;
            this.traceGenerator = traceGenerator;
            this.varsToRemove = varsToRemove;
        }
    }

    static {
        BDDPackage pkg = jtlv ? BDDPackage.JTLV : BDDPackage.CUDD;
        BBDPackageVersion version = jtlv ? BBDPackageVersion.DEFAULT : BBDPackageVersion.CUDD_3_0;
        BDDPackage.setCurrPackage(pkg, version);
        Env.enableReorder();
        Env.TRUE().getFactory().autoReorder(BDDFactory.REORDER_SIFT);
    }

    private static GameInput getGameInput(String spectraFilePath) {
        GameInput gi;
        try {
            gi = sip.getGameInput(spectraFilePath);
        } catch (SpectraTranslationException | ErrorsInSpectraException e) {
            System.out.println("Error: Could not prepare game input from Spectra file. Please verify that the file is a valid Spectra specification.");
            e.printStackTrace();
            return null;
        }

        try {
            List<Translator> transList = DefaultTranslators.getDefaultTranslators();
            TranslationProvider.translate(gi, transList);
        } catch (TranslationException e) {
            System.out.println("Error: Could not execute translators on Spectra file. Please verify that the file is a valid Spectra specification.");
            e.printStackTrace();
            return null;
        }
        return gi;
    }

    private static GameModel getGameModel(String spectraFilePath) {
        GameInput gameInput = getGameInput(spectraFilePath);
        return BDDGenerator.generateGameModel(
            gameInput, BDDGenerator.TraceInfo.ALL, grouping,
            optimize ? PlayerModule.TransFuncType.DECOMPOSED_FUNC : PlayerModule.TransFuncType.SINGLE_FUNC, verbose);
    }

    public static boolean checkRealizability(String spectraFilePath, int timeout) throws Exception {
        Future<Boolean> future = executor.submit(() -> Boolean.valueOf(checkRealizability(spectraFilePath)));
        return future.get(timeout, TimeUnit.SECONDS);
    }

    public static boolean checkRealizability(String spectraFilePath) {
        GameModel gameModel = getGameModel(spectraFilePath);
        RabinGame rabin = new RabinGame(gameModel);
        boolean isRealizable = !rabin.checkRealizability();
        gameModel.free();
        rabin.free();
        return isRealizable;
    }

    public static String computeUnrealizableCore(String spectraFilePath) throws IOException {
        GameModel gameModel = getGameModel(spectraFilePath);
        final SysTraceInfoBuilder builder = new SysTraceInfoBuilder(gameModel);
        List<Integer> result = new ArrayList<>();
        AbstractDdmin<Integer> ucmin = new AbstractDdmin<>() {
            public boolean check(List<Integer> part) {
                return !RealizabilityCheck.isRealizable(builder.build(part));
            }
        };

        result.addAll(ucmin.minimize(builder.getTraceList()));
        gameModel.free();
        String formattedResult = TraceIdentifier.formatLines(result);
        File tempFile = File.createTempFile("unrealizable_core_", ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.write(formattedResult);
        }
        return tempFile.getAbsolutePath();
    }

    public static String generateCounterStrategy(String spectraFilePath, int timeout, boolean minimise) throws Exception {
        Future<String> future = executor.submit(() -> generateCounterStrategy(spectraFilePath, minimise));
        return future.get(timeout, TimeUnit.SECONDS);
    }

    public static String generateCounterStrategy(String spectraFilePath, boolean minimise) throws ErrorsInSpectraException, SpectraTranslationException, AbstractGamesException {
        System.out.println("Generating counter-strategy for file path: " + spectraFilePath);

        if (!gameDataCache.containsKey(spectraFilePath)) {
            System.out.println("Cache miss for file path: " + spectraFilePath);
            List<ModuleBDDField> varsToRemove = new ArrayList<>();
            GameModel gameModel = getGameModel(spectraFilePath);

            if (minimise) {
                DdminUnrealizableVarsCore varsMinimizer = new DdminUnrealizableVarsCore(gameModel);
                List<ModuleBDDField> coreVars = varsMinimizer.minimize(gameModel.getSys().getNonAuxFields());
                if (!coreVars.isEmpty()) {
                    System.out.println("Found " + coreVars.size() + " unrealizable core system variables.");
                    for (ModuleBDDField b : coreVars) {
                        System.out.println(TraceIdentifier.getLine(b.getTraceId()) + " variable " + b.toString() + ".");
                    }
                } else {
                    System.out.println("There are no core variables.");
                }
                gameModel.free();
                gameModel = getGameModel(spectraFilePath);
                PlayerModule sys = gameModel.getSys();
                varsToRemove = new ArrayList<>(sys.getNonAuxFields());
                varsToRemove.removeAll(coreVars);
                for (ModuleBDDField var : varsToRemove) {
                    sys.setInitial(sys.initial().exist(var.support()));
                    sys.setTrans(sys.trans().exist(var.support()));
                    List<BDD> justices = new ArrayList<>();
                    for (BDD b : sys.getJustices()) {
                        justices.add(b.exist(var.support()));
                    }
                    sys.setJustice(justices);
                }
            }

            RabinGame rabinGame = new RabinGame(gameModel);

            if (!rabinGame.checkRealizability()) {
                gameModel.free();
                rabinGame.free();
                return "Error: Specification is realizable.";
            }

            TraceGenerator traceGenerator = new traceGenerator(rabinGame.getMem(), gameModel, varsToRemove);
            gameDataCache.put(spectraFilePath, new GameData(gameModel, rabinGame, traceGenerator, varsToRemove));
        } else {
            System.out.println("Cache hit for file path: " + spectraFilePath);
        }

        GameData gameData = gameDataCache.get(spectraFilePath);
        String result = gameData.traceGenerator.generateTrace();
        int initialStateCount = gameData.concreteControllerConstruction.getInitialStateCount();
        return "Counter-strategy generated with " + initialStateCount + " initial states:\n" + result;
    }

    public static boolean checkSatisfiability(String spectraFilePath) {
        GameInput gameInput = getGameInput(spectraFilePath);
        SatisfiabilityCheck sat = new SatisfiabilityCheck();
        sat.init(gameInput, VacuityType.SAT, false, Vacuity.VacuityComputation.JAVA_IMPLEMENTATION, false);
        boolean isSat = SatisfiabilityCheck.compute();
        sat.free();
        return isSat;
    }

    public static boolean checkWellSeparation(String spectraFilePath) throws ModuleException, AbstractGamesException {
        GameModel gameModel = getGameModel(spectraFilePath);
        WellSeparationChecker c = new WellSeparationChecker();
        boolean isWellSeparated = c.checkEnvWellSeparated(gameModel, WellSeparationChecker.Systems.SPEC, WellSeparationChecker.EnvSpecPart.JUSTICE, WellSeparationChecker.Positions.REACH, false);
        gameModel.free();
        return isWellSeparated;
    }

    public static boolean checkYSatisfiability(String spectraFilePath) {
        GameModel gameModel = getGameModel(spectraFilePath);
        boolean isYSat = CouldAsmHelp.couldAsmHelp(gameModel);
        gameModel.free();
        return isYSat;
    }

    public static void shutdown() {
        executor.shutdown();
    }

    public static void main(String[] args) throws Exception {
        String spectraFilePath = "./docking.spectra";
        String result = generateCounterStrategy(spectraFilePath, false);
        System.out.println(result);

        while (!result.contains("no more states to explore")) {
            result = generateCounterStrategy(spectraFilePath, false);
            System.out.println(result);
        }

        shutdown();
    }
}
