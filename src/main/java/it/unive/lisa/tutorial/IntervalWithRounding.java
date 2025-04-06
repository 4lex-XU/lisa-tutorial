package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.DivisionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.*;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * A non-relational abstract domain representing intervals with floating-point rounding.
 * Each variable is mapped to an interval [low, high], adjusted for rounding errors.
 */
public class IntervalWithRounding implements BaseNonRelationalValueDomain<IntervalWithRounding> {

    // Special values for top and bottom
    public static final IntervalWithRounding TOP = new IntervalWithRounding(FloatOrInf.infiniteNeg, FloatOrInf.infinitePos);
    private static final IntervalWithRounding BOTTOM = new IntervalWithRounding(FloatOrInf.infinitePos, FloatOrInf.infiniteNeg);

    final FloatOrInf low;
    final FloatOrInf high;

    // Constructor for an interval [low, high]
    public IntervalWithRounding(FloatOrInf low, FloatOrInf high) {
        this.low = low;
        this.high = high;
    }

    // Default constructor for BOTTOM
    public IntervalWithRounding() {
        this(FloatOrInf.infinitePos, FloatOrInf.infiniteNeg); // Invalid interval for bottom
    }

    @Override
    public IntervalWithRounding top() {
        return TOP;
    }

    @Override
    public IntervalWithRounding bottom() {
        return BOTTOM;
    }

    @Override
    public boolean isTop() {
        return low.isNegInf() && high.isPosInf();
    }

    @Override
    public boolean isBottom() {
        return low.isPosInf() || high.isNegInf() || (!low.isInf() && !high.isInf() && low.value.compareTo(high.value) > 0);
    }

    @Override
    public IntervalWithRounding lubAux(IntervalWithRounding other) throws SemanticException {
        if (this.isBottom()) return other;
        if (other.isBottom()) return this;
        return new IntervalWithRounding(FloatOrInf.min(this.low, other.low), FloatOrInf.max(this.high, other.high));
    }

    @Override
    public IntervalWithRounding glbAux(IntervalWithRounding other) throws SemanticException {
        if (this.isBottom() || other.isBottom()) return BOTTOM;
        FloatOrInf newLow = FloatOrInf.max(this.low, other.low);
        FloatOrInf newHigh = FloatOrInf.min(this.high, other.high);
        return newLow.lessOrEqual(newHigh) ? new IntervalWithRounding(newLow, newHigh) : BOTTOM;
    }

    @Override
    public IntervalWithRounding wideningAux(IntervalWithRounding other) throws SemanticException {
        if (this.isBottom()) return other;
        if (other.isBottom()) return this;
        FloatOrInf newLow = other.low.lessThan(this.low) ? FloatOrInf.infiniteNeg : this.low;
        FloatOrInf newHigh = this.high.lessThan(other.high) ? FloatOrInf.infinitePos : this.high;
        return new IntervalWithRounding(newLow, newHigh);
    }

    @Override
    public boolean lessOrEqualAux(IntervalWithRounding other) throws SemanticException {
        if (this.isBottom()) return true;
        if (other.isBottom()) return false;
        return other.low.lessOrEqual(this.low) && this.high.lessOrEqual(other.high);
    }

    @Override
    public IntervalWithRounding evalNullConstant(ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        return TOP;
    }

    @Override
    public IntervalWithRounding evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (constant.getValue() instanceof Number) {
            Number num = (Number) constant.getValue();
            BigDecimal val = new BigDecimal(num.toString());
            return new IntervalWithRounding(new FloatOrInf(val), new FloatOrInf(val));
        }
        return TOP;
    }

    @Override
    public IntervalWithRounding evalUnaryExpression(UnaryOperator operator, IntervalWithRounding arg, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (arg.isBottom()) return BOTTOM;
        if (operator == NumericNegation.INSTANCE) {
            return new IntervalWithRounding(FloatOrInf.negate(arg.high), FloatOrInf.negate(arg.low));
        }
        return TOP;
    }

    @Override
    public IntervalWithRounding evalBinaryExpression(BinaryOperator operator, IntervalWithRounding left, IntervalWithRounding right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        System.out.println(left + " " + operator + " " + right);
        if (left.isBottom() || right.isBottom()) return BOTTOM;

        if (operator instanceof AdditionOperator) {
            FloatOrInf low = FloatOrInf.add(left.low, right.low);
            FloatOrInf high = FloatOrInf.add(left.high, right.high);
            return new IntervalWithRounding(low, high);
        } else if (operator instanceof SubtractionOperator) {
            FloatOrInf low = FloatOrInf.sub(left.low, right.high);
            FloatOrInf high = FloatOrInf.sub(left.high, right.low);
            return new IntervalWithRounding(low, high);
        } else if (operator instanceof MultiplicationOperator) {
            FloatOrInf[] bounds = {
                    FloatOrInf.mul(left.low, right.low), FloatOrInf.mul(left.low, right.high),
                    FloatOrInf.mul(left.high, right.low), FloatOrInf.mul(left.high, right.high)
            };
            FloatOrInf low = FloatOrInf.min(Arrays.stream(bounds).toArray(FloatOrInf[]::new));
            FloatOrInf high = FloatOrInf.max(Arrays.stream(bounds).toArray(FloatOrInf[]::new));
            return new IntervalWithRounding(low, high);
        } else if (operator instanceof DivisionOperator) {
            if (!right.low.isInf() && !right.high.isInf() &&
                    right.low.value.compareTo(BigDecimal.ZERO) <= 0 &&
                    right.high.value.compareTo(BigDecimal.ZERO) >= 0) {
                return TOP;
            }

            FloatOrInf[] bounds = {
                    FloatOrInf.div(left.low, right.low),
                    FloatOrInf.div(left.low, right.high),
                    FloatOrInf.div(left.high, right.low),
                    FloatOrInf.div(left.high, right.high)
            };
            FloatOrInf low = FloatOrInf.min(Arrays.stream(bounds).toArray(FloatOrInf[]::new));
            FloatOrInf high = FloatOrInf.max(Arrays.stream(bounds).toArray(FloatOrInf[]::new));
            return new IntervalWithRounding(low, high);
        }
        return TOP;
    }

    @Override
    public StructuredRepresentation representation() {
        if (isBottom()) return new StringRepresentation("⊥");
        if (isTop()) return new StringRepresentation("⊤");
        return new StringRepresentation("[" + low + ", " + high + "]");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IntervalWithRounding)) return false;
        IntervalWithRounding other = (IntervalWithRounding) obj;
        return this.low.equals(other.low) && this.high.equals(other.high);
    }

    @Override
    public int hashCode() {
        return low.hashCode() * 31 + high.hashCode();
    }

    @Override
    public String toString() {
        if (isBottom()) return "⊥";
        if (isTop()) return "⊤";
        return "[" + low + ", " + high + "]";
    }

    // Helper class for representing float or infinity
    static class FloatOrInf {
        private final boolean isInf;
        private final boolean isNeg;
        final BigDecimal value;
        public static final FloatOrInf infiniteNeg = new FloatOrInf(true);
        public static final FloatOrInf infinitePos = new FloatOrInf(false);

        // Constructeur pour une valeur finie
        public FloatOrInf(BigDecimal value) {
            this.isInf = false;
            this.isNeg = false;
            this.value = value;
        }

        // Constructeur pour une valeur finie à partir d'un double
        public FloatOrInf(double value) {
            this(new BigDecimal(String.valueOf(value))); // Conversion exacte via String
        }

        // Constructeur pour l'infini
        private FloatOrInf(boolean isNeg) {
            this.isInf = true;
            this.isNeg = isNeg;
            this.value = null;
        }

        public boolean isInf() {
            return isInf;
        }

        public boolean isNegInf() {
            return isInf && isNeg;
        }

        public boolean isPosInf() {
            return isInf && !isNeg;
        }

        public static FloatOrInf min(FloatOrInf a, FloatOrInf b) {
            if (a.isNegInf() || b.isNegInf()) return infiniteNeg;
            if (a.isPosInf()) return b;
            if (b.isPosInf()) return a;
            return new FloatOrInf(a.value.min(b.value));
        }

        public static FloatOrInf min(FloatOrInf... values) {
            return Arrays.stream(values).reduce(FloatOrInf::min).orElse(infiniteNeg);
        }

        public static FloatOrInf max(FloatOrInf a, FloatOrInf b) {
            if (a.isPosInf() || b.isPosInf()) return infinitePos;
            if (a.isNegInf()) return b;
            if (b.isNegInf()) return a;
            return new FloatOrInf(a.value.max(b.value));
        }

        public static FloatOrInf max(FloatOrInf... values) {
            return Arrays.stream(values).reduce(FloatOrInf::max).orElse(infinitePos);
        }

        public static FloatOrInf add(FloatOrInf a, FloatOrInf b) {
            if (a.isInf() || b.isInf()) {
                if (a.isNegInf() || b.isNegInf()) return infiniteNeg;
                return infinitePos;
            }
            return new FloatOrInf(a.value.add(b.value));
        }

        public static FloatOrInf sub(FloatOrInf a, FloatOrInf b) {
            if (a.isInf() || b.isInf()) {
                if (a.isNegInf() || b.isPosInf()) return infiniteNeg;
                if (a.isPosInf() || b.isNegInf()) return infinitePos;
            }
            return new FloatOrInf(a.value.subtract(b.value));
        }

        public static FloatOrInf mul(FloatOrInf a, FloatOrInf b) {
            if (a.isInf() || b.isInf()) {
                int sign = (a.isNegInf() || b.isNegInf()) ? -1 : 1;
                if (a.value != null && a.value.signum() < 0) sign *= -1;
                if (b.value != null && b.value.signum() < 0) sign *= -1;
                return sign < 0 ? infiniteNeg : infinitePos;
            }
            return new FloatOrInf(a.value.multiply(b.value));
        }

        public static FloatOrInf div(FloatOrInf a, FloatOrInf b) {
            if (a.isInf() || b.isInf()) {
                if (a.isNegInf()) {
                    if (b.isNegInf() || b.isPosInf()) return infinitePos;
                    return b.value.signum() < 0 ? infinitePos : infiniteNeg;
                } else if (a.isPosInf()) {
                    if (b.isNegInf() || b.isPosInf()) return infinitePos;
                    return b.value.signum() < 0 ? infiniteNeg : infinitePos;
                } else if (b.isNegInf()) {
                    return new FloatOrInf(BigDecimal.ZERO);
                } else if (b.isPosInf()) {
                    return new FloatOrInf(BigDecimal.ZERO);
                }
            } else if (b.value.compareTo(BigDecimal.ZERO) == 0) {
                return a.value.signum() < 0 ? infiniteNeg : infinitePos;
            }
            return new FloatOrInf(a.value.divide(b.value, 10, BigDecimal.ROUND_HALF_UP));
        }

        public static FloatOrInf negate(FloatOrInf a) {
            if (a.isNegInf()) return infinitePos;
            if (a.isPosInf()) return infiniteNeg;
            return new FloatOrInf(a.value.negate());
        }

        public boolean lessThan(FloatOrInf other) {
            if (this.isNegInf()) return !other.isNegInf();
            if (this.isPosInf()) return false;
            if (other.isNegInf()) return false;
            if (other.isPosInf()) return true;
            return this.value.compareTo(other.value) < 0;
        }

        public boolean lessOrEqual(FloatOrInf other) {
            if (this.isNegInf()) return true;
            if (this.isPosInf()) return other.isPosInf();
            if (other.isNegInf()) return false;
            if (other.isPosInf()) return true;
            return this.value.compareTo(other.value) <= 0;
        }

        @Override
        public String toString() {
            if (isNegInf()) return "-∞";
            if (isPosInf()) return "+∞";
            return value.stripTrailingZeros().toPlainString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FloatOrInf)) return false;
            FloatOrInf other = (FloatOrInf) obj;
            if (this.isInf && other.isInf) return this.isNeg == other.isNeg;
            if (this.isInf || other.isInf) return false;
            return this.value.compareTo(other.value) == 0;
        }

        @Override
        public int hashCode() {
            return isInf ? (isNeg ? -1 : 1) : value.hashCode();
        }
    }

    public ValueEnvironment<IntervalWithRounding> assumeBinaryExpression(
            ValueEnvironment<IntervalWithRounding> environment,
            BinaryOperator operator,
            ValueExpression left,
            ValueExpression right,
            ProgramPoint src,
            ProgramPoint dest,
            SemanticOracle oracle) throws SemanticException {

        if (environment.isBottom()) {
            return environment;
        }

        IntervalWithRounding leftValue = environment.eval(left, src, oracle);
        IntervalWithRounding rightValue = environment.eval(right, src, oracle);

        if (leftValue.isBottom() || rightValue.isBottom()) {
            return environment.bottom();
        }

        Identifier id = null;
        IntervalWithRounding eval = null;
        boolean rightIsExpr = false;

        if (left instanceof Identifier) {
            id = (Identifier) left;
            eval = rightValue;
            rightIsExpr = true;
        } else if (right instanceof Identifier) {
            id = (Identifier) right;
            eval = leftValue;
            rightIsExpr = false;
        } else {
            return environment;
        }

        IntervalWithRounding starting = environment.getState(id);
        if (starting == null) {
            starting = top();
        }

        IntervalWithRounding update = null;
        if (operator instanceof ComparisonLt) {
            if (rightIsExpr) {
                FloatOrInf newHigh;
                if (eval.low.isInf()) {
                    newHigh = eval.low;
                } else {
                    newHigh = new FloatOrInf(eval.low.value.subtract(BigDecimal.ONE));
                }
                update = new IntervalWithRounding(starting.low, FloatOrInf.min(starting.high, newHigh));
            } else {
                update = new IntervalWithRounding(FloatOrInf.max(starting.low, eval.low), starting.high);
            }
        } else if (operator instanceof ComparisonLe) {
            if (rightIsExpr) {
                update = new IntervalWithRounding(starting.low, FloatOrInf.min(starting.high, eval.low));
            } else {
                update = new IntervalWithRounding(FloatOrInf.max(starting.low, eval.low), starting.high);
            }
        } else if (operator instanceof ComparisonGt) {
            if (rightIsExpr) {
                FloatOrInf newLow;
                if (eval.high.isInf()) {
                    newLow = eval.high;
                } else {
                    newLow = new FloatOrInf(eval.high.value.add(BigDecimal.ONE));
                }
                update = new IntervalWithRounding(FloatOrInf.max(starting.low, newLow), starting.high);
            } else {
                FloatOrInf newHigh;
                if (eval.high.isInf()) {
                    newHigh = eval.high;
                } else {
                    newHigh = new FloatOrInf(eval.high.value.subtract(BigDecimal.ONE));
                }
                update = new IntervalWithRounding(starting.low, FloatOrInf.min(starting.high, newHigh));
            }
        } else if (operator instanceof ComparisonGe) {
            if (rightIsExpr) {
                update = new IntervalWithRounding(FloatOrInf.max(starting.low, eval.high), starting.high);
            } else {
                update = new IntervalWithRounding(starting.low, FloatOrInf.min(starting.high, eval.high));
            }
        } else if (operator instanceof ComparisonEq) {
            if (starting.low.value != null && eval.low.value != null &&
                    starting.high.value != null && eval.high.value != null &&
                    starting.low.value.compareTo(eval.low.value) <= 0 &&
                    starting.high.value.compareTo(eval.high.value) >= 0) {
                update = new IntervalWithRounding(eval.low, eval.high);
            } else {
                update = bottom();
            }
        } else if (operator instanceof ComparisonNe) {
            update = starting;
        } else {
            return environment;
        }

        if (update == null || update.isBottom()) {
            return environment;
        }

        IntervalWithRounding refined = new IntervalWithRounding(
                FloatOrInf.max(starting.low, update.low),
                FloatOrInf.min(starting.high, update.high)
        );

        if (!refined.isBottom() &&
                refined.low.value != null && refined.high.value != null &&
                refined.low.value.compareTo(refined.high.value) > 0) {
            return environment.bottom();
        }

        return environment.putState(id, refined);
    }
}
