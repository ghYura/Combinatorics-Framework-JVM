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

package org.ghyura.cf.fixtures.assistant;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/** Shared app2/app3 arithmetic with bounded exact integers and explicit admission refusals. */
public final class LocalArithmetic {
    public static final int MAX_TEXT=12000, MAX_EXPRESSION=120, MAX_INTEGER_BITS=4096;
    private static final Pattern CANDIDATES=Pattern.compile("[0-9\\s\\x1c-\\x1f.\\+\\-*/%()]+",Pattern.UNICODE_CHARACTER_CLASS);
    private LocalArithmetic(){ }
    public enum Disposition { VALUE, NO_RESULT, RESOURCE_REFUSED }
    public record Result(Optional<String> expression,OptionalDouble value,Disposition disposition){
        public Result{Objects.requireNonNull(expression);Objects.requireNonNull(value);Objects.requireNonNull(disposition);}
    }
    public static Optional<String> extractExpression(String text){
        Objects.requireNonNull(text);if(text.length()>MAX_TEXT)throw new IllegalArgumentException("arithmetic text exceeds12000UTF16units");
        var matcher=CANDIDATES.matcher(text.toLowerCase(Locale.ROOT).replace("^","**"));String best=null;
        while(matcher.find()){String candidate=strip(matcher.group());
            if(!candidate.chars().anyMatch(c->c>='0'&&c<='9')||!candidate.chars().anyMatch(c->"+-*/%".indexOf(c)>=0))continue;
            if(best==null||candidate.length()>best.length())best=candidate;
        }return best==null||best.length()>MAX_EXPRESSION?Optional.empty():Optional.of(best);
    }
    public static Result evaluate(String text){
        Objects.requireNonNull(text);if(text.length()>MAX_TEXT)return new Result(Optional.empty(),OptionalDouble.empty(),Disposition.RESOURCE_REFUSED);
        var expression=extractExpression(text);if(expression.isEmpty())return new Result(expression,OptionalDouble.empty(),Disposition.NO_RESULT);
        try{double value=new Parser(expression.orElseThrow()).parse().asDouble();boolean finite=Double.isFinite(value);return new Result(expression,finite?OptionalDouble.of(value):OptionalDouble.empty(),finite?Disposition.VALUE:Disposition.NO_RESULT);}
        catch(AdmissionRefusal e){return new Result(expression,OptionalDouble.empty(),Disposition.RESOURCE_REFUSED);}
        catch(ArithmeticException|IllegalArgumentException e){return new Result(expression,OptionalDouble.empty(),Disposition.NO_RESULT);}
    }
    private static boolean space(int cp){return Character.isWhitespace(cp)||Character.isSpaceChar(cp)||cp==0x85;}
    private static String strip(String s){int a=0,b=s.length();while(a<b&&space(s.codePointAt(a)))a+=Character.charCount(s.codePointAt(a));while(b>a&&space(s.codePointBefore(b)))b-=Character.charCount(s.codePointBefore(b));return s.substring(a,b);}
    private static final class AdmissionRefusal extends RuntimeException{private static final long serialVersionUID=1L;}
    private record Value(BigInteger integer,double floating){
        static Value of(BigInteger n){if(n.abs().bitLength()>MAX_INTEGER_BITS)throw new AdmissionRefusal();return new Value(n,0);}
        static Value of(double n){return new Value(null,n);}
        boolean exact(){return integer!=null;}
        double asDouble(){double d=exact()?integer.doubleValue():floating;if(exact()&&!Double.isFinite(d))throw new ArithmeticException("integer conversion overflows binary64");return d;}
        Value negate(){return exact()?of(integer.negate()):of(-floating);}
    }
    private static Value binary(String op,Value a,Value b){
        if(a.exact()&&b.exact()){
            BigInteger x=a.integer(),y=b.integer();switch(op){
                case "+":return Value.of(x.add(y));case "-":return Value.of(x.subtract(y));
                case "*":if(x.signum()!=0&&y.signum()!=0&&x.abs().bitLength()+y.abs().bitLength()-1>MAX_INTEGER_BITS)throw new AdmissionRefusal();return Value.of(x.multiply(y));
                case "/":return Value.of(ratio(x,y));
                case "//","%":{var qr=x.divideAndRemainder(y);if(qr[1].signum()!=0&&x.signum()!=y.signum()){qr[0]=qr[0].subtract(BigInteger.ONE);qr[1]=qr[1].add(y);}return Value.of(op.equals("//")?qr[0]:qr[1]);}
                case "**":if(y.signum()>=0){
                    if(y.signum()==0)return Value.of(BigInteger.ONE);if(x.signum()==0)return Value.of(BigInteger.ZERO);
                    if(x.abs().equals(BigInteger.ONE))return Value.of(x.signum()<0&&y.testBit(0)?BigInteger.ONE.negate():BigInteger.ONE);
                    if(y.bitLength()>31||y.longValue()*x.abs().bitLength()>MAX_INTEGER_BITS)throw new AdmissionRefusal();return Value.of(x.pow(y.intValueExact()));
                }break;
                default:throw new IllegalArgumentException("unknown arithmetic operator");
            }
        }
        double x=a.asDouble(),y=b.asDouble();return Value.of(switch(op){
            case "+"->x+y;case "-"->x-y;case "*"->x*y;case "/"->{nonzero(y);yield x/y;}
            case "%","//"->{nonzero(y);double mod=x%y,div=(x-mod)/y;if(mod!=0){if((mod<0)!=(y<0)){mod+=y;div-=1;}}else mod=Math.copySign(0,y);double floor=div!=0?Math.floor(div):Math.copySign(0,x/y);if(div!=0&&div-floor>.5)floor++;yield op.equals("%")?mod:floor;}
            case "**"->{
                if(x==0&&y<0)throw new ArithmeticException("zero to negative power");
                if(x==1||Double.isInfinite(y)&&Math.abs(x)==1)yield 1.;
                // A complex intermediate remains complex through all permitted operations: the source rejects it.
                if(x<0&&Double.isFinite(x)&&Double.isFinite(y)&&Math.rint(y)!=y)throw new ArithmeticException("complex power result");
                double power=StrictMath.pow(x,y);
                if(Double.isFinite(x)&&Double.isFinite(y)&&Double.isInfinite(power))throw new ArithmeticException("floating power overflow");
                yield power;
            }
            default->throw new IllegalArgumentException("unknown arithmetic operator");
        });
    }
    private static void nonzero(double d){if(d==0)throw new ArithmeticException("division by zero");}
    /** Round the rational directly to nearest binary64, ties to even, including signed subnormal results. */
    private static double ratio(BigInteger numerator,BigInteger denominator){
        if(denominator.signum()==0)throw new ArithmeticException("division by zero");boolean negative=numerator.signum()<0^denominator.signum()<0;BigInteger a=numerator.abs(),b=denominator.abs();if(a.signum()==0)return negative?-0.:0.;
        int exponent=a.bitLength()-b.bitLength();if((exponent>=0?a.compareTo(b.shiftLeft(exponent)):a.shiftLeft(-exponent).compareTo(b))<0)exponent--;
        if(exponent>1023)throw new ArithmeticException("integer ratio overflows binary64");if(exponent< -1075)return negative?-0.:0.;
        int shift=Math.max(-1074,exponent-52);var qr=shift<0?a.shiftLeft(-shift).divideAndRemainder(b):a.divideAndRemainder(b.shiftLeft(shift));BigInteger divisor=shift<0?b:b.shiftLeft(shift);int rounding=qr[1].shiftLeft(1).compareTo(divisor);if(rounding>0||rounding==0&&qr[0].testBit(0))qr[0]=qr[0].add(BigInteger.ONE);
        double d=Math.scalb(qr[0].doubleValue(),shift);if(!Double.isFinite(d))throw new ArithmeticException("integer ratio overflows binary64");return negative?-d:d;
    }
    private static final class Parser{
        private final String source;private int offset,depth;Parser(String source){this.source=source;}
        Value parse(){Value v=sum();skip();if(offset!=source.length())throw new IllegalArgumentException("invalid arithmetic syntax");return v;}
        Value sum(){Value v=product();while(true){if(take("+"))v=binary("+",v,product());else if(take("-"))v=binary("-",v,product());else return v;}}
        Value product(){Value v=factor();while(true){skip();if(source.startsWith("**",offset))return v;if(take("//"))v=binary("//",v,factor());else if(take("*"))v=binary("*",v,factor());else if(take("/"))v=binary("/",v,factor());else if(take("%"))v=binary("%",v,factor());else return v;}}
        Value factor(){if(take("+"))return factor();if(take("-"))return factor().negate();Value v=atom();return take("**")?binary("**",v,factor()):v;}
        Value atom(){
            if(take("(")){depth++;Value v=sum();if(!take(")"))throw new IllegalArgumentException("missing closing parenthesis");depth--;return v;}
            skip();int start=offset;boolean digit=false,dot=false;while(offset<source.length()){char c=source.charAt(offset);if(c>='0'&&c<='9'){digit=true;offset++;}else if(c=='.'&&!dot){dot=true;offset++;}else break;}
            if(!digit)throw new IllegalArgumentException("numeric literal required");String s=source.substring(start,offset);if(dot)return Value.of(Double.parseDouble(s));if(s.length()>1&&s.charAt(0)=='0'&&s.chars().anyMatch(c->c!='0'))throw new IllegalArgumentException("leading zeros in integer literal");return Value.of(new BigInteger(s));
        }
        boolean take(String token){skip();if(!source.startsWith(token,offset))return false;offset+=token.length();return true;}
        void skip(){while(offset<source.length()){char c=source.charAt(offset);if(c==' '||c=='\t'||c=='\f'||depth>0&&(c=='\n'||c=='\r'))offset++;else break;}}
    }
}
