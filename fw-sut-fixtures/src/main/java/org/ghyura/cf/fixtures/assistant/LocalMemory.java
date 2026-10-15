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

import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Bounded lexical memory, with original seed data, IDs, stable rounded ranking and snippets. */
public final class LocalMemory {
    public static final int MAX_DOCUMENTS=256,MAX_TEXT=12000,MAX_SNAPSHOT=16*1024*1024;
    private static final Pattern WORD=Pattern.compile("[A-Za-zА-Яа-яЁё0-9_']+");
    private static final Pattern SENTENCES=Pattern.compile("(?<=[.!?])[\\s\\x1c-\\x1f]+|\\n+",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Map<?,?> LITERALS=literals();
    private static final Set<String> STOP=Set.copyOf(strings(LITERALS.get("stop_words")));
    private final Clock clock;private List<Document> documents;
    public record Input(String title,String text,List<String> tags){
        public Input{Objects.requireNonNull(title);Objects.requireNonNull(text);tags=List.copyOf(tags);if(title.isEmpty()||length(title)>160||text.isEmpty()||length(text)>MAX_TEXT||tags.size()>16||tags.stream().anyMatch(t->length(t)>160))throw new IllegalArgumentException("local memory document exceeds admitted bounds");}
    }
    public record Document(String id,String title,String text,List<String> tags,long created){
        public Document{Objects.requireNonNull(id);Objects.requireNonNull(title);Objects.requireNonNull(text);tags=List.copyOf(tags);}
        public Map<String,Object> fields(){return Collections.unmodifiableMap(new TreeMap<>(Map.of("id",id,"title",title,"text",text,"tags",tags,"created",created)));}
    }
    public record Match(String id,String title,double score,String snippet,List<String> tags){
        public Match{tags=List.copyOf(tags);}public Map<String,Object> fields(){return Collections.unmodifiableMap(new TreeMap<>(Map.of("id",id,"title",title,"score",score,"snippet",snippet,"tags",tags)));}
    }
    public LocalMemory(Clock clock){
        this.clock=Objects.requireNonNull(clock);var seeds=new ArrayList<Document>();for(Object item:(List<?>)LITERALS.get("seeds")){var raw=(Map<?,?>)item;String title=(String)raw.get("title"),text=(String)raw.get("text");seeds.add(new Document(documentId(title,text),title,text,strings(raw.get("tags")),clock.instant().getEpochSecond()));}documents=List.copyOf(seeds);
    }
    public synchronized List<Document> documents(){return documents;}
    public synchronized int count(){return documents.size();}
    /** Validate the whole batch before publishing a mutation. IDs use raw input, as in the original ingest path. */
    public synchronized List<Document> addDocuments(List<Input> incoming){
        incoming=List.copyOf(incoming);if(incoming.isEmpty()||incoming.size()>64)throw new IllegalArgumentException("ingest needs1..64documents");var ids=new HashSet<String>();documents.forEach(d->ids.add(d.id()));var added=new ArrayList<Document>();
        for(Input input:incoming){String id=documentId(input.title(),input.text());if(!ids.add(id))continue;added.add(new Document(id,strip(input.title()),strip(input.text()),input.tags().stream().map(LocalMemory::strip).filter(s->!s.isEmpty()).toList(),clock.instant().getEpochSecond()));}
        if(documents.size()+added.size()>MAX_DOCUMENTS)throw new IllegalArgumentException("memory exceeds256documents");var next=new ArrayList<>(documents);next.addAll(added);documents=List.copyOf(next);return List.copyOf(added);
    }
    public List<Match> search(String query,int limit){
        Objects.requireNonNull(query);if(length(query)>MAX_TEXT||limit<1||limit>20)throw new IllegalArgumentException("search exceeds admitted query/limit bounds");var left=counts(tokenize(query));if(left.isEmpty())return List.of();var matches=new ArrayList<Match>();
        for(Document doc:documents()){var right=counts(tokenize(doc.title()+" "+doc.text()+" "+String.join(" ",doc.tags())));double score=cosine(left,right);if(score<=0)continue;double rounded=new BigDecimal(score).setScale(4,RoundingMode.HALF_EVEN).doubleValue();matches.add(new Match(doc.id(),doc.title(),rounded,snippet(doc.text(),left.keySet()),doc.tags()));}
        matches.sort(Comparator.comparingDouble(Match::score).reversed());return List.copyOf(matches.subList(0,Math.min(limit,matches.size())));
    }
    public synchronized void save(Path path)throws IOException{
        byte[] bytes=Json.write(new TreeMap<>(Map.of("version",1,"documents",documents.stream().map(Document::fields).toList()))).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_SNAPSHOT)throw new IllegalArgumentException("memory snapshot exceeds16MiB");AtomicFiles.write(path,bytes);
    }
    /** Missing, malformed or empty original memory files recover the original seed bank. */
    public static LocalMemory load(Path path,Clock clock){
        var result=new LocalMemory(clock);if(!Files.exists(path))return result;
        try{byte[] bytes;try(var in=Files.newInputStream(path)){bytes=in.readNBytes(MAX_SNAPSHOT+1);}if(bytes.length>MAX_SNAPSHOT)throw new IllegalArgumentException("memory snapshot exceeds16MiB");String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();Object parsed=Json.parse(text,"local memory");Object rows=parsed instanceof Map<?,?> m?m.get("documents"):parsed;
            if(!(rows instanceof List<?> list)||list.size()>MAX_DOCUMENTS)throw new IllegalArgumentException("invalid memory document list");var docs=new ArrayList<Document>();
            for(Object item:list){if(!(item instanceof Map<?,?> map))continue;String title=map.get("title")instanceof String s?strip(s):"Untitled";if(title.isEmpty())title="Untitled";String body=map.get("text")instanceof String s?strip(s):"";var tags=map.containsKey("tags")?strings(map.get("tags")):List.<String>of();if(length(title)>160||length(body)>MAX_TEXT||tags.size()>16||tags.stream().anyMatch(t->length(t)>160))throw new IllegalArgumentException("loaded document exceeds admitted bounds");String id=map.get("id")instanceof String s&&!s.isEmpty()?s:documentId(title,body);if(id.length()>160)throw new IllegalArgumentException("document identifier exceeds160units");long created=map.get("created")instanceof Number n&&n.longValue()!=0?n.longValue():clock.instant().getEpochSecond();docs.add(new Document(id,title,body,tags,created));}
            if(!docs.isEmpty())result.documents=List.copyOf(docs);
        }catch(IOException|RuntimeException e){return new LocalMemory(clock);}return result;
    }
    public static List<String> tokenize(String text){var result=new ArrayList<String>();var matcher=WORD.matcher(text);while(matcher.find()){String token=matcher.group().toLowerCase(Locale.ROOT);if(token.length()>1&&!STOP.contains(token))result.add(token);}return List.copyOf(result);}
    public static String documentId(String title,String text){try{return "doc_"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest((title+"\n"+text).getBytes(StandardCharsets.UTF_8))).substring(0,16);}catch(NoSuchAlgorithmException e){throw new IllegalStateException("JVM missing SHA1",e);}}
    private static Map<String,Integer> counts(List<String> words){var result=new HashMap<String,Integer>();words.forEach(w->result.merge(w,1,Integer::sum));return result;}
    private static double cosine(Map<String,Integer> a,Map<String,Integer> b){if(a.isEmpty()||b.isEmpty())return 0;long dot=0,aa=0,bb=0;for(var e:a.entrySet()){dot+=(long)e.getValue()*b.getOrDefault(e.getKey(),0);aa+=(long)e.getValue()*e.getValue();}for(int v:b.values())bb+=(long)v*v;return dot/(Math.sqrt(aa)*Math.sqrt(bb));}
    private static String snippet(String text,Set<String> query){String best=null;int bestScore=-1;for(String raw:SENTENCES.split(text)){String sentence=strip(raw);if(sentence.isEmpty())continue;var terms=counts(tokenize(sentence));int score=0;for(String term:query)score+=terms.getOrDefault(term,0);if(score>bestScore){best=sentence;bestScore=score;}}return prefix(best==null?text:best,best==null?280:360);}
    static int length(String s){return s.codePointCount(0,s.length());}
    static String prefix(String s,int count){return s.substring(0,s.offsetByCodePoints(0,Math.min(count,length(s))));}
    static String strip(String s){int a=0,b=s.length();while(a<b&&space(s.codePointAt(a)))a+=Character.charCount(s.codePointAt(a));while(b>a&&space(s.codePointBefore(b)))b-=Character.charCount(s.codePointBefore(b));return s.substring(a,b);}
    private static boolean space(int cp){return Character.isWhitespace(cp)||Character.isSpaceChar(cp)||cp==0x85;}
    private static List<String> strings(Object v){return ((List<?>)v).stream().map(String.class::cast).toList();}
    private static Map<?,?> literals(){try(var in=LocalMemory.class.getResourceAsStream("memory-literals.json")){if(in==null)throw new IllegalStateException("missing original memory literals");byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("memory literals exceed64KiB");return (Map<?,?>)Json.parse(new String(bytes,StandardCharsets.UTF_8),"original memory literals");}catch(IOException e){throw new IllegalStateException("memory literals unreadable",e);}}
}
