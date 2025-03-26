package it.unive.lisa.tutorial;

import it.unive.lisa.AnalysisException;
import it.unive.lisa.DefaultConfiguration;
import it.unive.lisa.LiSA;
import it.unive.lisa.analysis.heap.pointbased.FieldSensitivePointBasedHeap;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.conf.LiSAConfiguration;
import it.unive.lisa.conf.LiSAConfiguration.GraphType;
import it.unive.lisa.imp.IMPFrontend;
import it.unive.lisa.imp.ParsingException;
import it.unive.lisa.interprocedural.ReturnTopPolicy;
import it.unive.lisa.program.Program;
import org.junit.Test;

public class EqualityDomainTest {

    @Test
    public void testEqualityDomain() throws ParsingException, AnalysisException {
        // Charger le programme IMP
        Program program = IMPFrontend.processFile("inputs/equality.imp");

        // Configuration de base
        LiSAConfiguration conf = new DefaultConfiguration();
        conf.workdir = "outputs/equality";
        conf.analysisGraphs = GraphType.HTML;

        // Définir l’état abstrait à utiliser
        conf.abstractState = DefaultConfiguration.simpleState(
                new FieldSensitivePointBasedHeap(), // heap
                new EqualityDomain(),              // value domain — nouveau !
                DefaultConfiguration.defaultTypeDomain());

        // Politique d’appels
        conf.openCallPolicy = ReturnTopPolicy.INSTANCE;

        // Exécution de l’analyse
        LiSA lisa = new LiSA(conf);
        lisa.run(program);
    }
}
