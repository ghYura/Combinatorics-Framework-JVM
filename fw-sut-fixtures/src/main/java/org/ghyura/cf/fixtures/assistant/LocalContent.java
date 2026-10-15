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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/** Original finite JSON content conversion, including ignored carriers and Python scalar/container formatting. */
public final class LocalContent {
 public static final int MAX_CHARS=65536,MAX_ATOMS=16384,MAX_DEPTH=64;
 private LocalContent(){ }
 public static String text(Object value){var renderer=new Renderer();return renderer.content(value,0);}
 private static final class Renderer {
  private int atoms;
  private void admit(int depth){if(depth>MAX_DEPTH||++atoms>MAX_ATOMS)throw new IllegalArgumentException("content exceeds depth/atom bounds");}
  private String bounded(String value){for(int at=0;at<value.length();){int cp=value.codePointAt(at);if(cp>=0xd800&&cp<=0xdfff)throw new IllegalArgumentException("valid Unicode scalar content required");at+=Character.charCount(cp);}if(value.length()>MAX_CHARS)throw new IllegalArgumentException("converted content exceeds64Ki characters");return value;}
  private void append(StringBuilder out,String text){if(out.length()>MAX_CHARS-text.length())throw new IllegalArgumentException("converted content exceeds64Ki characters");out.append(text);}
  private String content(Object value,int depth){
   admit(depth);if(value==null)return "";if(value instanceof String s)return bounded(s);if(value instanceof List<?> list){var out=new StringBuilder();boolean first=true;for(Object item:list){admit(depth+1);String part="";if(item instanceof Map<?,?> map){Object type=map.get("type");if(Objects.equals(type,"text")||Objects.equals(type,"input_text"))part=string(map.containsKey("text")?map.get("text"):"",depth+1);else if(map.containsKey("content"))part=content(map.get("content"),depth+1);}else part=string(item,depth+1);if(!part.isEmpty()){if(!first)append(out,"\n");append(out,part);first=false;}}return out.toString();}if(value instanceof Map<?,?> map)return map.containsKey("text")?string(map.get("text"),depth+1):json(map,depth+1);return string(value,depth+1);
  }
  private String string(Object value,int depth){if(value instanceof String s){admit(depth);return bounded(s);}return repr(value,depth);}
  private String scalar(Object value,boolean json){
   if(value==null)return json?"null":"None";if(value instanceof Boolean b)return json?b.toString():b?"True":"False";if(value instanceof Byte||value instanceof Integer||value instanceof Long)return value.toString();if(value instanceof Double d)return floating(d);if(value instanceof BigDecimal d)return d.scale()==0?integral(d):floating(d.doubleValue());throw new IllegalArgumentException("finite JSON scalar required");
  }
  private String repr(Object value,int depth){admit(depth);if(value instanceof String s)return quote(s,false);if(value instanceof List<?> list){var out=new StringBuilder("[");for(int i=0;i<list.size();i++){if(i>0)append(out,", ");append(out,repr(list.get(i),depth+1));}append(out,"]");return out.toString();}if(value instanceof Map<?,?> map){var out=new StringBuilder("{");boolean first=true;for(var item:map.entrySet()){if(!(item.getKey()instanceof String key))throw new IllegalArgumentException("JSON string keys required");if(!first)append(out,", ");append(out,quote(key,false));append(out,": ");append(out,repr(item.getValue(),depth+1));first=false;}append(out,"}");return out.toString();}return bounded(scalar(value,false));}
  private String json(Object value,int depth){admit(depth);if(value instanceof String s)return quote(s,true);if(value instanceof List<?> list){var out=new StringBuilder("[");for(int i=0;i<list.size();i++){if(i>0)append(out,", ");append(out,json(list.get(i),depth+1));}append(out,"]");return out.toString();}if(value instanceof Map<?,?> map){var out=new StringBuilder("{");boolean first=true;for(var item:map.entrySet()){if(!(item.getKey()instanceof String key))throw new IllegalArgumentException("JSON string keys required");if(!first)append(out,", ");append(out,quote(key,true));append(out,": ");append(out,json(item.getValue(),depth+1));first=false;}append(out,"}");return out.toString();}return bounded(scalar(value,true));}
  private String quote(String value,boolean json){
   bounded(value);char delimiter=json?'"':value.indexOf('\'')>=0&&value.indexOf('"')<0?'"':'\'';var out=new StringBuilder();out.append(delimiter);for(int at=0;at<value.length();){int cp=value.codePointAt(at);at+=Character.charCount(cp);String part;if(cp==delimiter||cp=='\\')part="\\"+(char)cp;else if(cp=='\n')part="\\n";else if(cp=='\r')part="\\r";else if(cp=='\t')part="\\t";else if(json&&cp=='\b')part="\\b";else if(json&&cp=='\f')part="\\f";else if(json&&cp<32)part=String.format(Locale.ROOT,"\\u%04x",cp);else if(!json&&!printable(cp))part=String.format(Locale.ROOT,cp<=255?"\\x%02x":cp<=65535?"\\u%04x":"\\U%08x",cp);else part=new String(Character.toChars(cp));append(out,part);}append(out,Character.toString(delimiter));return out.toString();
  }
 }
 private static boolean printable(int cp){if(cp==32)return true;return switch(Character.getType(cp)){case Character.CONTROL,Character.FORMAT,Character.SURROGATE,Character.PRIVATE_USE,Character.UNASSIGNED,Character.SPACE_SEPARATOR,Character.LINE_SEPARATOR,Character.PARAGRAPH_SEPARATOR->false;default->true;};}
 private static String integral(BigDecimal value){try{return Long.toString(value.longValueExact());}catch(ArithmeticException e){throw new IllegalArgumentException("signed64 JSON integer required",e);}}
 private static String floating(double value){
  if(!Double.isFinite(value))throw new IllegalArgumentException("finite JSON float required");if(value==0)return Double.doubleToRawLongBits(value)<0?"-0.0":"0.0";var exact=new BigDecimal(value);BigDecimal shortest=null;for(int digits=1;digits<=17;digits++){var rounded=exact.round(new MathContext(digits,RoundingMode.HALF_EVEN));if(Double.doubleToRawLongBits(rounded.doubleValue())==Double.doubleToRawLongBits(value)){shortest=rounded.stripTrailingZeros();break;}}if(shortest==null)throw new IllegalStateException("binary64 shortest decimal missing");int exponent=shortest.precision()-shortest.scale()-1;if(exponent>=-4&&exponent<16){String text=shortest.toPlainString();return text.indexOf('.')<0?text+".0":text;}String mantissa=shortest.movePointLeft(exponent).toPlainString();return mantissa+"e"+(exponent>=0?"+":"-")+String.format(Locale.ROOT,"%02d",Math.abs(exponent));
 }
}
