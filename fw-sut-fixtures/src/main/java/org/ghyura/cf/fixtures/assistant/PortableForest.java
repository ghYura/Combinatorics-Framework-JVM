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
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Json;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** Immutable neutral numeric forest inference; no serialized executable objects or original runtime. */
public final class PortableForest implements LocalAssistant.Predictor {
 public static final int MAX_BYTES=2*1024*1024;
 public static final String BUNDLED_SHA256="66b1156aaf3de578a97c64123318250d9fcd7a37070a89dea45b598e23e087a0";
 private final double[] mean,scale;private final List<Integer> classes;private final List<List<Node>> trees;private final byte[] bytes;private final String digest,sourceDigest;
 private record Node(int left,int right,int feature,double threshold,double[] weights){ }
 public record Inference(int prediction,double confidence,List<Integer> classes,List<Double> probabilities){public Inference{classes=List.copyOf(classes);probabilities=List.copyOf(probabilities);}public Map<String,Object> fields(){return Collections.unmodifiableMap(new TreeMap<>(Map.of("prediction",prediction,"confidence",confidence,"classes",classes,"probabilities",probabilities)));}}
 private PortableForest(byte[] input)throws IOException{
  if(input.length==0||input.length>MAX_BYTES)throw new IllegalArgumentException("neutral forest exceeds2MiB");bytes=input.clone();String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();digest=Canonical.sha256Hex(text);var root=map(Json.parse(text,"neutral forest"));if(!"cf.local-forest/v1".equals(root.get("schema")))throw new IllegalArgumentException("unsupported neutral forest schema");int features=integer(root.get("features"));if(features<1||features>256)throw new IllegalArgumentException("forest needs1..256features");mean=vector(root.get("mean"),features);scale=vector(root.get("scale"),features);for(double v:scale)if(v<=0)throw new IllegalArgumentException("positive finite scale required");
  var rawClasses=list(root.get("classes"));if(rawClasses.isEmpty()||rawClasses.size()>16)throw new IllegalArgumentException("forest needs1..16classes");var labels=new ArrayList<Integer>();for(Object raw:rawClasses){int value=integer(raw);if(!labels.isEmpty()&&value<=labels.getLast())throw new IllegalArgumentException("ascending unique class labels required");labels.add(value);}classes=List.copyOf(labels);sourceDigest=(String)map(root.get("source")).get("sha256");if(sourceDigest==null||!sourceDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("source asset digest required");
  var rawTrees=list(root.get("trees"));if(rawTrees.isEmpty()||rawTrees.size()>128)throw new IllegalArgumentException("forest needs1..128trees");var parsed=new ArrayList<List<Node>>();for(Object raw:rawTrees){var rawNodes=list(raw);if(rawNodes.isEmpty()||rawNodes.size()>2047)throw new IllegalArgumentException("tree needs1..2047nodes");var nodes=new ArrayList<Node>();for(Object item:rawNodes){var n=map(item);int left=integer(n.get("left")),right=integer(n.get("right")),feature=integer(n.get("feature"));double threshold=finite(n.get("threshold"));double[] weights=vector(n.get("weights"),classes.size());double total=0;for(double v:weights){if(v<0)throw new IllegalArgumentException("negative class weight");total+=v;}if(!Double.isFinite(total)||total<=0)throw new IllegalArgumentException("positive finite weight sum required");if(!((left==-1&&right==-1)||(left>=0&&right>=0&&left<rawNodes.size()&&right<rawNodes.size()&&left!=right&&feature>=0&&feature<features)))throw new IllegalArgumentException("invalid tree child/feature");nodes.add(new Node(left,right,feature,threshold,weights));}validate(nodes);parsed.add(List.copyOf(nodes));}trees=List.copyOf(parsed);
 }
 private static void validate(List<Node> nodes){var pending=new ArrayDeque<int[]>();pending.push(new int[]{0,0});var visited=new BitSet();while(!pending.isEmpty()){var step=pending.pop();if(step[1]>16||visited.get(step[0]))throw new IllegalArgumentException("cyclic,shared or excessive-depth tree");visited.set(step[0]);var n=nodes.get(step[0]);if(n.left()!=-1){pending.push(new int[]{n.right(),step[1]+1});pending.push(new int[]{n.left(),step[1]+1});}}if(visited.cardinality()!=nodes.size())throw new IllegalArgumentException("unreachable tree nodes");}
 public static PortableForest load(Path path)throws IOException{try(var in=Files.newInputStream(path)){return read(in);}}
 private static PortableForest read(InputStream in)throws IOException{return new PortableForest(in.readNBytes(MAX_BYTES+1));}
 public static PortableForest bundled(){try(var in=PortableForest.class.getResourceAsStream("source-forest.json")){if(in==null)throw new IllegalStateException("missing bundled neutral source forest");var model=read(in);if(!model.digest().equals(BUNDLED_SHA256))throw new IllegalArgumentException("bundled forest digest mismatch");return model;}catch(IOException e){throw new IllegalStateException("bundled neutral forest invalid",e);}}
 public void save(Path path)throws IOException{AtomicFiles.write(path,bytes);}
 public int featureCount(){return mean.length;}public int treeCount(){return trees.size();}public int nodeCount(){return trees.stream().mapToInt(List::size).sum();}public List<Integer> classes(){return classes;}public String digest(){return digest;}public String sourceDigest(){return sourceDigest;}
 public Inference infer(List<Double> features){
  features=List.copyOf(features);if(features.size()!=mean.length)throw new IllegalArgumentException("fitted forest requires"+mean.length+"features");float[] vector=new float[mean.length];for(int i=0;i<vector.length;i++){double value=features.get(i);if(!Double.isFinite(value))throw new IllegalArgumentException("finite features required");vector[i]=(float)((value-mean[i])/scale[i]);if(!Float.isFinite(vector[i]))throw new IllegalArgumentException("scaled feature outside finitefloat32 admission");}
  double[] probabilities=new double[classes.size()];for(var tree:trees){var node=tree.getFirst();while(node.left()!=-1)node=tree.get(vector[node.feature()]<=node.threshold()?node.left():node.right());double total=0;for(double weight:node.weights())total+=weight;for(int c=0;c<probabilities.length;c++)probabilities[c]+=node.weights()[c]/total;}
  int best=0;var values=new ArrayList<Double>();for(int c=0;c<probabilities.length;c++){probabilities[c]/=trees.size();values.add(probabilities[c]);if(probabilities[c]>probabilities[best])best=c;}return new Inference(classes.get(best),probabilities[best],classes,values);
 }
 @Override public LocalAssistant.Prediction predict(List<Double> features){var result=infer(features);return new LocalAssistant.Prediction(result.prediction(),result.confidence());}
 private static Map<?,?> map(Object value){if(!(value instanceof Map<?,?> m))throw new IllegalArgumentException("forest object required");return m;}
 private static List<?> list(Object value){if(!(value instanceof List<?> l))throw new IllegalArgumentException("forest array required");return l;}
 private static int integer(Object value){if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue())throw new IllegalArgumentException("bounded integer required");return n.intValue();}
 private static double finite(Object value){if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("finite scalar required");return n.doubleValue();}
 private static double[] vector(Object value,int size){var values=list(value);if(values.size()!=size)throw new IllegalArgumentException("forest dimension mismatch");double[] result=new double[size];for(int i=0;i<size;i++)result[i]=finite(values.get(i));return result;}
}
