package it.unive.lisa.tutorial;

import it.unive.lisa.AnalysisException;
import it.unive.lisa.DefaultConfiguration;
import it.unive.lisa.LiSA;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.conf.LiSAConfiguration;
import it.unive.lisa.conf.LiSAConfiguration.GraphType;
import it.unive.lisa.imp.IMPFrontend;
import it.unive.lisa.imp.ParsingException;
import it.unive.lisa.interprocedural.ReturnTopPolicy;
import it.unive.lisa.program.Program;
import org.junit.Test;

public class IntervalWithRoundingTest {

    @Test
    public void testIntervalWithRounding() throws ParsingException, AnalysisException {
        // Parse the program to get the CFG representation
        Program program = IMPFrontend.processFile("inputs/intervalWithRoundingTests.imp");

        // Build a new configuration for the analysis
        LiSAConfiguration conf = new DefaultConfiguration();

        // Specify where we want files to be generated
        conf.workdir = "outputs/intervalWithRoundingTests";

        // Specify the visual format of the analysis results
        conf.analysisGraphs = GraphType.HTML;

        // Specify the analysis we want to execute
        conf.abstractState = DefaultConfiguration.simpleState(
            DefaultConfiguration.defaultHeapDomain(),
            new ValueEnvironment<>(IntervalWithRounding.TOP),
            DefaultConfiguration.defaultTypeDomain());

        conf.openCallPolicy = ReturnTopPolicy.INSTANCE;

        // Enable serialization of results for debugging
        conf.serializeResults = true;

        // Instantiate LiSA with our configuration
        LiSA lisa = new LiSA(conf);

        // Run the analysis
        lisa.run(program);
    }
}
