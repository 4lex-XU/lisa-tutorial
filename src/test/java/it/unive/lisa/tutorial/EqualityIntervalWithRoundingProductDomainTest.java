package it.unive.lisa.tutorial;

import it.unive.lisa.AnalysisException;
import it.unive.lisa.DefaultConfiguration;
import it.unive.lisa.LiSA;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.conf.LiSAConfiguration;
import it.unive.lisa.imp.IMPFrontend;
import it.unive.lisa.imp.ParsingException;
import it.unive.lisa.interprocedural.ReturnTopPolicy;
import it.unive.lisa.program.Program;
import org.junit.Test;

public class EqualityIntervalWithRoundingProductDomainTest {
    @Test
    public void testEqualityIntervalWithRoundingProductDomain() throws ParsingException, AnalysisException {
        // Parse the program to get the CFG representation
        Program program = IMPFrontend.processFile("inputs/equalityIntervalWithRoundingProductDomain.imp");
        // Build a new configuration for the analysis
        LiSAConfiguration conf = new DefaultConfiguration();

        // Specify where we want files to be generated
        conf.workdir = "outputs/equalityIntervalWithRoundingProductDomain";

        // we specify the visual format of the analysis results
        conf.analysisGraphs = LiSAConfiguration.GraphType.HTML;

        // we specify the analysis that we want to execute
        conf.abstractState = DefaultConfiguration.simpleState(
            DefaultConfiguration.defaultHeapDomain(),
            new EqualityIntervalWithRoundingProductDomain(
                new ValueEnvironment<>(IntervalWithRounding.TOP),
                new EqualityDomain()
            ),
            DefaultConfiguration.defaultTypeDomain());

        conf.openCallPolicy = ReturnTopPolicy.INSTANCE;


        // we instantiate LiSA with our configuration
        LiSA lisa = new LiSA(conf);

        // finally, we tell LiSA to analyze the program
        lisa.run(program);
    }
}
