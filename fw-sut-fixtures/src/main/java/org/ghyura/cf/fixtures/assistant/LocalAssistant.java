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

import org.ghyura.cf.model.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.math.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Original bounded text-message retrieval/rules core; a trained predictor is a typed local collaborator. */
public final class LocalAssistant {
    private static final Map<?,?> LITERALS=literals();
    private static final Pattern WORD=Pattern.compile("[A-Za-zА-Яа-яЁё0-9_']+"),NUMBER=Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)",Pattern.UNICODE_CHARACTER_CLASS),MATH=Pattern.compile("\\d+\\s*[+\\-*/%]\\s*\\d+",Pattern.UNICODE_CHARACTER_CLASS),PIECES=Pattern.compile("[^\\s\\x1c-\\x1f]+[\\s\\x1c-\\x1f]*",Pattern.UNICODE_CHARACTER_CLASS);
    private final LocalMemory memory;private final Predictor predictor;private final Clock clock;private final Supplier<String> identifiers;
    public record Message(String role,String content){public Message{Objects.requireNonNull(role);Objects.requireNonNull(content);if(role.length()>160||LocalMemory.length(content)>48000)throw new IllegalArgumentException("message exceeds admitted input bounds");}}
    public record Request(List<Message> messages,String model,Integer maxTokens){public Request{messages=List.copyOf(messages);if(messages.isEmpty()||messages.size()>256||model!=null&&model.length()>160)throw new IllegalArgumentException("chat request exceeds admitted bounds");}}
    public record Prediction(int prediction,double confidence){ }
    @FunctionalInterface public interface Predictor{Prediction predict(List<Double> features);}
    public record Usage(int promptTokens,int completionTokens,int totalTokens){public Map<String,Object> fields(){return new TreeMap<>(Map.of("prompt_tokens",promptTokens,"completion_tokens",completionTokens,"total_tokens",totalTokens));}}
    public record Completion(String id,long created,String model,String text,Usage usage,List<LocalMemory.Match> sources,LocalArithmetic.Disposition arithmeticDisposition){
        public Completion{sources=List.copyOf(sources);}
        public Map<String,Object> fields(){return new TreeMap<>(Map.of("id",id,"object","chat.completion","created",created,"model",model,"choices",List.of(new TreeMap<>(Map.of("index",0,"message",new TreeMap<>(Map.of("role","assistant","content",text)),"finish_reason","stop"))),"usage",usage.fields(),"local_meta",new TreeMap<>(Map.of("engine","retrieval-rules-surrogate","sources",sources.stream().map(LocalMemory.Match::fields).toList(),"cpu_profile","fx-8320-class"))));}
    }
    /** A newly created original pipeline is untrained. This constructor preserves that state. */
    public LocalAssistant(LocalMemory memory,Clock clock){this(memory,v->{if(v.size()>256)throw new IllegalArgumentException("too many features: max is 256");return new Prediction(-1,0);},clock,()->"chatcmpl-"+UUID.randomUUID().toString().replace("-","").substring(0,12));}
    public LocalAssistant(LocalMemory memory,Predictor predictor,Clock clock,Supplier<String> identifiers){this.memory=Objects.requireNonNull(memory);this.predictor=Objects.requireNonNull(predictor);this.clock=Objects.requireNonNull(clock);this.identifiers=Objects.requireNonNull(identifiers);}
    public Completion complete(Request request){
        var messages=normalize(request.messages());int limit=request.maxTokens()==null?180:Math.max(1,Math.min(320,request.maxTokens())),prompt=messages.stream().mapToInt(m->tokens(m.content())).sum();String user=messages.isEmpty()?"":messages.getLast().content();for(int i=messages.size()-1;i>=0;i--)if(messages.get(i).role().equals("user")){user=messages.get(i).content();break;}
        String system=String.join("\n",messages.stream().filter(m->m.role().equals("system")||m.role().equals("developer")).map(Message::content).toList()),lower=user.toLowerCase(Locale.ROOT),text;List<LocalMemory.Match> sources=List.of();var disposition=LocalArithmetic.Disposition.NO_RESULT;
        if(user.isEmpty())text=literal("empty");else{
            var arithmetic=LocalArithmetic.evaluate(user);disposition=arithmetic.disposition();
            if(arithmetic.value().isPresent()&&(contains(lower,"calculate","calc","math","solve","сколько","посчитай")||MATH.matcher(lower).find()))text="The arithmetic result is "+general(arithmetic.value().orElseThrow())+".";
            else if(NUMBER.matcher(user).find()&&contains(lower,"predict","classify","classification","feature","features","surrogate","класс","предскажи","признак")){
                var values=new ArrayList<Double>();var matcher=NUMBER.matcher(user);while(matcher.find())values.add(Double.parseDouble(asciiDigits(matcher.group())));
                try{var result=predictor.predict(List.copyOf(values));text=result.prediction()==-1?literal("untrained"):result.confidence()>=.75?String.format(Locale.ROOT,"Surrogate prediction: class %d with confidence %.3f.",result.prediction(),result.confidence()):String.format(Locale.ROOT,"Surrogate prediction: class %d with low confidence %.3f. For production use, ask the oracle or add this case to the retraining buffer.",result.prediction(),result.confidence());}
                catch(IllegalArgumentException e){text="I could not run the surrogate classifier for those numbers: "+e.getMessage();}
            }else if(contains(lower,"who are you","what are you","какая ты модель","кто ты","что ты умеешь","are you an llm"))text=literal("identity");
            else if(List.of("help","/help","помощь").contains(LocalMemory.strip(lower))||contains(lower,"how to use","endpoints"))text=literal("help");
            else{var matches=memory.search(user,3);
                if(contains(lower,"fx-8320","cpu","gpu","ram","videocard","hardware","желез")){text=literal("hardware");sources=matches;}
                else if(!matches.isEmpty()&&matches.getFirst().score()>=.08){sources=matches;var b=new StringBuilder("From local memory, the closest useful context is:");int n=0;for(var m:matches)b.append('\n').append(++n).append(". ").append(m.title()).append(": ").append(m.snippet());if(!system.isEmpty())b.append("\n\nI also applied the active system/developer instructions from this chat.");if(LocalMemory.strip(user).endsWith("?"))b.append("\n\nShort answer: use the retrieved context as the local source of truth and keep the CPU-only limits enabled.");text=b.toString();}
                else{var words=LocalMemory.tokenize(user);String focus=words.isEmpty()?"your prompt":String.join(", ",words.subList(0,Math.min(6,words.size())));text="I do not have a neural model behind this endpoint, so I will answer conservatively from local rules. The main topic looks like: "+focus+". For better answers, add project notes through /memory/ingest or ask for a numeric surrogate prediction. The service will keep using CPU-friendly retrieval and deterministic generation.";if(!system.isEmpty())text+=" I considered the system/developer message in the chat context.";}
            }text=trim(text,limit);
        }
        int completion=tokens(text);String id=Objects.requireNonNull(identifiers.get());if(id.length()>160)throw new IllegalArgumentException("completion identifier exceeds160units");return new Completion(id,clock.instant().getEpochSecond(),request.model()==null||request.model().isEmpty()?"local-cpu-surrogate-llm":request.model(),text,new Usage(prompt,completion,prompt+completion),sources,disposition);
    }
    /** Emit the already computed completion; no repeated inference or timestamp/identity regeneration. */
    public void stream(Completion c,Consumer<String> consumer){Objects.requireNonNull(consumer);emit(c,Map.of("role","assistant"),null,consumer);var matcher=PIECES.matcher(c.text());while(matcher.find())emit(c,Map.of("content",matcher.group()),null,consumer);emit(c,Map.of(),"stop",consumer);consumer.accept("data: [DONE]\n\n");}
    private static void emit(Completion c,Map<String,Object> delta,String reason,Consumer<String> consumer){var choice=new TreeMap<String,Object>();choice.put("index",0);choice.put("delta",delta);choice.put("finish_reason",reason);consumer.accept("data: "+compact(Json.write(new TreeMap<>(Map.of("id",c.id(),"object","chat.completion.chunk","created",c.created(),"model",c.model(),"choices",List.of(choice)))))+"\n\n");}
    private static String compact(String json){var b=new StringBuilder();boolean quoted=false,escaped=false;for(int i=0;i<json.length();i++){char c=json.charAt(i);if(quoted){b.append(c);if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}else if(c=='"'){quoted=true;b.append(c);}else if(!Character.isWhitespace(c))b.append(c);}return b.toString();}
    private static String asciiDigits(String s){var b=new StringBuilder();s.codePoints().forEach(cp->{int digit=Character.digit(cp,10);if(digit>=0)b.append((char)('0'+digit));else b.appendCodePoint(cp);});return b.toString();}
    private static List<Message> normalize(List<Message> input){var out=new ArrayList<Message>();for(var m:input.subList(Math.max(0,input.size()-24),input.size())){String role=LocalMemory.strip(m.role().toLowerCase(Locale.ROOT)),content=LocalMemory.strip(m.content());if(content.isEmpty())continue;int n=LocalMemory.length(content);if(n>12000)content=content.substring(content.offsetByCodePoints(0,n-12000));out.add(new Message(role.isEmpty()?"user":role,content));}return List.copyOf(out);}
    private static int tokens(String s){int n=0;var m=WORD.matcher(s);while(m.find())n++;return Math.max(1,n);}
    private static boolean contains(String s,String... alternatives){for(String a:alternatives)if(s.contains(a))return true;return false;}
    private static String trim(String s,int limit){var m=PIECES.matcher(s);int n=0,end=0;while(m.find()&&n++<limit)end=m.end();return LocalMemory.strip(s.substring(0,end));}
    private static String general(double v){if(v==0)return Double.doubleToRawLongBits(v)<0?"-0":"0";var d=new BigDecimal(v).round(new MathContext(6,RoundingMode.HALF_EVEN)).stripTrailingZeros();int exponent=d.precision()-d.scale()-1;return exponent>=-4&&exponent<6?d.toPlainString():d.movePointLeft(exponent).stripTrailingZeros().toPlainString()+String.format(Locale.ROOT,"e%+03d",exponent);}
    private static String literal(String key){return (String)LITERALS.get(key);}
    private static Map<?,?> literals(){try(var in=LocalAssistant.class.getResourceAsStream("chat-literals.json")){if(in==null)throw new IllegalStateException("missing original chat literals");byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("chat literals exceed64KiB");return (Map<?,?>)Json.parse(new String(bytes,StandardCharsets.UTF_8),"original chat literals");}catch(IOException e){throw new IllegalStateException("chat literals unreadable",e);}}
}
