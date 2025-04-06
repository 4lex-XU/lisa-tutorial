package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.ValueCartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.symbolic.value.Identifier;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public class EqualityIntervalWithRoundingProductDomain extends ValueCartesianProduct<
    ValueEnvironment<IntervalWithRounding>,
    EqualityDomain> {

    public EqualityIntervalWithRoundingProductDomain(ValueEnvironment<IntervalWithRounding> left, EqualityDomain right) {
        super(left, right);
    }

    @Override
    public EqualityIntervalWithRoundingProductDomain mk(ValueEnvironment<IntervalWithRounding> left, EqualityDomain right) {
        return new EqualityIntervalWithRoundingProductDomain(left, right).reduce();
    }
    
    private EqualityIntervalWithRoundingProductDomain reduce() {
        ValueEnvironment<IntervalWithRounding> newLeft = this.left;
        EqualityDomain newRight = this.right;

        for (Identifier id : newRight.getKeys()) {
            EqualityDomain.EqualityGroup eqGroup = newRight.getState(id);
            Object concreteValue = eqGroup.getConcreteValue();

            if (concreteValue != null && concreteValue instanceof Number) {
                BigDecimal val = new BigDecimal(concreteValue.toString());
                IntervalWithRounding interval = new IntervalWithRounding(
                    new IntervalWithRounding.FloatOrInf(val),
                    new IntervalWithRounding.FloatOrInf(val)
                );
                newLeft = newLeft.putState(id, interval);
            }

            Set<Identifier> group = eqGroup.elements;
            for (Identifier member : group) {
                if (!newLeft.getKeys().contains(member)) {
                    if (concreteValue != null && concreteValue instanceof Number) {
                        BigDecimal val = new BigDecimal(concreteValue.toString());
                        IntervalWithRounding interval = new IntervalWithRounding(
                            new IntervalWithRounding.FloatOrInf(val),
                            new IntervalWithRounding.FloatOrInf(val)
                        );
                        newLeft = newLeft.putState(member, interval);
                    }
                }
            }
        }

        for (Identifier id : newLeft.getKeys()) {
            IntervalWithRounding interval = newLeft.getState(id);
            if (!interval.isBottom() && !interval.isTop() &&
                !interval.low.isInf() && !interval.high.isInf() &&
                interval.low.value.compareTo(interval.high.value) == 0) {
                BigDecimal singletonValue = interval.low.value;
                EqualityDomain.EqualityGroup eqGroup = newRight.getState(id);
                Set<Identifier> newGroup = eqGroup != null ? new HashSet<>(eqGroup.elements) : new HashSet<>();
                newGroup.add(id);

                Object existingValue = eqGroup != null ? eqGroup.getConcreteValue() : null;
                if (existingValue != null && !singletonValue.equals(new BigDecimal(existingValue.toString()))) {
                    return new EqualityIntervalWithRoundingProductDomain(newLeft.bottom(), newRight.bottom());
                }

                for (Identifier member : newGroup) {
                    newRight = newRight.putState(member, new EqualityDomain.EqualityGroup(newGroup, false, singletonValue));
                }
            }
        }

        return new EqualityIntervalWithRoundingProductDomain(newLeft, newRight);
    }

    @Override
    public EqualityIntervalWithRoundingProductDomain top() {
        return new EqualityIntervalWithRoundingProductDomain(left.top(), right.top());
    }

    @Override
    public EqualityIntervalWithRoundingProductDomain bottom() {
        return new EqualityIntervalWithRoundingProductDomain(left.bottom(), right.bottom());
    }

    @Override
    public String toString() {
        return "Intervals: " + left.toString() + ", Equalities: " + right.toString();
    }
}
