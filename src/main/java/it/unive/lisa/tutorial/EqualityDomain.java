package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.InverseSetLattice;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.nonrelational.Environment;
import it.unive.lisa.analysis.nonrelational.NonRelationalDomain;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EqualityDomain
        extends Environment<EqualityDomain, ValueExpression, EqualityDomain.Equivalence>
        implements ValueDomain<EqualityDomain> {

    public EqualityDomain() {
        super(new Equivalence(Collections.emptySet()).top());
    }

    public EqualityDomain(Equivalence lattice, Map<Identifier, Equivalence> function) {
        super(lattice, function);
    }

    @Override
    public EqualityDomain mk(Equivalence lattice, Map<Identifier, Equivalence> function) {
        return new EqualityDomain(lattice, function);
    }

    @Override
    public EqualityDomain top() {
        return new EqualityDomain(lattice.top(), null);
    }

    @Override
    public EqualityDomain bottom() {
        return new EqualityDomain(lattice.bottom(), null);
    }

    @Override
    public EqualityDomain assign(Identifier id, ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {

        EqualityDomain result = this;

        // Cas x = y : fusion des classes
        if (expression instanceof Identifier other && !other.equals(id)) {
            Equivalence eq1 = getState(id);
            Equivalence eq2 = getState(other);
            Equivalence merged = eq1.glb(eq2).add(id).add(other);
            return putState(id, merged).putState(other, merged);
        }

        // Sinon : x = <expression> constante ou arithmétique => casser l'égalité

        // On retire `id` des autres classes
        Equivalence oldEq = getState(id);
        for (Identifier var : oldEq.elements) {
            if (!var.equals(id)) {
                Equivalence varEq = getState(var);
                Set<Identifier> newSet = new HashSet<>(varEq.elements);
                newSet.remove(id);
                result = result.putState(var, new Equivalence(newSet));
            }
        }

        // On isole `id` dans une nouvelle classe
        return result.putState(id, new Equivalence(Collections.singleton(id)));
    }




    @Override
    public EqualityDomain smallStepSemantics(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }

    @Override
    public Satisfiability satisfies(ValueExpression expression, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        if (!(expression instanceof BinaryExpression be))
            return Satisfiability.UNKNOWN;

        if (!(be.getOperator() instanceof ComparisonEq))
            return Satisfiability.UNKNOWN;

        if (!(be.getLeft() instanceof Identifier left && be.getRight() instanceof Identifier right))
            return Satisfiability.UNKNOWN;

        return getState(left).elements.contains(right) ? Satisfiability.SATISFIED : Satisfiability.NOT_SATISFIED;
    }

    @Override
    public EqualityDomain assume(ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle)
            throws SemanticException {
        if (!(expression instanceof BinaryExpression be))
            return this;

        if (!(be.getOperator() instanceof ComparisonEq))
            return this;

        if (!(be.getLeft() instanceof Identifier left && be.getRight() instanceof Identifier right))
            return this;

        Equivalence eq1 = getState(left);
        Equivalence eq2 = getState(right);
        Equivalence merged = eq1.glb(eq2).add(left).add(right);
        return putState(left, merged).putState(right, merged);
    }

    public static class Equivalence
            extends InverseSetLattice<Equivalence, Identifier>
            implements NonRelationalDomain<Equivalence, ValueExpression, EqualityDomain> {

        public Equivalence(Set<Identifier> elements) {
            super(elements, elements.isEmpty());
        }

        public Equivalence(Set<Identifier> elements, boolean isTop) {
            super(elements, isTop);
        }

        @Override
        public Equivalence top() {
            return new Equivalence(Collections.emptySet(), true);
        }

        @Override
        public Equivalence bottom() {
            return new Equivalence(Collections.emptySet(), false);
        }

        @Override
        public Equivalence mk(Set<Identifier> set) {
            return new Equivalence(set);
        }

        public Equivalence add(Identifier id) {
            Set<Identifier> result = new HashSet<>(elements);
            result.add(id);
            return new Equivalence(result);
        }

        @Override
        public Equivalence eval(ValueExpression expression, EqualityDomain environment, ProgramPoint pp, SemanticOracle oracle) {
            return top();
        }

        @Override
        public Satisfiability satisfies(ValueExpression expression, EqualityDomain environment, ProgramPoint pp, SemanticOracle oracle) {
            return Satisfiability.UNKNOWN;
        }

        @Override
        public EqualityDomain assume(EqualityDomain environment, ValueExpression expression, ProgramPoint src, ProgramPoint dest, SemanticOracle oracle) {
            return environment;
        }

        @Override
        public boolean canProcess(SymbolicExpression expression, ProgramPoint pp, SemanticOracle oracle) {
            return true;
        }
    }
}
