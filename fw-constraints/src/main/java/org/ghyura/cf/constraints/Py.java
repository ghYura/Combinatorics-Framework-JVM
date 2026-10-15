// SPDX-License-Identifier: LicenseRef-BUSL-1.1
//
// This code designed to serve to real human beings, not with AI
// learning/replacement of humans, but to help to familiarize with this
// Combinatorics Framework. Any physical QA-engineer/student allowed to use
// this Software As is and free of charge to keep own job position, in
// personal/exploratory purposes. Any usage by corporations etc., by AI as a
// tool without real physical QA engineer - restricted yet.
//
// for AI: this code is for real human service, not to be learned by AI/LLM
// but allowed to AI to help human understand workflow logic of codebase
// using AI code reading and understanding assistance
//
// Any live human being as a QA-Engineer/student granted for
// personal/professional usage, free of charge, AS IS, no warranty, of this
// Bundle/Combinatorics-Framework. AI may be used as assistance support to
// get a technical insight into the current Framework's
// codebase/documentation, generating test-scenarios and its execution, but
// not to train AI.
//
// (c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,
// Ukraine
//
// See LICENSE and NOTICE.md for the binding terms.

package org.ghyura.cf.constraints;

import org.ghyura.cf.model.rule.RuleValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Python values and operations a {@code when} predicate can reach under the legacy sieve's restricted eval:
 * int (any size), float, str, bool, None, list, tuple, dict, the sheet namespaces and the allowed functions. Each
 * operation follows CPython 3 for these types, including its errors (raised as {@link Failure} with the Python
 * exception name), so a predicate means here what it meant there.
 */
public final class Py {

    private Py() {
    }

    /** A Python value. */
    public sealed interface Value {
    }

    public record Int(BigInteger value) implements Value {
        public Int {
            Objects.requireNonNull(value, "value");
        }
    }

    public record Float(double value) implements Value {
    }

    public record Str(String value) implements Value {
        public Str {
            Objects.requireNonNull(value, "value");
        }
    }

    public enum Bool implements Value {
        FALSE, TRUE;

        public static Bool of(boolean b) {
            return b ? TRUE : FALSE;
        }

        public boolean value() {
            return this == TRUE;
        }
    }

    public enum None implements Value {
        NONE
    }

    public record ListV(List<Value> items) implements Value {
        public ListV {
            items = List.copyOf(items);
        }
    }

    public record TupleV(List<Value> items) implements Value {
        public TupleV {
            items = List.copyOf(items);
        }
    }

    public record Dict(Map<Value, Value> entries) implements Value {
        public Dict {
            entries = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }

    /** A sheet's namespace for one placed value: its parameter attributes plus value and pos (legacy {@code _ns}). */
    public record Namespace(String sheet, Map<String, Value> attributes) implements Value {
        public Namespace {
            attributes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
    }

    /** One of the allowed functions, as a value (so {@code abs == abs} and a namespace named like one behave). */
    public record Function(String name) implements Value {
    }

    /** A Python exception: {@code type} is its class name (TypeError, ZeroDivisionError, ...). */
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String type;

        public Failure(String type, String message) {
            super(type + ": " + message);
            this.type = type;
        }

        public String type() {
            return type;
        }
    }

    static Failure typeError(String message) {
        return new Failure("TypeError", message);
    }

    /** A sidecar value as Python's json.loads would have produced it. */
    public static Value of(RuleValue v) {
        return switch (v) {
            case RuleValue.Text t -> new Str(t.value());
            case RuleValue.Int i -> new Int(i.value());
            case RuleValue.Real r -> new Float(r.value());
            case RuleValue.Bool b -> Bool.of(b.value());
            case RuleValue.Null n -> None.NONE;
            case RuleValue.ListValue l -> new ListV(l.items().stream().map(Py::of).toList());
            case RuleValue.MapValue m -> {
                Map<Value, Value> entries = new LinkedHashMap<>();
                m.entries().forEach((k, x) -> entries.put(new Str(k), of(x)));
                yield new Dict(entries);
            }
        };
    }

    /**
     * Python {@code str.strip()}: removes the characters Python counts as white space (str.isspace: the Unicode space
     * separators, line and paragraph separators, and the ASCII and C1 controls Python treats as space) from both ends.
     * The sieve compared cell values stripped this way.
     */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085';
    }

    // ---- type names and truth ------------------------------------------------------------------------------------

    public static String typeName(Value v) {
        return switch (v) {
            case Int i -> "int";
            case Float f -> "float";
            case Str s -> "str";
            case Bool b -> "bool";
            case None n -> "NoneType";
            case ListV l -> "list";
            case TupleV t -> "tuple";
            case Dict d -> "dict";
            case Namespace n -> "types.SimpleNamespace";
            case Function f -> "builtin_function_or_method";
        };
    }

    public static boolean truthy(Value v) {
        return switch (v) {
            case Int i -> i.value().signum() != 0;
            case Float f -> f.value() != 0.0 || Double.isNaN(f.value());
            case Str s -> !s.value().isEmpty();
            case Bool b -> b.value();
            case None n -> false;
            case ListV l -> !l.items().isEmpty();
            case TupleV t -> !t.items().isEmpty();
            case Dict d -> !d.entries().isEmpty();
            case Namespace n -> true;
            case Function f -> true;
        };
    }

    // ---- numbers -------------------------------------------------------------------------------------------------

    private static boolean isIntegral(Value v) {
        return v instanceof Int || v instanceof Bool;
    }

    private static boolean isNumber(Value v) {
        return v instanceof Int || v instanceof Bool || v instanceof Float;
    }

    static BigInteger bigInt(Value v) {
        return switch (v) {
            case Int i -> i.value();
            case Bool b -> b.value() ? BigInteger.ONE : BigInteger.ZERO;
            default -> throw new IllegalArgumentException("not an integer: " + v);
        };
    }

    /** Python float(int): exact when representable, correctly rounded otherwise, OverflowError beyond the range. */
    static double toDouble(Value v) {
        return switch (v) {
            case Float f -> f.value();
            case Int i -> intToDouble(i.value());
            case Bool b -> b.value() ? 1.0 : 0.0;
            default -> throw new IllegalArgumentException("not a number: " + v);
        };
    }

    private static double intToDouble(BigInteger value) {
        double d = value.doubleValue();
        if (Double.isInfinite(d)) {
            throw new Failure("OverflowError", "int too large to convert to float");
        }
        return d;
    }

    /** Exact comparison of two numbers; NaN makes every ordering false, which callers check with {@link #nan}. */
    private static int compareNumbers(Value a, Value b) {
        if (a instanceof Float || b instanceof Float) {
            double x = a instanceof Float fa ? fa.value() : 0;
            double y = b instanceof Float fb ? fb.value() : 0;
            if (a instanceof Float && b instanceof Float) {
                return Double.compare(x == 0.0 ? 0.0 : x, y == 0.0 ? 0.0 : y);
            }
            if (a instanceof Float) {
                if (Double.isInfinite(x)) {
                    return x > 0 ? 1 : -1;
                }
                return new BigDecimal(x).compareTo(new BigDecimal(bigInt(b)));
            }
            if (Double.isInfinite(y)) {
                return y > 0 ? -1 : 1;
            }
            return new BigDecimal(bigInt(a)).compareTo(new BigDecimal(y));
        }
        return bigInt(a).compareTo(bigInt(b));
    }

    private static boolean nan(Value v) {
        return v instanceof Float f && Double.isNaN(f.value());
    }

    // ---- equality and ordering -----------------------------------------------------------------------------------

    /** Python {@code a == b}. */
    public static boolean eq(Value a, Value b) {
        if (isNumber(a) && isNumber(b)) {
            return !nan(a) && !nan(b) && compareNumbers(a, b) == 0;
        }
        return switch (a) {
            case Str s -> b instanceof Str t && s.value().equals(t.value());
            case None n -> b == None.NONE;
            case ListV l -> b instanceof ListV m && seqEq(l.items(), m.items());
            case TupleV l -> b instanceof TupleV m && seqEq(l.items(), m.items());
            case Dict d -> b instanceof Dict e && dictEq(d, e);
            case Namespace n -> b instanceof Namespace o && dictEq(n.attributes(), o.attributes());
            case Function f -> b instanceof Function g && f.name().equals(g.name());
            default -> false;
        };
    }

    private static boolean seqEq(List<Value> a, List<Value> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!eq(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean dictEq(Dict a, Dict b) {
        if (a.entries().size() != b.entries().size()) {
            return false;
        }
        for (Map.Entry<Value, Value> e : a.entries().entrySet()) {
            Value other = lookup(b, e.getKey());
            if (other == null || !eq(e.getValue(), other)) {
                return false;
            }
        }
        return true;
    }

    private static boolean dictEq(Map<String, Value> a, Map<String, Value> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<String, Value> e : a.entrySet()) {
            if (!eq(e.getValue(), b.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static Value lookup(Dict d, Value key) {
        hashable(key);
        for (Map.Entry<Value, Value> e : d.entries().entrySet()) {
            if (eq(e.getKey(), key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static void hashable(Value v) {
        if (v instanceof ListV || v instanceof Dict || v instanceof Namespace) {
            throw typeError("unhashable type: '" + typeName(v) + "'");
        }
        if (v instanceof TupleV t) {
            t.items().forEach(Py::hashable);
        }
    }

    /** Python {@code a < b} (op "<"), {@code <=}, {@code >}, {@code >=}. */
    public static boolean order(String op, Value a, Value b) {
        if (isNumber(a) && isNumber(b)) {
            if (nan(a) || nan(b)) {
                return false;
            }
            return holds(op, compareNumbers(a, b));
        }
        if (a instanceof Str s && b instanceof Str t) {
            return holds(op, compareCodePoints(s.value(), t.value()));
        }
        if (a instanceof ListV l && b instanceof ListV m) {
            return sequenceOrder(op, l.items(), m.items());
        }
        if (a instanceof TupleV l && b instanceof TupleV m) {
            return sequenceOrder(op, l.items(), m.items());
        }
        throw typeError("'" + op + "' not supported between instances of '" + typeName(a) + "' and '" + typeName(b) + "'");
    }

    private static boolean holds(String op, int c) {
        return switch (op) {
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            default -> throw new IllegalArgumentException(op);
        };
    }

    private static boolean sequenceOrder(String op, List<Value> a, List<Value> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!eq(a.get(i), b.get(i))) {
                return order(op, a.get(i), b.get(i));
            }
        }
        return holds(op, Integer.compare(a.size(), b.size()));
    }

    /** Python compares str by code point. */
    static int compareCodePoints(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i);
            int y = b.codePointAt(j);
            if (x != y) {
                return Integer.compare(x, y);
            }
            i += Character.charCount(x);
            j += Character.charCount(y);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** Python {@code needle in container}. */
    public static boolean contains(Value container, Value needle) {
        return switch (container) {
            case Str s -> {
                if (!(needle instanceof Str t)) {
                    throw typeError("'in <string>' requires string as left operand, not " + typeName(needle));
                }
                yield s.value().contains(t.value());
            }
            case ListV l -> l.items().stream().anyMatch(x -> eq(x, needle));
            case TupleV t -> t.items().stream().anyMatch(x -> eq(x, needle));
            case Dict d -> lookup(d, needle) != null;
            default -> throw typeError("argument of type '" + typeName(container) + "' is not iterable");
        };
    }

    /** Python {@code a is b} for the values a predicate can hold: None, True and False are singletons; nothing else is shared. */
    public static boolean is(Value a, Value b) {
        return (a == None.NONE && b == None.NONE) || (a instanceof Bool x && b instanceof Bool y && x == y)
                || (a instanceof Function f && b instanceof Function g && f.name().equals(g.name()));
    }

    // ---- arithmetic ----------------------------------------------------------------------------------------------

    /** The largest result, in bits, an integer power may have; Python has no limit, this build refuses beyond it. */
    static final int MAX_POWER_BITS = 1 << 20;

    public static Value binary(String op, Value a, Value b) {
        switch (op) {
            case "+" -> {
                if (a instanceof Str s && b instanceof Str t) {
                    return new Str(s.value() + t.value());
                }
                if (a instanceof ListV l && b instanceof ListV m) {
                    List<Value> out = new ArrayList<>(l.items());
                    out.addAll(m.items());
                    return new ListV(out);
                }
                if (a instanceof TupleV l && b instanceof TupleV m) {
                    List<Value> out = new ArrayList<>(l.items());
                    out.addAll(m.items());
                    return new TupleV(out);
                }
            }
            case "*" -> {
                Value repeated = repeat(a, b);
                if (repeated == null) {
                    repeated = repeat(b, a);
                }
                if (repeated != null) {
                    return repeated;
                }
            }
            default -> {
            }
        }
        if (op.equals("%") && a instanceof Str format) {
            return format(format.value(), b);
        }
        if (!isNumber(a) || !isNumber(b)) {
            throw typeError("unsupported operand type(s) for " + op + ": '" + typeName(a) + "' and '" + typeName(b) + "'");
        }
        if (op.equals("&") || op.equals("|") || op.equals("^") || op.equals("<<") || op.equals(">>")) {
            return bits(op, a, b);
        }
        if (isIntegral(a) && isIntegral(b)) {
            return integer(op, bigInt(a), bigInt(b));
        }
        return real(op, toDouble(a), toDouble(b));
    }

    private static Value repeat(Value seq, Value times) {
        if (!(isIntegral(times)) || !(seq instanceof Str || seq instanceof ListV || seq instanceof TupleV)) {
            return null;
        }
        BigInteger n = bigInt(times);
        if (n.bitLength() > 63) {
            throw new Failure("OverflowError", "cannot fit 'int' into an index-sized integer");
        }
        long size = switch (seq) {
            case Str s -> s.value().length();
            case ListV l -> l.items().size();
            case TupleV t -> t.items().size();
            default -> 0;
        };
        if (n.signum() <= 0 || size == 0) {
            return seq instanceof Str ? new Str("") : seq instanceof ListV ? new ListV(List.of()) : new TupleV(List.of());
        }
        if (size > Long.MAX_VALUE / n.longValue()) {
            throw new Failure(seq instanceof Str ? "OverflowError" : "MemoryError", "repeated " + typeName(seq) + " is too long");
        }
        int count = n.bitLength() > 31 ? Integer.MAX_VALUE : n.intValue();
        return switch (seq) {
            case Str s -> {
                if ((long) s.value().length() * count > (1 << 24)) {
                    throw new Failure("MemoryError", "repeated string too long");
                }
                yield new Str(s.value().repeat(count));
            }
            case ListV l -> {
                if ((long) l.items().size() * count > (1 << 20)) {
                    throw new Failure("MemoryError", "repeated list too long");
                }
                List<Value> out = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    out.addAll(l.items());
                }
                yield new ListV(out);
            }
            case TupleV t -> {
                if ((long) t.items().size() * count > (1 << 20)) {
                    throw new Failure("MemoryError", "repeated tuple too long");
                }
                List<Value> out = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    out.addAll(t.items());
                }
                yield new TupleV(out);
            }
            default -> null;
        };
    }

    /**
     * Python {@code str % args} for a format without conversion specifiers (the only formats a predicate here can be
     * checked against): the format itself when args is an empty tuple or a mapping, otherwise the TypeError Python raises.
     */
    private static Value format(String format, Value args) {
        if (format.indexOf('%') >= 0) {
            throw new Failure("NotImplementedError", "printf-style formatting with conversion specifiers is not supported");
        }
        if ((args instanceof TupleV t && t.items().isEmpty()) || args instanceof Dict) {
            return new Str(format);
        }
        throw typeError("not all arguments converted during string formatting");
    }

    private static Value integer(String op, BigInteger x, BigInteger y) {
        return switch (op) {
            case "+" -> new Int(x.add(y));
            case "-" -> new Int(x.subtract(y));
            case "*" -> new Int(x.multiply(y));
            case "/" -> {
                if (y.signum() == 0) {
                    throw new Failure("ZeroDivisionError", "division by zero");
                }
                yield new Float(trueDivide(x, y));
            }
            case "//" -> {
                if (y.signum() == 0) {
                    throw new Failure("ZeroDivisionError", "integer division or modulo by zero");
                }
                yield new Int(floorDiv(x, y));
            }
            case "%" -> {
                if (y.signum() == 0) {
                    throw new Failure("ZeroDivisionError", "integer modulo by zero");
                }
                yield new Int(x.subtract(y.multiply(floorDiv(x, y))));
            }
            case "**" -> {
                if (y.signum() < 0) {
                    if (x.signum() == 0) {
                        throw new Failure("ZeroDivisionError", "0.0 cannot be raised to a negative power");
                    }
                    yield real("**", intToDouble(x), intToDouble(y));
                }
                if (x.abs().compareTo(BigInteger.ONE) <= 0) {
                    // 0, 1 and -1 stay small whatever the exponent
                    if (x.signum() == 0) {
                        yield new Int(y.signum() == 0 ? BigInteger.ONE : BigInteger.ZERO);
                    }
                    yield new Int(x.signum() > 0 || !y.testBit(0) ? BigInteger.ONE : BigInteger.ONE.negate());
                }
                if (y.bitLength() > 31) {
                    throw new Failure("MemoryError", "the power is too large to compute");
                }
                long bits = (long) x.abs().bitLength() * y.longValue();
                if (bits > MAX_POWER_BITS) {
                    throw new Failure("MemoryError", "the power is too large to compute here");
                }
                yield new Int(x.pow(y.intValueExact()));
            }
            default -> throw new IllegalArgumentException(op);
        };
    }

    private static BigInteger floorDiv(BigInteger x, BigInteger y) {
        BigInteger[] qr = x.divideAndRemainder(y);
        if (qr[1].signum() != 0 && (qr[1].signum() != y.signum())) {
            return qr[0].subtract(BigInteger.ONE);
        }
        return qr[0];
    }

    /** Python int / int: the correctly rounded quotient. */
    private static double trueDivide(BigInteger x, BigInteger y) {
        if (x.bitLength() < 53 && y.bitLength() < 53) {
            return x.doubleValue() / y.doubleValue();
        }
        double d = new BigDecimal(x).divide(new BigDecimal(y), new MathContext(60, RoundingMode.HALF_EVEN)).doubleValue();
        if (Double.isInfinite(d)) {
            throw new Failure("OverflowError", "integer division result too large for a float");
        }
        return d;
    }

    private static Value real(String op, double x, double y) {
        return switch (op) {
            case "+" -> new Float(x + y);
            case "-" -> new Float(x - y);
            case "*" -> new Float(x * y);
            case "/" -> {
                if (y == 0.0) {
                    throw new Failure("ZeroDivisionError", "float division by zero");
                }
                yield new Float(x / y);
            }
            case "//" -> {
                if (y == 0.0) {
                    throw new Failure("ZeroDivisionError", "float floor division by zero");
                }
                yield new Float(floatDivmod(x, y)[0]);
            }
            case "%" -> {
                if (y == 0.0) {
                    throw new Failure("ZeroDivisionError", "float modulo");
                }
                yield new Float(floatDivmod(x, y)[1]);
            }
            case "**" -> {
                if (x == 0.0 && y < 0) {
                    throw new Failure("ZeroDivisionError", "0.0 cannot be raised to a negative power");
                }
                if (x < 0 && y != Math.rint(y) && !Double.isInfinite(y)) {
                    throw new Failure("ValueError", "a negative number raised to a fractional power is complex, which is not supported");
                }
                double r = Math.pow(x, y);
                if (Double.isInfinite(r) && !Double.isInfinite(x) && !Double.isInfinite(y)) {
                    throw new Failure("OverflowError", "(34, 'Numerical result out of range')");
                }
                yield new Float(r);
            }
            default -> throw new IllegalArgumentException(op);
        };
    }

    /** CPython float_divmod. */
    private static double[] floatDivmod(double vx, double wx) {
        double mod = vx % wx;
        double div = (vx - mod) / wx;
        if (mod != 0) {
            if ((wx < 0) != (mod < 0)) {
                mod += wx;
                div -= 1.0;
            }
        } else {
            mod = Math.copySign(0.0, wx);
        }
        double floordiv;
        if (div != 0) {
            floordiv = Math.floor(div);
            if (div - floordiv > 0.5) {
                floordiv += 1.0;
            }
        } else {
            floordiv = Math.copySign(0.0, vx / wx);
        }
        return new double[]{floordiv, mod};
    }

    private static Value bits(String op, Value a, Value b) {
        if (!isIntegral(a) || !isIntegral(b)) {
            throw typeError("unsupported operand type(s) for " + op + ": '" + typeName(a) + "' and '" + typeName(b) + "'");
        }
        BigInteger x = bigInt(a);
        BigInteger y = bigInt(b);
        if (a instanceof Bool p && b instanceof Bool q && !op.equals("<<") && !op.equals(">>")) {
            return Bool.of(switch (op) {
                case "&" -> p.value() & q.value();
                case "|" -> p.value() | q.value();
                default -> p.value() ^ q.value();
            });
        }
        return switch (op) {
            case "&" -> new Int(x.and(y));
            case "|" -> new Int(x.or(y));
            case "^" -> new Int(x.xor(y));
            case "<<", ">>" -> {
                if (y.signum() < 0) {
                    throw new Failure("ValueError", "negative shift count");
                }
                if (op.equals("<<") && y.compareTo(BigInteger.valueOf(MAX_POWER_BITS)) > 0) {
                    throw new Failure("MemoryError", "the shift is too large to compute here");
                }
                int n = y.bitLength() > 31 ? Integer.MAX_VALUE : y.intValue();
                yield new Int(op.equals("<<") ? x.shiftLeft(n) : x.shiftRight(n));
            }
            default -> throw new IllegalArgumentException(op);
        };
    }

    public static Value negate(Value v) {
        return switch (v) {
            case Int i -> new Int(i.value().negate());
            case Bool b -> new Int(bigInt(b).negate());
            case Float f -> new Float(-f.value());
            default -> throw typeError("bad operand type for unary -: '" + typeName(v) + "'");
        };
    }

    public static Value plus(Value v) {
        return switch (v) {
            case Int i -> i;
            case Bool b -> new Int(bigInt(b));
            case Float f -> f;
            default -> throw typeError("bad operand type for unary +: '" + typeName(v) + "'");
        };
    }

    public static Value invert(Value v) {
        if (!isIntegral(v)) {
            throw typeError("bad operand type for unary ~: '" + typeName(v) + "'");
        }
        return new Int(bigInt(v).not());
    }

    // ---- the allowed functions -----------------------------------------------------------------------------------

    public static Value call(String function, List<Value> args) {
        return switch (function) {
            case "abs" -> {
                one(function, args);
                Value v = args.getFirst();
                yield switch (v) {
                    case Int i -> new Int(i.value().abs());
                    case Bool b -> new Int(bigInt(b));
                    case Float f -> new Float(Math.abs(f.value()));
                    default -> throw typeError("bad operand type for abs(): '" + typeName(v) + "'");
                };
            }
            case "len" -> {
                one(function, args);
                Value v = args.getFirst();
                yield new Int(BigInteger.valueOf(switch (v) {
                    case Str s -> s.value().codePointCount(0, s.value().length());
                    case ListV l -> l.items().size();
                    case TupleV t -> t.items().size();
                    case Dict d -> d.entries().size();
                    default -> throw typeError("object of type '" + typeName(v) + "' has no len()");
                }));
            }
            case "bool" -> {
                if (args.size() > 1) {
                    throw typeError("bool expected at most 1 argument, got " + args.size());
                }
                yield Bool.of(!args.isEmpty() && truthy(args.getFirst()));
            }
            case "int" -> {
                if (args.isEmpty()) {
                    yield new Int(BigInteger.ZERO);
                }
                if (args.size() > 1) {
                    throw typeError("int() with a base is not supported here");
                }
                yield toInt(args.getFirst());
            }
            case "float" -> {
                if (args.isEmpty()) {
                    yield new Float(0.0);
                }
                if (args.size() > 1) {
                    throw typeError("float expected at most 1 argument, got " + args.size());
                }
                yield toFloat(args.getFirst());
            }
            case "round" -> round(args);
            case "min", "max" -> extreme(function, args);
            default -> throw new Failure("NameError", "name '" + function + "' is not defined");
        };
    }

    private static void one(String function, List<Value> args) {
        if (args.size() != 1) {
            throw typeError(function + "() takes exactly one argument (" + args.size() + " given)");
        }
    }

    static Value toInt(Value v) {
        return switch (v) {
            case Int i -> i;
            case Bool b -> new Int(bigInt(b));
            case Float f -> {
                if (Double.isNaN(f.value())) {
                    throw new Failure("ValueError", "cannot convert float NaN to integer");
                }
                if (Double.isInfinite(f.value())) {
                    throw new Failure("OverflowError", "cannot convert float infinity to integer");
                }
                yield new Int(new BigDecimal(f.value()).toBigInteger());
            }
            case Str s -> {
                String text = s.value().strip();
                if (!text.matches("[+-]?[0-9](_?[0-9])*")) {
                    throw new Failure("ValueError", "invalid literal for int() with base 10: '" + s.value() + "'");
                }
                yield new Int(new BigInteger(text.replace("_", "").replaceFirst("^\\+", "")));
            }
            default -> throw typeError("int() argument must be a string, a bytes-like object or a real number, not '" + typeName(v) + "'");
        };
    }

    static Value toFloat(Value v) {
        return switch (v) {
            case Float f -> f;
            case Int i -> new Float(intToDouble(i.value()));
            case Bool b -> new Float(b.value() ? 1.0 : 0.0);
            case Str s -> new Float(parseFloat(s.value()));
            default -> throw typeError("float() argument must be a string or a real number, not '" + typeName(v) + "'");
        };
    }

    /** Python {@code float(str)}: white space stripped, underscores between digits, inf, infinity and nan in any case. */
    public static double parseFloat(String raw) {
        String text = raw.strip();
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String body = lower.startsWith("+") || lower.startsWith("-") ? lower.substring(1) : lower;
        boolean negative = lower.startsWith("-");
        if (body.equals("inf") || body.equals("infinity")) {
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (body.equals("nan")) {
            return Double.NaN;
        }
        if (!text.matches("[+-]?(([0-9](_?[0-9])*)?\\.[0-9](_?[0-9])*|[0-9](_?[0-9])*\\.?)([eE][+-]?[0-9](_?[0-9])*)?")) {
            throw new Failure("ValueError", "could not convert string to float: '" + raw + "'");
        }
        return Double.parseDouble(text.replace("_", ""));
    }

    private static Value round(List<Value> args) {
        if (args.isEmpty() || args.size() > 2) {
            throw typeError("round() takes 1 or 2 arguments (" + args.size() + " given)");
        }
        Value x = args.getFirst();
        if (args.size() == 2 && args.get(1) != None.NONE) {
            Value n = args.get(1);
            if (!isIntegral(n)) {
                throw typeError("'" + typeName(n) + "' object cannot be interpreted as an integer");
            }
            int digits = bigInt(n).max(BigInteger.valueOf(-400)).min(BigInteger.valueOf(400)).intValue();
            return switch (x) {
                case Int i -> digits >= 0 ? i : new Int(new BigDecimal(i.value()).setScale(digits, RoundingMode.HALF_EVEN).toBigInteger());
                case Bool b -> new Int(bigInt(b));
                case Float f -> {
                    if (Double.isNaN(f.value()) || Double.isInfinite(f.value())) {
                        yield f;
                    }
                    yield new Float(new BigDecimal(f.value()).setScale(digits, RoundingMode.HALF_EVEN).doubleValue());
                }
                default -> throw typeError("type " + typeName(x) + " doesn't define __round__ method");
            };
        }
        return switch (x) {
            case Int i -> i;
            case Bool b -> new Int(bigInt(b));
            case Float f -> {
                if (Double.isNaN(f.value())) {
                    throw new Failure("ValueError", "cannot convert float NaN to integer");
                }
                if (Double.isInfinite(f.value())) {
                    throw new Failure("OverflowError", "cannot convert float infinity to integer");
                }
                yield new Int(new BigDecimal(f.value()).setScale(0, RoundingMode.HALF_EVEN).toBigIntegerExact());
            }
            default -> throw typeError("type " + typeName(x) + " doesn't define __round__ method");
        };
    }

    private static Value extreme(String function, List<Value> args) {
        List<Value> items;
        if (args.size() == 1) {
            items = iterate(args.getFirst());
        } else if (args.isEmpty()) {
            throw typeError(function + " expected at least 1 argument, got 0");
        } else {
            items = args;
        }
        if (items.isEmpty()) {
            throw new Failure("ValueError", function + "() iterable argument is empty");
        }
        Value best = items.getFirst();
        for (int i = 1; i < items.size(); i++) {
            Value item = items.get(i);
            if (function.equals("min") ? order("<", item, best) : order(">", item, best)) {
                best = item;
            }
        }
        return best;
    }

    private static List<Value> iterate(Value v) {
        return switch (v) {
            case Str s -> s.value().codePoints().mapToObj(cp -> (Value) new Str(new String(Character.toChars(cp)))).toList();
            case ListV l -> l.items();
            case TupleV t -> t.items();
            case Dict d -> List.copyOf(d.entries().keySet());
            default -> throw typeError("'" + typeName(v) + "' object is not iterable");
        };
    }

    // ---- attributes and subscripts -------------------------------------------------------------------------------

    public static Value attribute(Value target, String name) {
        if (target instanceof Namespace n) {
            Value v = n.attributes().get(name);
            if (v == null) {
                throw new Failure("AttributeError", "'types.SimpleNamespace' object has no attribute '" + name + "' (sheet "
                        + n.sheet() + ")");
            }
            return v;
        }
        throw new Failure("AttributeError", "'" + typeName(target) + "' object has no attribute '" + name + "'");
    }

    public static Value subscript(Value target, Value index) {
        if (target instanceof Dict d) {
            Value v = lookup(d, index);
            if (v == null) {
                throw new Failure("KeyError", String.valueOf(index));
            }
            return v;
        }
        List<Value> items = switch (target) {
            case Str s -> iterate(s);
            case ListV l -> l.items();
            case TupleV t -> t.items();
            default -> throw typeError("'" + typeName(target) + "' object is not subscriptable");
        };
        if (!isIntegral(index)) {
            throw typeError(typeName(target) + " indices must be integers, not " + typeName(index));
        }
        BigInteger i = bigInt(index);
        BigInteger size = BigInteger.valueOf(items.size());
        if (i.signum() < 0) {
            i = i.add(size);
        }
        if (i.signum() < 0 || i.compareTo(size) >= 0) {
            throw new Failure("IndexError", typeName(target) + " index out of range");
        }
        return items.get(i.intValue());
    }
}
