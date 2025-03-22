package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.CodeLocation;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.*;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Arrays;

/**
 * A non-relational abstract domain representing intervals with floating-point rounding.
 * Each variable is mapped to an interval [low, high], adjusted for rounding errors.
 */
public class IntervalWithRounding implements BaseNonRelationalValueDomain<IntervalWithRounding> {

    // Special values for top and bottom
    public static final IntervalWithRounding TOP = new IntervalWithRounding(FloatOrInf.infiniteNeg, FloatOrInf.infinitePos);
    private static final IntervalWithRounding BOTTOM = new IntervalWithRounding(FloatOrInf.infinitePos, FloatOrInf.infiniteNeg);

    private final FloatOrInf low;  // Lower bound of the interval
    private final FloatOrInf high; // Upper bound of the interval

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
        return low.isPosInf() || high.isNegInf() || (!low.isInf() && !high.isInf() && low.value > high.value);
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
    /*
    @Override
    public IntervalWithRounding wideningAux(IntervalWithRounding other) throws SemanticException {
        if (this.isBottom()) return other;
        if (other.isBottom()) return this;

        // Gestion de la borne inférieure
        FloatOrInf newLow;
        if (other.low.lessThan(this.low)) {
            // La borne inférieure diminue
            if (!this.low.isInf() && this.low.value > -10) {
                newLow = new FloatOrInf(-10.0);
            } else if (!this.low.isInf() && this.low.value > -100) {
                newLow = new FloatOrInf(-100.0);
            } else if (!this.low.isInf() && this.low.value > -1000) {
                newLow = new FloatOrInf(-1000.0);
            } else {
                newLow = FloatOrInf.infiniteNeg;
            }
        } else {
            newLow = this.low;
        }

        // Gestion de la borne supérieure
        FloatOrInf newHigh;
        if (this.high.lessThan(other.high)) {
            // La borne supérieure augmente
            if (!this.high.isInf() && this.high.value < 10) {
                newHigh = new FloatOrInf(10.0);
            } else if (!this.high.isInf() && this.high.value < 100) {
                newHigh = new FloatOrInf(100.0);
            } else if (!this.high.isInf() && this.high.value < 1000) {
                newHigh = new FloatOrInf(1000.0);
            } else {
                newHigh = FloatOrInf.infinitePos;
            }
        } else {
            newHigh = this.high;
        }

        return new IntervalWithRounding(newLow, newHigh);
    }*/

    @Override
    public boolean lessOrEqualAux(IntervalWithRounding other) throws SemanticException {
        if (this.isBottom()) return true;
        if (other.isBottom()) return false;
        return other.low.lessOrEqual(this.low) && this.high.lessOrEqual(other.high);
    }

    @Override
    public IntervalWithRounding evalNullConstant(ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        return TOP; // Null is approximated as top
    }

    @Override
    public IntervalWithRounding evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (constant.getValue() instanceof Number) {
            double val = ((Number) constant.getValue()).doubleValue();
            return new IntervalWithRounding(new FloatOrInf(val), new FloatOrInf(val));
        }
        return TOP; // Non-numeric constants are approximated as top
    }

    @Override
    public IntervalWithRounding evalUnaryExpression(UnaryOperator operator, IntervalWithRounding arg, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (arg.isBottom()) return BOTTOM;
        if (operator == NumericNegation.INSTANCE) {
            return new IntervalWithRounding(FloatOrInf.negate(arg.high), FloatOrInf.negate(arg.low));
        }
        return TOP; // Unknown operators result in top
    }

    @Override
    public IntervalWithRounding evalBinaryExpression(BinaryOperator operator, IntervalWithRounding left, IntervalWithRounding right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        System.out.println(left + " ; " + right);
        if (left.isBottom() || right.isBottom()) return BOTTOM;
        if (operator instanceof AdditionOperator) {
            FloatOrInf low = FloatOrInf.add(left.low, right.low);
            FloatOrInf high = FloatOrInf.add(left.high, right.high);
            // Adjust for rounding
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
        }
        return TOP; // Unsupported operators result in top
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
    private static class FloatOrInf {
        private final boolean isInf;
        private final boolean isNeg; // True if negative infinity, false if positive infinity
        private final double value;
        public static final FloatOrInf infiniteNeg = new FloatOrInf(true);
        public static final FloatOrInf infinitePos = new FloatOrInf(false);

        // Constructor for finite value
        public FloatOrInf(double value) {
            this.isInf = false;
            this.isNeg = false;
            this.value = value;
        }

        // Constructor for infinity
        private FloatOrInf(boolean isNeg) {
            this.isInf = true;
            this.isNeg = isNeg;
            this.value = isNeg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
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
            return new FloatOrInf(Math.min(a.value, b.value));
        }

        public static FloatOrInf min(FloatOrInf... values) {
            return Arrays.stream(values).reduce(FloatOrInf::min).orElse(infiniteNeg);
        }

        public static FloatOrInf max(FloatOrInf a, FloatOrInf b) {
            if (a.isPosInf() || b.isPosInf()) return infinitePos;
            if (a.isNegInf()) return b;
            if (b.isNegInf()) return a;
            return new FloatOrInf(Math.max(a.value, b.value));
        }

        public static FloatOrInf max(FloatOrInf... values) {
            return Arrays.stream(values).reduce(FloatOrInf::max).orElse(infinitePos);
        }

        public static FloatOrInf add(FloatOrInf a, FloatOrInf b) {
            if (a.isInf() || b.isInf()) {
                if (a.isNegInf() || b.isNegInf()) return infiniteNeg;
                return infinitePos;
            }
            return new FloatOrInf(a.value + b.value);
        }

        public static FloatOrInf sub(FloatOrInf a, FloatOrInf b) {
            if (a.isInf() || b.isInf()) {
                if (a.isNegInf() || b.isPosInf()) return infiniteNeg;
                if (a.isPosInf() || b.isNegInf()) return infinitePos;
            }
            return new FloatOrInf(a.value - b.value);
        }

        public static FloatOrInf mul(FloatOrInf a, FloatOrInf b) {
            if (a.isInf() || b.isInf()) {
                if ((a.isNegInf() && b.value < 0) || (b.isNegInf() && a.value < 0)) return infinitePos;
                if ((a.isPosInf() && b.value < 0) || (b.isPosInf() && a.value < 0)) return infiniteNeg;
                return infinitePos;
            }
            return new FloatOrInf(a.value * b.value);
        }

        public static FloatOrInf negate(FloatOrInf a) {
            if (a.isNegInf()) return infinitePos;
            if (a.isPosInf()) return infiniteNeg;
            return new FloatOrInf(-a.value);
        }

        public boolean lessThan(FloatOrInf other) {
            if (this.isNegInf()) return !other.isNegInf();
            if (this.isPosInf()) return false;
            if (other.isNegInf()) return false;
            if (other.isPosInf()) return true;
            return this.value < other.value;
        }

        public boolean lessOrEqual(FloatOrInf other) {
            if (this.isNegInf()) return true;
            if (this.isPosInf()) return other.isPosInf();
            if (other.isNegInf()) return false;
            if (other.isPosInf()) return true;
            return this.value <= other.value;
        }

        // Adjust for rounding down (slightly widen lower bound)
        public FloatOrInf roundDown() {
            if (isInf()) return this;
            return new FloatOrInf(value - Math.ulp(Math.abs(value)));
        }

        // Adjust for rounding up (slightly widen upper bound)
        public FloatOrInf roundUp() {
            if (isInf()) return this;
            return new FloatOrInf(value + Math.ulp(Math.abs(value)));
        }

        @Override
        public String toString() {
            if (isNegInf()) return "-∞";
            if (isPosInf()) return "+∞";
            return String.valueOf(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FloatOrInf)) return false;
            FloatOrInf other = (FloatOrInf) obj;
            if (this.isInf && other.isInf) return this.isNeg == other.isNeg;
            if (this.isInf || other.isInf) return false;
            return this.value == other.value;
        }

        @Override
        public int hashCode() {
            return isInf ? (isNeg ? -1 : 1) : Double.hashCode(value);
        }
    }

    @Override
    public ValueEnvironment<IntervalWithRounding> assumeBinaryExpression(
        ValueEnvironment<IntervalWithRounding> environment,
        BinaryOperator operator,
        ValueExpression left,
        ValueExpression right,
        ProgramPoint src,
        ProgramPoint dest,
        SemanticOracle oracle)
        throws SemanticException {
        Identifier id;
        IntervalWithRounding eval;
        boolean rightIsExpr;

        // Étape 1 : Identifier la variable et évaluer l'autre opérande
        if (left instanceof Identifier) {
            eval = eval(right, environment, src, oracle);
            id = (Identifier) left;
            rightIsExpr = true;
        } else if (right instanceof Identifier) {
            eval = eval(left, environment, src, oracle);
            id = (Identifier) right;
            rightIsExpr = false;
        } else {
            return environment; // Ni left ni right n'est un identifiant, pas de raffinement
        }

        // Étape 2 : Récupérer l'intervalle actuel de la variable
        IntervalWithRounding starting = environment.getState(id);
        if (eval.isBottom() || starting.isBottom()) {
            return environment.bottom();
        }

        // Étape 3 : Préparer les intervalles pour le raffinement
        boolean lowIsMinusInfinity = eval.low.isNegInf();
        IntervalWithRounding low_inf = new IntervalWithRounding(
            eval.low,
            FloatOrInf.infinitePos
        );
        IntervalWithRounding lowp1_inf = new IntervalWithRounding(
            new FloatOrInf(eval.low.isInf() ? eval.low.value : eval.low.value + 1.0),
            FloatOrInf.infinitePos
        );
        IntervalWithRounding inf_high = new IntervalWithRounding(
            FloatOrInf.infiniteNeg,
            eval.high
        );
        IntervalWithRounding inf_highm1 = new IntervalWithRounding(
            FloatOrInf.infiniteNeg,
            new FloatOrInf(eval.high.isInf() ? eval.high.value : eval.high.value - 1.0)
        );

        // Étape 4 : Raffiner l'intervalle en fonction de l'opérateur
        IntervalWithRounding update = null;
        if (operator == ComparisonEq.INSTANCE) {
            update = eval; // i == 10.0 -> i: [10.0, 10.0]
        } else if (operator == ComparisonGe.INSTANCE) {
            if (rightIsExpr) {
                // i >= 10.0 -> i: [10.0, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(low_inf);
            } else {
                // 10.0 >= i -> i: [-∞, 10.0]
                update = starting.glb(inf_high);
            }
        } else if (operator == ComparisonGt.INSTANCE) {
            if (rightIsExpr) {
                // i > 10.0 -> i: [10.0 + 1, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);
            } else {
                // 10.0 > i -> i: [-∞, 10.0 - 1]
                update = lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
            }
        } else if (operator == ComparisonLe.INSTANCE) {
            if (rightIsExpr) {
                // i <= 10.0 -> i: [-∞, 10.0]
                update = starting.glb(inf_high);
            } else {
                // 10.0 <= i -> i: [10.0, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(low_inf);
            }
        } else if (operator == ComparisonLt.INSTANCE) {
            if (rightIsExpr) {
                // i < 10.0 -> i: [-∞, 10.0 - 1]
                update = lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
            } else {
                // 10.0 < i -> i: [10.0 + 1, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);
            }
        }

        // Étape 5 : Mettre à jour l'environnement
        if (update == null) {
            return environment; // Pas de raffinement possible
        } else if (update.isBottom()) {
            return environment.bottom(); // Condition contradictoire
        } else {
            return environment.putState(id, update); // Mettre à jour l'intervalle de la variable
        }
    }
}
