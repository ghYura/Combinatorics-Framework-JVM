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

package org.ghyura.cf.fixtures.finance;

import org.ghyura.cf.model.Json;
import org.ghyura.cf.fixtures.assistant.LocalContent;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.DoubleSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/** Source raw-body HMAC and permanent in-session nonce replay semantics, with bounded local admission. */
public final class FinanceSecurity {
    public static final int MAX_BYTES=65536,MAX_NONCES=4096;
    private FinanceSecurity() { }
    public record Verification(boolean ok,String reason) { }
    private static byte[] utf8(String value) { return text(value).getBytes(StandardCharsets.UTF_8); }
    private static void body(byte[] value) { Objects.requireNonNull(value);if(value.length>MAX_BYTES)throw new IllegalArgumentException("security body exceeds64KiB"); }
    public static String digest(byte[] value) {
        body(value);try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String sign(String secret,String method,String path,byte[] body,String timestamp,String nonce) {
        String canonical=text(timestamp)+"\n"+text(nonce)+"\n"+text(method).toUpperCase(Locale.ROOT)+"\n"+text(path)+"\n"+digest(body);
        try {
            var mac=Mac.getInstance("HmacSHA256");byte[] key=utf8(secret);
            // HMAC pads an empty key with zeros;the JDK key object requires a nonempty array.
            mac.init(new SecretKeySpec(key.length==0?new byte[1]:key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    private static BigInteger timestamp(String input) {
        String value=strip(input);if(value.length()>128)throw new IllegalArgumentException("timestamp exceeds128 units");
        if(!value.matches("[+-]?\\p{Nd}+(?:_\\p{Nd}+)*"))throw new NumberFormatException();
        var ascii=new StringBuilder();for(int cp:value.codePoints().toArray()) {
            if(cp=='_')continue;if(cp=='+'||cp=='-')ascii.append((char)cp);else ascii.append(Character.digit(cp,10));
        }
        return new BigInteger(ascii.toString());
    }
    public static Verification verify(String secret,String method,String path,byte[] body,String timestamp,String nonce,
                                      String signature,int window,Set<String> seen,DoubleSupplier seconds) {
        if(timestamp==null||timestamp.isEmpty()||nonce==null||nonce.isEmpty()||signature==null||signature.isEmpty())
            return new Verification(false,"missing_auth_headers");
        BigInteger ts;try{ts=timestamp(timestamp);}catch(NumberFormatException e){return new Verification(false,"invalid_timestamp");}
        double now=seconds.getAsDouble();if(!Double.isFinite(now)||Math.abs(now)>1e12)throw new IllegalArgumentException("finite bounded seconds required");
        if(BigInteger.valueOf((long)now).subtract(ts).abs().compareTo(BigInteger.valueOf(window))>0)
            return new Verification(false,"timestamp_out_of_window");
        text(signature);if(!signature.chars().allMatch(c->c<128))throw new IllegalArgumentException("ASCII signature admission required");
        String expected=sign(secret,method,path,body,timestamp,nonce);
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))
            return new Verification(false,"bad_signature");
        if(seen!=null) {
            if(seen.contains(nonce))return new Verification(false,"nonce_replayed");
            if(seen.size()>=MAX_NONCES)throw new IllegalArgumentException("nonce replay state exceeds4096");seen.add(nonce);
        }
        return new Verification(true,"ok");
    }
    public static byte[] canonicalBytes(Object payload) {
        var encoder=new Encoder();String value=encoder.json(payload,0);byte[] bytes=value.getBytes(StandardCharsets.UTF_8);body(bytes);return bytes;
    }
    private static int codepointOrder(String a,String b) {
        int i=0,j=0;while(i<a.length()&&j<b.length()){int x=a.codePointAt(i),y=b.codePointAt(j);if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}return Integer.compare(a.length()-i,b.length()-j);
    }
    private static final class Encoder {
        private int atoms;
        private void append(StringBuilder out,String value) { if(out.length()>MAX_BYTES-value.length())throw new IllegalArgumentException("canonical JSON exceeds64KiB");out.append(value); }
        private String quote(String value) { LocalContent.text(value);return Json.write(value).strip(); }
        String json(Object value,int depth) {
            if(depth>64||++atoms>16384)throw new IllegalArgumentException("canonical JSON depth/atom bound exceeded");
            if(value==null)return "null";if(value instanceof String s)return quote(s);
            if(value instanceof Boolean b)return b.toString();
            if(value instanceof Byte||value instanceof Integer||value instanceof Long||value instanceof Double)return LocalContent.text(value);
            if(value instanceof List<?> list) {
                var out=new StringBuilder("[");for(int i=0;i<list.size();i++){if(i>0)append(out,",");append(out,json(list.get(i),depth+1));}append(out,"]");return out.toString();
            }
            if(value instanceof Map<?,?> map) {
                var sorted=new TreeMap<String,Object>(FinanceSecurity::codepointOrder);
                for(var e:map.entrySet()){if(!(e.getKey() instanceof String k))throw new IllegalArgumentException("JSON string keys required");sorted.put(k,e.getValue());}
                var out=new StringBuilder("{");boolean first=true;for(var e:sorted.entrySet()){if(++atoms>16384)throw new IllegalArgumentException("canonical JSON atom bound exceeded");if(!first)append(out,",");append(out,quote(e.getKey()));append(out,":");append(out,json(e.getValue(),depth+1));first=false;}append(out,"}");return out.toString();
            }
            throw new IllegalArgumentException("finite JSON scalar/list/string-keyed object required");
        }
    }
}
