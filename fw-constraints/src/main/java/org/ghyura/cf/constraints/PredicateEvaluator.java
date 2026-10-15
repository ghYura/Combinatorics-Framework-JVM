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

import org.ghyura.cf.model.rule.Expr;
import org.ghyura.cf.model.rule.ExprParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a parsed {@code when} predicate the way {@code eval(expr, {"__builtins__": {}}, {**_SAFE_FUNCS, **ns})}
 * did: names resolve to the sheets' namespaces first, then to the allowed functions; {@code and}/{@code or} return
 * their deciding operand; comparison chains stop at the first false link; the result's truth is the predicate's.
 */
public final class PredicateEvaluator {

    private PredicateEvaluator() {
    }

    /** Python {@code bool(eval(expr, ...))}. @throws Py.Failure with the Python exception the legacy would have raised */
    public static boolean holds(Expr expr, Map<String, Py.Value> names) {
        return Py.truthy(evaluate(expr, names));
    }

    public static Py.Value evaluate(Expr expr, Map<String, Py.Value> names) {
        return switch (expr) {
            case Expr.Constant c -> Py.of(c.value());
            case Expr.Name n -> resolve(n.name(), names);
            case Expr.Attribute a -> Py.attribute(evaluate(a.target(), names), a.name());
            case Expr.Call c -> {
                Py.Value callee = resolve(c.function(), names);
                if (!(callee instanceof Py.Function f)) {
                    throw Py.typeError("'" + Py.typeName(callee) + "' object is not callable");
                }
                List<Py.Value> args = new ArrayList<>(c.args().size());
                for (Expr arg : c.args()) {
                    args.add(evaluate(arg, names));
                }
                yield Py.call(f.name(), args);
            }
            case Expr.Subscript s -> Py.subscript(evaluate(s.target(), names), evaluate(s.index(), names));
            case Expr.Unary u -> {
                Py.Value v = evaluate(u.operand(), names);
                yield switch (u.op()) {
                    case NEGATE -> Py.negate(v);
                    case PLUS -> Py.plus(v);
                    case INVERT -> Py.invert(v);
                };
            }
            case Expr.Binary b -> Py.binary(b.op().symbol(), evaluate(b.left(), names), evaluate(b.right(), names));
            case Expr.Compare c -> {
                Py.Value left = evaluate(c.first(), names);
                for (int i = 0; i < c.ops().size(); i++) {
                    Py.Value right = evaluate(c.rest().get(i), names);
                    if (!compare(c.ops().get(i), left, right)) {
                        yield Py.Bool.FALSE;
                    }
                    left = right;
                }
                yield Py.Bool.TRUE;
            }
            case Expr.And a -> {
                Py.Value last = null;
                for (Expr operand : a.operands()) {
                    last = evaluate(operand, names);
                    if (!Py.truthy(last)) {
                        yield last;
                    }
                }
                yield last;
            }
            case Expr.Or o -> {
                Py.Value last = null;
                for (Expr operand : o.operands()) {
                    last = evaluate(operand, names);
                    if (Py.truthy(last)) {
                        yield last;
                    }
                }
                yield last;
            }
            case Expr.Not n -> Py.Bool.of(!Py.truthy(evaluate(n.operand(), names)));
            case Expr.IfElse i -> Py.truthy(evaluate(i.test(), names)) ? evaluate(i.then(), names) : evaluate(i.otherwise(), names);
            case Expr.ListDisplay l -> new Py.ListV(l.items().stream().map(x -> evaluate(x, names)).toList());
            case Expr.TupleDisplay t -> new Py.TupleV(t.items().stream().map(x -> evaluate(x, names)).toList());
        };
    }

    private static Py.Value resolve(String name, Map<String, Py.Value> names) {
        Py.Value v = names.get(name);
        if (v != null) {
            return v;
        }
        if (ExprParser.FUNCTIONS.contains(name)) {
            return new Py.Function(name);
        }
        throw new Py.Failure("NameError", "name '" + name + "' is not defined");
    }

    private static boolean compare(Expr.CompareOp op, Py.Value a, Py.Value b) {
        return switch (op) {
            case EQUAL -> Py.eq(a, b);
            case NOT_EQUAL -> !Py.eq(a, b);
            case LESS -> Py.order("<", a, b);
            case LESS_EQUAL -> Py.order("<=", a, b);
            case GREATER -> Py.order(">", a, b);
            case GREATER_EQUAL -> Py.order(">=", a, b);
            case IN -> Py.contains(b, a);
            case NOT_IN -> !Py.contains(b, a);
            case IS -> Py.is(a, b);
            case IS_NOT -> !Py.is(a, b);
        };
    }
}
