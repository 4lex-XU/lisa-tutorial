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
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonEq;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;

public class EqualityDomain extends FunctionalLattice<EqualityDomain, Identifier, EqualityDomain.EqualityGroup>
    implements ValueDomain<EqualityDomain> {

    // Constructeurs et méthodes inchangées omises pour brièveté
    public EqualityDomain(EqualityGroup treillis, Map<Identifier, EqualityGroup> mapping) {
        super(treillis, mapping);
    }

    public EqualityDomain(EqualityGroup treillis) {
        super(treillis);
    }

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

    @Override
    public EqualityDomain assign(Identifier id, ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
        throws SemanticException {
        EqualityDomain current = this.forgetIdentifier(id);

        if (expr instanceof Identifier sourceId) {
            Set<Identifier> group = new HashSet<>(current.getState(sourceId).elements);
            group.add(id);
            Object sourceValue = current.getState(sourceId).getConcreteValue();
            current = current.putState(id, new EqualityGroup(group, false, sourceValue))
                .putState(sourceId, new EqualityGroup(group, false, sourceValue));
        } else if (expr instanceof Constant constant) {
            Object value = constant.getValue();
            current = current.putState(id, new EqualityGroup(Set.of(id), false, value));
        } else if (expr instanceof BinaryExpression binExpr) {
            ValueExpression left = (ValueExpression) binExpr.getLeft();
            ValueExpression right = (ValueExpression) binExpr.getRight();
            BinaryOperator op = binExpr.getOperator();

            if (left instanceof Constant l && right instanceof Constant r &&
                l.getValue() instanceof Number && r.getValue() instanceof Number) {
                BigDecimal result = evaluateOperation(op, (Number) l.getValue(), (Number) r.getValue());
                current = current.putState(id, new EqualityGroup(Set.of(id), false, result));
            } else if (left instanceof Identifier leftId && right instanceof Constant rConst &&
                rConst.getValue() instanceof Number) {
                Object leftVal = current.getState(leftId).getConcreteValue();
                if (leftVal instanceof Number) {
                    BigDecimal result = evaluateOperation(op, (Number) leftVal, (Number) rConst.getValue());
                    current = current.putState(id, new EqualityGroup(Set.of(id), false, result));
                } else {
                    current = current.putState(id, new EqualityGroup(Set.of(id), false, null));
                }
            } else if (right instanceof Identifier rightId && left instanceof Constant lConst &&
                lConst.getValue() instanceof Number) {
                Object rightVal = current.getState(rightId).getConcreteValue();
                if (rightVal instanceof Number) {
                    BigDecimal result = evaluateOperation(op, (Number) lConst.getValue(), (Number) rightVal);
                    current = current.putState(id, new EqualityGroup(Set.of(id), false, result));
                } else {
                    current = current.putState(id, new EqualityGroup(Set.of(id), false, null));
                }
            } else {
                current = current.putState(id, new EqualityGroup(Set.of(id), false, null));
            }
        }
        return current.mergeGroups();
    }

    private BigDecimal evaluateOperation(BinaryOperator operator, Number left, Number right) {
        BigDecimal l = new BigDecimal(left.toString());
        BigDecimal r = new BigDecimal(right.toString());
        return switch (operator.toString()) {
            case "+" -> l.add(r);
            case "-" -> l.subtract(r);
            case "*" -> l.multiply(r);
            case "/" -> r.compareTo(BigDecimal.ZERO) != 0 ? l.divide(r, 10, BigDecimal.ROUND_HALF_UP) : null;
            default -> null;
        };
    }

    private EqualityDomain mergeGroups() {
        EqualityDomain result = new EqualityDomain();
        Map<Object, Set<Identifier>> concreteGroups = new HashMap<>();

        for (Identifier id : this.getKeys()) {
            Object val = this.getState(id).getConcreteValue();
            if (val != null) {
                concreteGroups.computeIfAbsent(val, k -> new HashSet<>()).add(id);
            }
        }

        for (Map.Entry<Object, Set<Identifier>> entry : concreteGroups.entrySet()) {
            Object val = entry.getKey();
            Set<Identifier> group = entry.getValue();
            for (Identifier member : group) {
                result = result.putState(member, new EqualityGroup(group, false, val));
            }
        }

        for (Identifier id : this.getKeys()) {
            if (this.getState(id).getConcreteValue() == null) {
                Set<Identifier> group = new HashSet<>(this.getState(id).elements);
                result = result.putState(id, new EqualityGroup(group, false, null));
            }
        }

        return result;
    }

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
                if (leftVal != null && rightVal != null && !leftVal.equals(rightVal))
                    return current.bottom();
                current = current.putState(leftId, new EqualityGroup(mergedGroup, false, finalVal))
                    .putState(rightId, new EqualityGroup(mergedGroup, false, finalVal));
            } else if (binExpr.getLeft() instanceof Identifier leftId && binExpr.getRight() instanceof Constant constant) {
                Object constVal = constant.getValue();
                Object currentVal = current.getState(leftId).getConcreteValue();
                if (currentVal != null && !currentVal.equals(constVal))
                    return current.bottom();
                current = current.putState(leftId,
                    new EqualityGroup(current.getState(leftId).elements, false, constVal));
            }
        }
        return current.mergeGroups();
    }

    @Override
    public Satisfiability satisfies(ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
        throws SemanticException {
        if (expr instanceof BinaryExpression binExpr && binExpr.getOperator() instanceof ComparisonEq) {
            if (binExpr.getLeft() instanceof Identifier left && binExpr.getRight() instanceof Identifier right) {
                Set<Identifier> leftGroup = this.getState(left).elements;
                Object leftVal = this.getState(left).getConcreteValue();
                Object rightVal = this.getState(right).getConcreteValue();
                if (leftGroup.contains(right) || (leftVal != null && leftVal.equals(rightVal)))
                    return Satisfiability.SATISFIED;
            } else if (binExpr.getLeft() instanceof Identifier left && binExpr.getRight() instanceof Constant constant) {
                Object constVal = constant.getValue();
                Object leftVal = this.getState(left).getConcreteValue();
                if (leftVal != null && leftVal.equals(constVal))
                    return Satisfiability.SATISFIED;
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
            Set<Identifier> groupToForget = new HashSet<>(result.getState(id).elements);
            for (Identifier var : groupToForget)
                result = result.putState(var, new EqualityGroup(Set.of(var), true, null));

            for (Identifier other : result.getKeys()) {
                if (!groupToForget.contains(other)) {
                    Set<Identifier> group = new HashSet<>(result.getState(other).elements);
                    group.removeAll(groupToForget);
                    Object val = result.getState(other).getConcreteValue();
                    result = result.putState(other, new EqualityGroup(group, group.isEmpty(), val));
                }
            }
        }
        return result;
    }

    @Override
    public EqualityDomain forgetIdentifiersIf(Predicate<Identifier> test) {
        return this;
    }

    @Override
    public EqualityDomain smallStepSemantics(ValueExpression expr, ProgramPoint pp, SemanticOracle oracle) {
        return this;
    }

    @Override
    public EqualityDomain pushScope(ScopeToken scope) {
        return this;
    }

    @Override
    public EqualityDomain popScope(ScopeToken scope) {
        return this;
    }

    @Override
    public EqualityDomain lub(EqualityDomain other) throws SemanticException {
        EqualityDomain union = this.top();

        for (Identifier id : this.getKeys()) {
            EqualityGroup group1 = this.getState(id);
            EqualityGroup group2 = other.getState(id);
            Set<Identifier> merged;
            Object val = null;

            if (group2 != null) {
                merged = new HashSet<>(group1.elements);
                merged.addAll(group2.elements);
                Object val1 = group1.getConcreteValue();
                Object val2 = group2.getConcreteValue();
                if (val1 != null && val1.equals(val2))
                    val = val1;
            } else {
                merged = new HashSet<>(group1.elements);
                val = group1.getConcreteValue();
            }

            for (Identifier v : merged)
                union = union.putState(v, new EqualityGroup(merged, false, val));
        }

        for (Identifier id : other.getKeys()) {
            if (!this.knowsIdentifier(id)) {
                EqualityGroup group2 = other.getState(id);
                for (Identifier v : group2.elements)
                    union = union.putState(v, new EqualityGroup(group2.elements, false, group2.getConcreteValue()));
            }
        }

        return union.mergeGroups();
    }

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
