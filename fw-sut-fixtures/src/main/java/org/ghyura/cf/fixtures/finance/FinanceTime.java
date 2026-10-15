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
import org.ghyura.cf.fixtures.catalogpub.PythonValues;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
/** Live local time units, optionally kept in the source's properties file. Source ordered, partially applied invalid updates are retained. */
public final class FinanceTime {
 public static final List<String> FIELDS=List.of("replay_window_seconds","confirmation_ttl_seconds","risk_window_seconds","peer_ping_interval_seconds","peer_reconnect_delay_seconds","peer_unavailable_after_seconds");
 static final String HEADER="# finance_stack time units (seconds) — edited live via PUT /v1/time-config";
 private final Map<String,Integer> values=new LinkedHashMap<>();private boolean accept=true;private final Path path;
 public FinanceTime(){this(null);}
 private FinanceTime(Path path){int[] defaults={300,3600,60,5,2,30};for(int i=0;i<FIELDS.size();i++)values.put(FIELDS.get(i),defaults[i]);this.path=path;}
 public int seconds(String field){var n=values.get(field);if(n==null)throw validation("unknown time unit '"+field+"'","field",field,"allowed",FIELDS);return n;}
 public Map<String,Integer> update(Map<String,?> input){var changed=new LinkedHashMap<String,Integer>();for(var e:input.entrySet()){String k=e.getKey();seconds(k);int n=positive(e.getValue(),k);values.put(k,n);changed.put(k,n);}return Collections.unmodifiableMap(changed);}
 /** Source _pos_int, int(str(value).strip()): signs, Unicode digits and single underscores; seconds past Java int are refused rather than reported invalid. */
 static int positive(Object value,String field){
  if(value instanceof String s&&s.length()>64)throw new IllegalArgumentException("time unit literal too long");
  // FinanceKernel.strip also removes U+0085,which Python counts as whitespace.
  BigInteger n;try{n=PythonValues.integer(value instanceof String s?strip(s):value);}catch(PythonValues.PythonError e){throw validation(field+" must be an integer number of seconds","field",field);}
  if(n.signum()<=0)throw validation(field+" must be a positive number of seconds","field",field);
  if(n.bitLength()>31)throw new IllegalArgumentException(field+" exceeds the admitted int seconds");
  return n.intValue();
 }
 public void acceptSync(boolean value){accept=value;}public boolean acceptSync(){return accept;}
 public Path path(){return path;}
 public Map<String,Object> view(){var out=new LinkedHashMap<String,Object>(values);out.put("accept_time_sync",accept);out.put("path",path==null?null:path.toString());return out;}
 /** Source TimeConfig.load: a missing file is created with the defaults;present known units and accept_time_sync override them. Bounded: 64KiB UTF-8. */
 public static FinanceTime load(Path path){
  var time=new FinanceTime(Objects.requireNonNull(path));
  try{
   if(Files.exists(path)){
    if(Files.size(path)>65536)throw new IllegalArgumentException("time config exceeds64KiB");
    String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();}
    catch(CharacterCodingException e){throw new IllegalArgumentException("time config is not UTF-8",e);}
    var props=new LinkedHashMap<String,String>();
    for(String raw:text.replace("\r\n","\n").replace('\r','\n').split("\n",-1)){String line=strip(raw);if(line.isEmpty()||line.startsWith("#")||!line.contains("="))continue;int i=line.indexOf('=');props.put(strip(line.substring(0,i)),strip(line.substring(i+1)));}
    for(String field:FIELDS)if(props.containsKey(field))time.values.put(field,positive(props.get(field),field));
    if(props.containsKey("accept_time_sync"))time.accept=Set.of("1","true","yes","on").contains(props.get("accept_time_sync").toLowerCase(Locale.ROOT));
   }else{Files.createDirectories(path.toAbsolutePath().getParent());time.save();}
  }catch(IOException e){throw new UncheckedIOException(e);}
  return time;
 }
 /** Source save: the header, every unit as "key = value" and accept_time_sync in lower case, written through a sibling .tmp file. */
 public void save(){
  if(path==null)return;
  var out=new StringBuilder(HEADER).append('\n');for(String field:FIELDS)out.append(field).append(" = ").append(values.get(field)).append('\n');out.append("accept_time_sync = ").append(accept).append('\n');
  Path tmp=path.resolveSibling(path.getFileName()+".tmp");
  try{Files.writeString(tmp,out,StandardCharsets.UTF_8);Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(IOException e){throw new UncheckedIOException(e);}
 }
}
