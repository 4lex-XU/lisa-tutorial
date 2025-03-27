package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.FunctionalLattice;
import it.unive.lisa.analysis.lattices.InverseSetLattice;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Domaine relationnel qui suit les égalités entre variables (x == y).
 */
public class EqualityDomain
        extends FunctionalLattice<EqualityDomain, Identifier, EqualityDomain.EqualityGroup>
        implements ValueDomain<EqualityDomain> {

    public EqualityDomain(EqualityGroup treillis, Map<Identifier, EqualityGroup> mapping) {
        super(treillis, mapping);
    }

    public EqualityDomain(EqualityGroup treillis) {
        super(treillis);
    }

    /** Constructeur par défaut (état initial vide). */
    public EqualityDomain() {
        super(new EqualityGroup(Collections.emptySet(), true, null));
    }

    @Override
    public EqualityGroup stateOfUnknown(Identifier id) {
        return new EqualityGroup(Collections.singleton(id), true, null);
    }

    @Override
    public EqualityDomain mk(EqualityGroup treillis, Map<Identifier, EqualityGroup> mapping) {
        return new EqualityDomain(treillis, mapping);
    }

    @Override
    public EqualityDomain top() {
        return new EqualityDomain(lattice.top(), null);
    }

    @Override
    public EqualityDomain bottom() {
        return new EqualityDomain(lattice.bottom(), null);
    }

    /**
     * Gère l'affectation d'une expression à une variable (ex. x = y, x = 5, x = y + 1).
     */
    @Override
    public EqualityDomain assign(Identifier id, ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        EqualityDomain current = this.forgetIdentifier(id);

        if (expr instanceof Identifier sourceId) { // Cas : x = y
            Set<Identifier> group = new HashSet<>(current.getState(sourceId).elements);
            group.add(id);
            Object sourceValue = current.getState(sourceId).getConcreteValue();
            current = current.putState(id, new EqualityGroup(group, false, sourceValue))
                    .putState(sourceId, new EqualityGroup(group, false, sourceValue));
        } else if (expr instanceof Constant constant) { // Cas : x = 5
            Object value = constant.getValue();
            Set<Identifier> group = new HashSet<>(Collections.singleton(id));
            current = current.putState(id, new EqualityGroup(group, false, value));
        } else if (expr instanceof BinaryExpression binExpr) { // Cas : x = y + 1
            ValueExpression left = (ValueExpression) binExpr.getLeft();
            ValueExpression right = (ValueExpression) binExpr.getRight();
            Set<Identifier> group = new HashSet<>(Collections.singleton(id));
            Object op = binExpr.getOperator();

            if (left instanceof Constant && right instanceof Constant) {
                Object leftVal = ((Constant) left).getValue();
                Object rightVal = ((Constant) right).getValue();
                if (leftVal instanceof Number && rightVal instanceof Number) {
                    Number result = evaluateOperation(op, (Number) leftVal, (Number) rightVal);
                    current = current.putState(id, new EqualityGroup(group, false, result));
                } else {
                    current = current.putState(id, new EqualityGroup(group, false, null));
                }
            } else {
                current = current.putState(id, new EqualityGroup(group, false, null));
            }
        }
        return current.mergeGroups();
    }

    /** Évalue une opération binaire (ex. +, -, *, /). */
    private Number evaluateOperation(Object operator, Number left, Number right) {
        String op = operator.toString();
        return switch (op) {
            case "+" -> left.intValue() + right.intValue();
            case "-" -> left.intValue() - right.intValue();
            case "*" -> left.intValue() * right.intValue();
            case "/" -> right.intValue() != 0 ? left.intValue() / right.intValue() : null;
            default -> null;
        };
    }

    /**
     * Gère une condition assumée (ex. x == y).
     */
    @Override
    public EqualityDomain assume(ValueExpression expr, ProgramPoint pp1, ProgramPoint pp2, SemanticOracle oracle)
            throws SemanticException {
        EqualityDomain current = this;
        if (expr instanceof BinaryExpression binExpr && binExpr.getOperator() instanceof ComparisonEq) {
            if (binExpr.getLeft() instanceof Identifier leftId && binExpr.getRight() instanceof Identifier rightId) {
                Set<Identifier> mergedGroup = new HashSet<>(current.getState(leftId).elements);
                mergedGroup.addAll(current.getState(rightId).elements);
                Object leftVal = current.getState(leftId).getConcreteValue();
                Object rightVal = current.getState(rightId).getConcreteValue();
                Object finalVal = (leftVal != null) ? leftVal : rightVal;
                if (leftVal != null && rightVal != null && !leftVal.equals(rightVal)) {
                    return current.bottom();
                }
                current = current.putState(leftId, new EqualityGroup(mergedGroup, false, finalVal))
                        .putState(rightId, new EqualityGroup(mergedGroup, false, finalVal));
            } else if (binExpr.getLeft() instanceof Identifier leftId && binExpr.getRight() instanceof Constant constant) {
                Object constVal = constant.getValue();
                Set<Identifier> group = current.getState(leftId).elements;
                Object currentVal = current.getState(leftId).getConcreteValue();
                if (currentVal != null && !currentVal.equals(constVal)) {
                    return current.bottom();
                }
                current = current.putState(leftId, new EqualityGroup(group, false, constVal));
            }
        }
        return current.mergeGroups();
    }

    /** Fusionne les groupes d'égalité basés sur les valeurs concrètes et les égalités explicites. */
    private EqualityDomain mergeGroups() {
        EqualityDomain result = this;
        Map<Object, Set<Identifier>> concreteGroups = new HashMap<>();

        // Regrouper par valeurs concrètes
        for (Identifier id : this.getKeys()) {
            Object val = result.getState(id).getConcreteValue();
            if (val != null) {
                concreteGroups.computeIfAbsent(val, k -> new HashSet<>()).add(id);
            }
        }

        // Fusionner les groupes
        for (Identifier id : this.getKeys()) {
            Set<Identifier> group = new HashSet<>(result.getState(id).elements);
            Object val = result.getState(id).getConcreteValue();
            if (val != null && concreteGroups.containsKey(val)) {
                group.addAll(concreteGroups.get(val));
            }
            for (Identifier member : group) {
                result = result.putState(member, new EqualityGroup(group, false, val));
            }
        }
        return result;
    }

    @Override
    public Satisfiability satisfies(ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        if (expr instanceof BinaryExpression binExpr && binExpr.getOperator() instanceof ComparisonEq) {
            if (binExpr.getLeft() instanceof Identifier left && binExpr.getRight() instanceof Identifier right) {
                Set<Identifier> leftGroup = this.getState(left).elements;
                Object leftVal = this.getState(left).getConcreteValue();
                Object rightVal = this.getState(right).getConcreteValue();
                if (leftGroup.contains(right) || (leftVal != null && leftVal.equals(rightVal))) {
                    return Satisfiability.SATISFIED;
                }
            } else if (binExpr.getLeft() instanceof Identifier left && binExpr.getRight() instanceof Constant constant) {
                Object constVal = constant.getValue();
                Object leftVal = this.getState(left).getConcreteValue();
                if (leftVal != null && leftVal.equals(constVal)) {
                    return Satisfiability.SATISFIED;
                }
            }
        }
        return Satisfiability.UNKNOWN;
    }

    @Override
    public boolean knowsIdentifier(Identifier id) {
        return this.getKeys().contains(id);
    }

    @Override
    public EqualityDomain forgetIdentifier(Identifier id) throws SemanticException {
        EqualityDomain result = this;
        if (result.getKeys().contains(id)) {
            result = result.putState(id, new EqualityGroup(Collections.singleton(id), true, null));
            for (Identifier other : result.getKeys()) {
                if (!other.equals(id)) {
                    Set<Identifier> group = new HashSet<>(result.getState(other).elements);
                    if (group.contains(id)) {
                        group.remove(id);
                        Object val = result.getState(other).getConcreteValue();
                        result = result.putState(other, new EqualityGroup(group, group.isEmpty(), val));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public EqualityDomain forgetIdentifiersIf(Predicate<Identifier> test) throws SemanticException {
        return this; // Non implémenté pour ce domaine simple
    }

    @Override
    public EqualityDomain smallStepSemantics(ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return this;
    }

    @Override
    public EqualityDomain pushScope(ScopeToken scope) throws SemanticException {
        return this;
    }

    @Override
    public EqualityDomain popScope(ScopeToken scope) throws SemanticException {
        return this;
    }

//    @Override
//    public EqualityDomain widening(EqualityDomain previous) throws SemanticException {
//        EqualityDomain result = this.bottom(); // État initial vide
//        for (Identifier id : this.getKeys()) {
//            EqualityGroup currentGroup = this.getState(id); // Groupe d'égalité actuel
//            EqualityGroup previousGroup = previous.getState(id); // Groupe précédent
//            if (previousGroup != null && currentGroup.elements.equals(previousGroup.elements)) {
//                // Si l'égalité est stable, on la conserve avec sa valeur concrète
//                result = result.putState(id, new EqualityGroup(currentGroup.elements, false, currentGroup.getConcreteValue()));
//            } else {
//                // Sinon, on oublie cette égalité
//                result = result.putState(id, new EqualityGroup(Collections.singleton(id), true, null));
//            }
//        }
//        return result.mergeGroups(); // Fusionne les groupes cohérents
//    }

    /**
     * Sous-classe représentant un groupe d'égalité entre variables.
     */
    public static class EqualityGroup extends InverseSetLattice<EqualityGroup, Identifier> {
        private final Object concreteValue;

        public EqualityGroup(Set<Identifier> members, boolean isTop, Object value) {
            super(members, isTop);
            this.concreteValue = value;
        }

        @Override
        public EqualityGroup mk(Set<Identifier> set) {
            return new EqualityGroup(set, set.isEmpty(), null);
        }

        @Override
        public EqualityGroup top() {
            return new EqualityGroup(Collections.emptySet(), true, null);
        }

        @Override
        public EqualityGroup bottom() {
            return new EqualityGroup(Collections.emptySet(), false, null);
        }

        public Object getConcreteValue() {
            return concreteValue;
        }
    }
}