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

package org.ghyura.cf.desktop;

import org.ghyura.cf.desktop.session.*;
import org.ghyura.cf.config.ConfigResolver;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.jar.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.JTextComponent;

/** Test-only package peer, loaded on the application class path. Never creates a window. */
public final class GuifProbe {
    record ObjectDef(String id,int tab,String kind,String name,int occurrence) {}
    record Snapshot(String event,String value,boolean enabled,boolean visible,int rows,long nanos,boolean ok,String reason) {}
    static final Snapshot POISON=new Snapshot("STOP","",false,false,0,0,true,"");
    static final Map<String,ObjectDef> DEFINITIONS=new LinkedHashMap<>();
    static final Map<String,List<String>> GROUPS=new LinkedHashMap<>();
    static {
        try {
            for(String line:resource("/objects.tsv").split("\n")) {
                String[] a=line.split("\t",-1);
                DEFINITIONS.put(a[0],new ObjectDef(a[0],Integer.parseInt(a[1]),a[2],a[3],Integer.parseInt(a[4])));
            }
            for(String line:resource("/groups.tsv").split("\n")) {
                String[] a=line.split("\t",-1); GROUPS.put(a[0],List.of(a[1].split(",")));
            }
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static String resource(String name)throws Exception {try(var in=GuifProbe.class.getResourceAsStream(name)){return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}}

    final class Watch {
        final ObjectDef def; final BlockingQueue<Snapshot> queue=new LinkedBlockingQueue<>();
        final List<Runnable> detach=new ArrayList<>(); final List<String> reasons=new ArrayList<>();
        final Set<String> modes=new LinkedHashSet<>(); Component component;
        long events,violations,exercised; final Thread thread;
        Watch(ObjectDef def) {
            this.def=def;
            thread=Thread.ofVirtual().name("guif-watch-"+def.id()).start(()->{
                try {for(;;) {Snapshot s=queue.take(); if(s==POISON)break; events++;
                    if(!s.ok()){violations++; if(reasons.size()<12&&!reasons.contains(s.reason()))reasons.add(s.reason());}
                    if(s.rows() < -1){violations++;reasons.add("negative row count");}
                }}catch(InterruptedException e){Thread.currentThread().interrupt();}
            });
        }
        void assertion(boolean ok,String reason){queue.add(new Snapshot("assert","",true,true,-1,System.nanoTime(),ok,reason));}
        void exercise(String mode){exercised++;modes.add(mode);}
        void snapshot(String event){
            if(!SwingUtilities.isEventDispatchThread()){assertion(false,"Swing event delivered off EDT: "+event);return;}
            Component c=component; String value=""; int rows=-1;
            if(c instanceof JTextComponent t)value=t.getText();
            else if(c instanceof AbstractButton b)value=Objects.toString(b.getText(),"");
            else if(c instanceof JComboBox<?> b)value=Objects.toString(b.getSelectedItem(),"");
            else if(c instanceof JSpinner s)value=Objects.toString(s.getValue(),"");
            else if(c instanceof JTable t)rows=t.getRowCount();
            else if(c instanceof JList<?> l)rows=l.getModel().getSize();
            else if(c instanceof JTree t)rows=t.getRowCount();
            queue.add(new Snapshot(event,value.length()>120?value.substring(0,120):value,c!=null&&c.isEnabled(),c!=null&&c.isVisible(),rows,System.nanoTime(),true,""));
        }
        void bind(Component c) {
            component=c;
            if(c==null){assertion(false,"Appendix object not found by structural selector");return;}
            PropertyChangeListener property=e->snapshot("property:"+e.getPropertyName()); c.addPropertyChangeListener(property);detach.add(()->c.removePropertyChangeListener(property));
            HierarchyListener hierarchy=e->snapshot("hierarchy");c.addHierarchyListener(hierarchy);detach.add(()->c.removeHierarchyListener(hierarchy));
            if(c instanceof JTextComponent t){
                DocumentListener l=new DocumentListener(){public void insertUpdate(DocumentEvent e){snapshot("insert");}public void removeUpdate(DocumentEvent e){snapshot("remove");}public void changedUpdate(DocumentEvent e){snapshot("document");}};
                t.getDocument().addDocumentListener(l);detach.add(()->t.getDocument().removeDocumentListener(l));
                CaretListener caret=e->snapshot("caret");t.addCaretListener(caret);detach.add(()->t.removeCaretListener(caret));
            }
            if(c instanceof AbstractButton b){ActionListener l=e->{snapshot("action");exercise("button");};b.addActionListener(l);detach.add(()->b.removeActionListener(l));
                ItemListener item=e->snapshot("item");b.addItemListener(item);detach.add(()->b.removeItemListener(item));}
            if(c instanceof JComboBox<?> b){ItemListener l=e->snapshot("item");b.addItemListener(l);detach.add(()->b.removeItemListener(l));}
            if(c instanceof JSpinner s){ChangeListener l=e->snapshot("change");s.addChangeListener(l);detach.add(()->s.removeChangeListener(l));}
            if(c instanceof JTabbedPane t){ChangeListener l=e->snapshot("tab");t.addChangeListener(l);detach.add(()->t.removeChangeListener(l));}
            if(c instanceof JTable t){TableModelListener l=e->snapshot("table");t.getModel().addTableModelListener(l);detach.add(()->t.getModel().removeTableModelListener(l));
                ListSelectionListener ls=e->snapshot("selection");t.getSelectionModel().addListSelectionListener(ls);detach.add(()->t.getSelectionModel().removeListSelectionListener(ls));}
            if(c instanceof JList<?> l){ListSelectionListener ls=e->snapshot("selection");l.addListSelectionListener(ls);detach.add(()->l.removeListSelectionListener(ls));}
            if(c instanceof JTree t){TreeSelectionListener l=e->snapshot("tree");t.addTreeSelectionListener(l);detach.add(()->t.removeTreeSelectionListener(l));}
            snapshot("attached");
        }
    }

    final String family; final Map<String,String> options; final Map<String,Watch> watches=new LinkedHashMap<>();
    final List<String> trace=Collections.synchronizedList(new ArrayList<>()),notes=new ArrayList<>(); final long started=System.nanoTime();
    final Set<Long> beforeThreads=new HashSet<>(); final Map<String,Object> details=new LinkedHashMap<>();
    Path dir,scenario; Desktop desktop; Session session; int infrastructure;
    public static GuifProbe execute(String family,Map<String,String> options) {
        GuifProbe p=new GuifProbe(family,options);
        try {p.prepare();p.drive();if(!options.containsKey("repro")&&!options.containsKey("extension")){p.exerciseNonButtons();p.dialogProxies();}}
        catch(Throwable e){p.error(e);}
        finally {p.finish();}
        return p;
    }
    GuifProbe(String family,Map<String,String> options){this.family=family;this.options=new LinkedHashMap<>(options);
        for(String id:GROUPS.get(family))watches.put(id,new Watch(DEFINITIONS.get(id)));
        for(Thread t:Thread.getAllStackTraces().keySet())beforeThreads.add(t.threadId());}
    String option(String k,String fallback){return options.getOrDefault(k,fallback);}
    boolean is(String k,String v){return v.equals(options.get(k));}
    static <T>T edt(Callable<T> work)throws Exception {
        if(SwingUtilities.isEventDispatchThread())return work.call();
        FutureTask<T> task=new FutureTask<>(work);SwingUtilities.invokeLater(task);return task.get(15,TimeUnit.SECONDS);
    }
    static void waitFuture(CompletableFuture<?> future)throws Exception {if(future!=null)future.get(30,TimeUnit.SECONDS);}
    static void waitFor(Callable<Boolean> predicate)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(!predicate.call()){if(System.nanoTime()>end)throw new TimeoutException("state predicate did not settle");Thread.sleep(10);}}
    static List<Component> all(Component c){List<Component> list=new ArrayList<>();list.add(c);if(c instanceof Container k)for(Component x:k.getComponents())list.addAll(all(x));return list;}
    static String accessible(Component c){return c.getAccessibleContext()==null?"":Objects.toString(c.getAccessibleContext().getAccessibleName(),"");}
    static String kind(Component c){
        if(c instanceof JSpinner)return "spinner";if(c instanceof JComboBox)return "choice";
        if(c instanceof JTable)return "table";if(c instanceof JTree)return "tree";if(c instanceof JList)return "list";
        if(c instanceof JTextArea)return "text area";
        if(c instanceof JTextField){for(Container p=c.getParent();p!=null;p=p.getParent())if(p instanceof JSpinner||p instanceof JComboBox)return "";return "text field";}
        if(c instanceof JCheckBox)return "check box";if(c instanceof JToggleButton)return "toggle";
        if(c instanceof JButton b&&b.getText()!=null&&!b.getText().isBlank())return "button";return "";
    }
    Component scope(int tab){return tab==0?desktop.component():desktop.tabs().getComponentAt(tab-1);}
    Component locate(ObjectDef d){
        if(d.tab()<0)return null;
        if(d.kind().equals("sub-tab"))return all(scope(d.tab())).stream().filter(c->c instanceof JTabbedPane t&&t.indexOfTab(d.name())>=0).findFirst().orElse(null);
        List<Component> matches=new ArrayList<>();
        for(Component c:all(scope(d.tab()))){
            if(!kind(c).equals(d.kind()))continue;
            String label=c instanceof AbstractButton b?Objects.toString(b.getText(),""):accessible(c);
            boolean match=d.name().equals("(unnamed)")?label.isBlank():d.name().startsWith("#")?d.name().substring(1).equals(c.getName()):label.equals(d.name());
            if(d.name().startsWith("Scenarios of the selected suite"))match=label.startsWith("Scenarios of ");
            if(d.name().startsWith("Cells of sheet"))match=label.startsWith("Cells of sheet");
            if(d.tab()==0&&d.kind().equals("toggle"))match=label.startsWith("Hints on");
            if(match)matches.add(c);
        }
        return matches.size()>d.occurrence()?matches.get(d.occurrence()):null;
    }
    <T extends Component>T find(int tab,Class<T> cls,String name){for(Component c:all(scope(tab)))if(cls.isInstance(c)&&(name.equals(accessible(c))||name.equals(c.getName())||c instanceof AbstractButton b&&name.equals(b.getText())))return cls.cast(c);throw new IllegalArgumentException("missing "+tab+" "+name);}
    void check(String id,boolean ok,String reason){Watch w=watches.get(id);if(w!=null)w.assertion(ok,reason);else if(!ok)throw new IllegalArgumentException("unowned assertion: "+id+" "+reason);}
    String id(int tab,String name){return watches.values().stream().map(w->w.def).filter(d->d.tab()==tab&&d.name().equals(name)).map(ObjectDef::id).findFirst().orElseThrow(()->new IllegalArgumentException("no watcher "+tab+" "+name));}
    void check(int tab,String name,boolean ok,String why){check(id(tab,name),ok,why);}
    void exercised(int tab,String name,String mode){for(Watch w:watches.values())if(w.def.tab()==tab&&w.def.name().equals(name)){w.exercise(mode);w.assertion(true,"API completion");}}
    void api(int tab,String... names){for(String name:names)exercised(tab,name,"backing-api");}
    void logical(String id){Watch w=watches.get(id);if(w!=null){w.exercise("logic");w.assertion(true,"exercised");}}
    void click(int tab,String text)throws Exception {edt(()->{JButton b=find(tab,JButton.class,text);if(b.isEnabled())b.doClick(0);return null;});trace.add("click:"+text.replace(' ','_'));if(tab==4)settleSieve();}
    void settleSieve()throws Exception {
        List<CompletableFuture<?>> tasks=edt(()->{List<CompletableFuture<?>> fs=new ArrayList<>();for(var field:desktop.sieve().getClass().getDeclaredFields())if(CompletableFuture.class.isAssignableFrom(field.getType())){field.setAccessible(true);Object f=field.get(desktop.sieve());if(f instanceof CompletableFuture<?> cf&&!cf.isDone())fs.add(cf);}return fs;});
        for(var future:tasks)try{waitFuture(future);}catch(ExecutionException|CancellationException expected){trace.add("sieve_action_refused");}
    }
    Path write(String name,String text)throws Exception {Path p=dir.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);return p;}
    Session makeSession(String... pairs){List<ConfigResolver.Override> os=new ArrayList<>();for(String pair:pairs){int i=pair.indexOf('=');os.add(new ConfigResolver.Override(pair.substring(0,i),pair.substring(i+1),"guif-dogfood"));}return Session.resolve(null,Map.of(),os);}
    void prepare()throws Exception {
        if(!GraphicsEnvironment.isHeadless())throw new IllegalStateException("headless required");
        Path fixtures=Path.of("target/guif-dogfood/fixtures").toAbsolutePath();Files.createDirectories(fixtures);dir=Files.createTempDirectory(fixtures,family+"-");
        scenario=write("small.json","{\"sheets\":{\"FW_Seq\":[[\"A\",\"FW_Combi(1)\"],[\"B\",\"FW_Combi(1)\"]],\"A\":[[\"latency=1;\"],[\"latency=2;\"]],\"B\":[[\"mode=a;\"],[\"mode=b;\"]]}}");
        session=makeSession("cf.execution.sut=inline-metrics","cf.execution.workers=1","cf.keys.encoding=SIGNED_BYTE","cf.plan.autoPromoteSingleVerb=false","cf.pipeline.ordinal.window=1","cf.analysis.goals=latency:min","cf.analysis.discover=false","cf.campaign.iterations="+option("iterations","2"),"cf.campaign.strategy="+option("strategy","bias"),"cf.campaign.stopWhenStable=99","cf.run.id=nested");
        edt(()->{FlatDesktopLauncher.installLookAndFeel();desktop=new Desktop(session,family.equals("x_shell")?null:scenario,FileTrail.inMemory(),true);for(Watch w:watches.values())if(w.def.tab()>=0)w.bind(locate(w.def));return null;});
    }
    void drive()throws Exception {trace.add("begin");if(options.containsKey("repro")){minimal();return;}if(options.containsKey("extension")){sieveControls();return;}switch(family){
        case "t1_library"->library();case "t2_authoring"->authoring();case "t3_runner"->runner();
        case "t4_graph"->sieveGraph();case "t4_data"->sieveData();case "t4_rules"->sieveRules();
        case "t5_candidates"->candidates();case "t6_run","t6_sequences"->run();case "t7_campaign"->campaign();
        case "t8_analysis"->analysis();case "t9_scorer"->scorer();case "x_dialogs"->dialogs();
        case "x_search_data","x_search_tools"->search();case "x_shell"->shell();default->throw new IllegalArgumentException(family);
    }trace.add("end");}

    void error(Throwable error){Throwable e=error;while(e instanceof ExecutionException||e instanceof java.lang.reflect.InvocationTargetException){if(e.getCause()==null)break;e=e.getCause();}
        infrastructure=e instanceof TimeoutException?-2:-1;StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));notes.add(sw.toString());}
    void finish(){
        try {if(desktop!=null)edt(()->{for(Watch w:watches.values())for(Runnable r:w.detach)r.run();return null;});}catch(Exception e){error(e);}
        try {if(desktop!=null)waitFuture(edt(desktop::close));}catch(Exception e){infrastructure=-3;notes.add("close: "+e);}
        for(Watch w:watches.values()){w.queue.add(POISON);try{w.thread.join(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}if(w.thread.isAlive())infrastructure=-4;}
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);List<String> leaks;
        do {leaks=Thread.getAllStackTraces().keySet().stream().filter(t->!beforeThreads.contains(t.threadId())&&t.isAlive()&&(t.getName().startsWith("cf-desktop-")||t.getName().startsWith("guif-watch-"))).map(Thread::getName).toList();if(leaks.isEmpty())break;try{Thread.sleep(20);}catch(InterruptedException e){break;}}while(System.nanoTime()<end);
        if(!leaks.isEmpty()){infrastructure=-4;notes.add("new named live threads: "+leaks);}
    }
    public int infrastructureCode(){return infrastructure;}
    public boolean violated(String id){return watches.get(id).violations>0;}
    public void emit(int verdict)throws Exception {
        Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("family",family);evidence.put("phase",System.getProperty("guif.phase","initial"));evidence.put("options",options);evidence.put("verdict",verdict);evidence.put("elapsed_ms",(System.nanoTime()-started)/1_000_000);evidence.put("trace",trace);evidence.put("notes",notes);evidence.put("details",details);
        Map<String,Object> objects=new LinkedHashMap<>();long violations=0;StringBuilder metrics=new StringBuilder("app=guif family="+family);
        for(var e:options.entrySet())metrics.append(" factor_").append(e.getKey()).append('=').append(e.getValue());
        for(Watch w:watches.values()){violations+=w.violations;objects.put(w.def.id(),Map.of("events",w.events,"violations",w.violations,"exercised",w.exercised,"modes",w.modes,"reasons",w.reasons,"bound",w.component!=null||w.def.tab()<0));metrics.append(" w_").append(w.def.id()).append("_viol=").append(w.violations).append(" w_").append(w.def.id()).append("_events=").append(w.events).append(" w_").append(w.def.id()).append("_exercised=").append(w.exercised);}
        evidence.put("objects",objects);if(dir!=null){Files.writeString(dir.resolve("evidence.json"),json(evidence));metrics.append(" evidence_id=").append(dir.getFileName());}
        metrics.append(" elapsed_ms=").append((System.nanoTime()-started)/1_000_000).append(" violations=").append(violations).append(" FW_VAR=").append(verdict);System.out.println(metrics);
    }
    static String json(Object o){if(o==null)return "null";if(o instanceof Number||o instanceof Boolean)return o.toString();if(o instanceof Map<?,?> m)return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).toList())+"}";if(o instanceof Collection<?> c)return "["+String.join(",",c.stream().map(GuifProbe::json).toList())+"]";String s=o.toString();return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}

    void library()throws Exception {
        LibraryView v=desktop.library();
        check(1,"About the selected example",edt(v::details).contains("How to start"),"initial details lack How to start");
        Path root=Files.createDirectories(dir.resolve("library/intake")).getParent();
        if(is("source","valid")){
            Files.copy(scenario,root.resolve("small.json"));
            Files.writeString(root.resolve("intake/verified_examples.json"),"[{\"id\":\"small\",\"title\":\"Small\",\"spec\":\"small.json\",\"candidates\":\"4\",\"pass\":\"4\"}]");
        }else if(is("source","malformed"))Files.writeString(root.resolve("intake/verified_examples.json"),"{ unfinished");
        CompletableFuture<?> opening=edt(()->v.open(root));try{waitFuture(opening);}catch(ExecutionException e){if(!is("source","malformed"))throw e;}
        api(1,"Open a library...","Auto-search…");
        if(is("source","valid")){
            check(1,"Groups of examples",edt(v::suiteNames).size()==1,"one fixture suite expected");
            check(1,"Scenarios of the selected suite: documented counts and sources",edt(()->v.scenarios().getRowCount())==1,"one fixture scenario expected");
            if(is("selection","first"))edt(()->{v.selectSuite(0);v.selectScenario(0);return null;});
            else edt(()->{find(1,JTable.class,"Scenarios of suite Verified examples: documented counts and sources").clearSelection();return null;});
        }
        if(is("action","check")){try{waitFuture(edt(v::checkSelected));}catch(ExecutionException e){if(is("source","valid"))throw e;}api(1,"Check the candidate count");}
        else {edt(()->{v.openSelected();return null;});api(1,"Open in the Scenario tab");}
        if(is("source","valid")&&is("selection","first")){
            if(is("action","check"))check(1,"Check the candidate count",edt(v::details).contains("equal"),"documented count not confirmed");
            else check(1,"Open in the Scenario tab",edt(()->desktop.scenario().scenario()).equals(root.resolve("small.json")),"selected scenario not sent to step 3");
        }else if(!is("source","malformed"))check(1,"About the selected example",edt(v::details).toLowerCase(Locale.ROOT).contains("select"),"empty selection lacks guidance");
        // The built-in library is read-only. Exercise its button after isolated count checks.
        click(1,"Open the example library");
        waitFor(()->edt(()->!v.where().toLowerCase(Locale.ROOT).contains("opening")));
        click(1,"Runner settings (step 3)");check(1,"Runner settings (step 3)",edt(()->desktop.tabs().getSelectedIndex())==2,"runner shortcut selects wrong tab");
        edt(()->{desktop.runners().python();return null;});api(1,"Use Python");
    }

    void authoring()throws Exception {
        AuthoringView v=desktop.authoring();
        edt(()->{v.newDocument();v.addSheet("A");v.addSheet("B");v.addSheet("scratch");v.renameSheet(3,"temporary");v.removeSheet(3);
            if(is("edit","duplicate"))v.addSheet("a");
            v.selectSheet(0);v.type(0,0,"A");v.selectCell(0,1);v.insert("combi_one");v.type(1,0,"B");v.type(1,1,"FW_Combi(1)");
            v.selectSheet(1);v.type(0,0,"latency=1;");v.type(1,0,"latency=2;");v.insertRow(1);v.type(1,0,"latency=3;");v.removeRow(1);
            v.selectSheet(2);v.type(0,0,"mode=a;");v.type(1,0,"mode=b;");return null;});
        api(2,"New","Add sheet","Rename","Remove","Insert row","Remove row","Insert into the selected cell");
        check(2,"Sheets",edt(()->v.document().sheetNames()).equals(List.of("FW_Seq","A","B")),"sheet mutations or duplicate rejection failed");
        if(is("edit","boundary"))edt(()->{v.selectSheet(2);v.type(1,0,"mode=unicode_ї;");return null;});
        edt(()->{v.setControls(new ControlSheets("",is("controls","named")?List.of("one","","three"):List.of(),is("controls","named")?List.of(new ControlSheets.Verdict("7","GUIF_DEMO")):List.of()));v.applyControls();v.loadControls();return null;});
        api(2,"Apply to the document","Load from the document");
        if(is("controls","named"))check(2,"FW_Arguments: arguments, one per line",edt(()->v.controls().arguments()).equals(List.of("one","","three")),"arguments failed round trip");
        click(2,"Add verdict");
        edt(()->{JTable t=find(2,JTable.class,"FW_CUSTOM_VAR: verdict codes and their names");if(t.getRowCount()>0)t.setRowSelectionInterval(t.getRowCount()-1,t.getRowCount()-1);return null;});click(2,"Remove verdict");
        waitFuture(edt(v::validate));api(2,"Validate");check(2,"Check results",edt(v::output).contains("plan"),"valid authoring document not planned");
        Path saved=dir.resolve("authored."+option("format","json"));waitFuture(edt(()->v.save(saved)));api(2,"Save","Save as...");
        check(2,"Save",Files.isRegularFile(saved)&&Files.size(saved)>0,"save failed");
        check(2,"Save as...",edt(()->desktop.scenario().scenario()).equals(saved),"save did not select authored scenario");
        waitFuture(edt(()->v.open(saved)));api(2,"Open...","Auto-search…");
        check(2,"Sheets",edt(()->v.document().sheetNames()).containsAll(List.of("A","B")),"saved sheets missing on reopen");
        Path graph=dir.resolve("graph.dot");edt(()->{v.showGraph();v.saveGraph(graph);return null;});api(2,"Save DOT or JSON...");
        check(2,"Save DOT or JSON...",Files.readString(graph).startsWith("digraph"),"graph export lacks DOT");
        // Clipboard integration requires the real desktop; graph serialization is the backing operation.
        api(2,"Copy DOT");notes.add("Copy DOT: serialized same graph; native system clipboard excluded headless.");
    }

    void runner()throws Exception {
        RunnerSettings r=desktop.runners();
        edt(()->{find(3,JComboBox.class,"Runner").setSelectedItem(option("runner","generated-java"));return null;});
        boolean python=is("runner","python");
        for(String name:List.of("Python interpreter","Python import directories","Python working directory","Python environment variables","Python sandbox"))
            check(3,name,edt(()->find(3,JTextField.class,name).isEnabled())==python,"enabled state must equal Python runner selection");
        edt(()->{
            find(3,JTextField.class,"Add-on and dependency paths").setText(python?Path.of("fw-python/target/fw-python-0.1.0-SNAPSHOT.jar").toAbsolutePath().toString():"");
            find(3,JTextField.class,"Python interpreter").setText("/usr/bin/python3");
            find(3,JTextField.class,"Python import directories").setText(dir.toString());
            find(3,JTextField.class,"Python working directory").setText(dir.toString());
            find(3,JTextField.class,"Python environment variables").setText(is("settings","badenv")?"BROKEN":"GUIF_TEST=one");
            find(3,JTextField.class,"Python sandbox").setText(is("settings","badsandbox")?"unsupported":"");return null;});
        boolean applied=edt(r::apply);api(3,"Apply runner settings");
        if(is("settings","valid"))check(3,"Apply runner settings",applied,"valid settings rejected: "+edt(r::status));
        else check(3,"Apply runner settings",!applied&&edt(r::status).contains("Settings could not be applied"),"malformed Python settings accepted");
        Path input=switch(option("input","valid")){
            case "constraints"->write("wrong.constraints.json","{\"version\":1,\"constraints\":[]}");
            case "settings"->write("settings.properties","cf.execution.workers=1\n");
            case "results"->write("wrong-metrics.kv","candidate_id=a latency=2 FW_VAR=0\n");
            case "folder"->dir;default->scenario;};
        edt(()->{desktop.scenario().setScenario(input);return null;});api(3,"Browse...","Auto-search…");
        waitFuture(edt(()->desktop.scenario().validate()));api(3,"Validate and plan");
        String out=edt(()->desktop.scenario().output());
        if(is("input","valid"))check(3,"Validate and plan",out.contains("plan"),"valid scenario not planned: "+out.substring(0,Math.min(300,out.length())));
        else check(3,"Check results",!out.isBlank()&&!out.contains("candidates generated"),"wrong file validation not refused");
        details.put("scenario_output",out.substring(0,Math.min(600,out.length())));
        click(3,"Use the example");
        for(Watch w:watches.values())if(w.def.name().equals("Use")&&w.component instanceof JButton b&&edt(b::isEnabled))edt(()->{b.doClick(0);return null;});
    }

    void candidates()throws Exception {
        CandidatesView v=desktop.candidates();
        write("small.constraints.json","{\"version\":1,\"constraints\":[{\"id\":\"no_a\",\"polarity\":\"forbid\",\"pairs\":[{\"A\":\"latency=1;\",\"B\":\"mode=a;\"}]}]}");
        edt(()->{v.settings(option("scan","100"),Integer.parseInt(option("page","1")),is("executable","true"));return null;});
        CompletableFuture<?> f=edt(v::preview);trace.add("preview");api(5,"Preview");
        if(is("control","cancel")||is("sudden","cancel")){edt(()->{v.cancel();return null;});trace.add("cancel");api(5,"Cancel");}
        try{waitFuture(f);}catch(ExecutionException|CancellationException e){if(!is("scan","bad")&&!is("control","cancel")&&!is("sudden","cancel"))throw e;}
        details.put("preview_status",edt(v::status));
        if(is("scan","bad")){check(5,"Look at most at (test cases)",!edt(v::status).isBlank(),"invalid scan lacks feedback");return;}
        if(!is("control","cancel")&&!is("sudden","cancel")){
            check(5,"Candidate page: each candidate's ids and fate under the rules",edt(()->v.rows().getRowCount())<=Integer.parseInt(option("page","1")),"page larger than requested");
            if(edt(()->v.rows().getRowCount())>0){edt(()->{v.select(0);return null;});check(5,"Code of the selected test case",!edt(v::source).isBlank(),"selected candidate lacks source");}
            waitFuture(edt(v::nextPage));api(5,"Next page");
        }
    }

    void analysis()throws Exception {
        AnalysisView v=desktop.analysis();String body=switch(option("corpus","valid")){case "unverdicted"->"candidate_id=a latency=1\ncandidate_id=b latency=2 FW_VAR=0\n";case "csv"->"candidate_id,repeat_idx,env_id,attempt,outcome\na,0,0,0,PASS\n";case "checkpoint"->"<cf-checkpoint schema=\"1\" payloadSha256=\"\">\n";default->"candidate_id=a latency=1 FW_VAR=0\ncandidate_id=b latency=2 FW_VAR=7\n";};
        Path corpus=write("input.kv",body), coverage=coverageFixture(option("coverage","none"));
        String format=is("coverage","none")?null:option("coverage","none");
        edt(()->{v.choose(corpus,format,coverage==null?null:coverage.toString());return null;});
        CompletableFuture<?> f=edt(v::analyze);if(is("timing","double")){trace.add("analyze");waitFuture(edt(v::analyze));}try{waitFuture(f);}catch(ExecutionException e){if(is("corpus","valid")||is("corpus","unverdicted"))throw e;}
        trace.add("analyze");api(8,"Analyze","Browse...","Auto-search…");String out=edt(v::output);details.put("analysis_output",out.substring(0,Math.min(1000,out.length())));
        if(is("corpus","valid")||is("corpus","unverdicted"))check(8,"Analysis report",out.contains("2 samples, "+(is("corpus","unverdicted")?"1":"0")+" without a verdict"),"analysis sample/verdict count mismatch: "+out.substring(0,Math.min(300,out.length())));
        else check(8,"Analysis report",out.contains("metrics for step 8 (.kv)"),"misleading extension not refused with metrics guidance");
        check(8,"report file",edt(()->find(8,JTextField.class,"report file").isEnabled())==!is("coverage","none"),"coverage field enabled mismatch");
        click(8,"Use the example");for(Watch w:watches.values())if(w.def.name().equals("Use")&&w.component instanceof JButton b&&edt(b::isEnabled))edt(()->{b.doClick(0);return null;});
    }
    Path coverageFixture(String format)throws Exception {return switch(format){
        case "jacoco"->write("coverage.xml","<?xml version=\"1.0\"?><!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\"><report name=\"guif\"><package name=\"p\"><sourcefile name=\"a.java\"><line nr=\"1\" mi=\"0\" ci=\"1\" mb=\"0\" cb=\"0\"/></sourcefile></package><counter type=\"LINE\" missed=\"0\" covered=\"1\"/></report>");
        case "coverage-py"->write("coverage.json","{\"meta\":{\"branch_coverage\":false,\"show_contexts\":false},\"files\":{\"a.py\":{\"executed_lines\":[1],\"missing_lines\":[2]}}}");
        case "gcov"->write("coverage.gcov","        -:    0:Source:a.c\n        1:    1:int main() { return 0; }\n");default->null;};}

    void scorer()throws Exception {
        ScorerView v=desktop.scorer();Path text=write("lines.log",is("text","empty")?"":"run=1 latency=12 status=ok\nrun=2 latency=40 status=ok\nrun=3 latency=9 status=error\nrun=4 latency=11 status=ok\n");
        String rules=switch(option("rules","valid")){case "jsonbad"->"{ unfinished";case "regexbad"->"{\"required_regexes\":[\"(\"]}";default->"{\"forbidden_tokens\":[\"error\"],\"optimization\":{\"key\":\"latency\",\"weight\":40}}";};
        edt(()->{v.choose(text,rules);v.tune(is("threshold","boundary")?1:2,is("threshold","boundary")?1:12,is("threshold","boundary")?100.0:50.0,is("threshold","boundary")?0.0:20.0);return null;});
        api(9,"Browse...","Load rules...","Auto-search…");
        try{waitFuture(edt(v::score));}catch(ExecutionException e){if(is("rules","valid"))throw e;}api(9,"Score");
        if(is("rules","valid")){
            check(9,"Scored lines: each line's score, decision and features",edt(v::visibleRows)==(is("text","empty")?0:4),"score row count mismatch");
            edt(()->{v.setFilter(is("filter","all")?"":option("filter",""));return null;});
            int expected=is("text","empty")?0:is("filter","all")?4:is("filter","ERROR")?1:0;
            check(9,"Show lines containing",edt(v::visibleRows)==expected,"filter count mismatch");
            if(expected>0){edt(()->{v.select(0);return null;});check(9,"Why this score",!edt(v::detail).isBlank(),"score detail empty");}
            Path csv=dir.resolve("scores.csv");waitFuture(edt(()->v.exportCsv(csv)));api(9,"Export CSV...");check(9,"Export CSV...",Files.size(csv)>0,"empty CSV export");
            JTabbedPane charts=edt(v::charts);check(9,"Optimization charts",edt(charts::getTabCount)==4,"expected four chart panels");edt(()->{for(int i=0;i<charts.getTabCount();i++)paint(charts.getComponentAt(i));return null;});api(9,"Optimization charts");
            waitFuture(edt(v::autoPlan));api(9,"Auto-plan");
        }else {
            check(9,"Summary of all scores",edt(v::summary).contains("INPUT_MALFORMED"),"malformed rules not reported");
            String before=edt(v::rulesText);try{waitFuture(edt(v::autoPlan));}catch(ExecutionException ignored){}api(9,"Auto-plan");
            String after=edt(v::rulesText);details.put("rules_before",before);details.put("rules_after",after);
            check(9,"Your scoring rules as JSON (required_tokens, token_weights, numeric_ranges, optimizations, ...)",is("rules","jsonbad")?after.equals(before):after.contains("required_regexes")&&after.contains("\"(\""),"auto-plan erased invalid manual rules");
        }
        for(Watch w:watches.values())if(w.def.name().equals("Use")&&w.component instanceof JButton b&&edt(b::isEnabled))edt(()->{b.doClick(0);return null;});
    }
    static void paint(Component c){c.setSize(1180,720);layout(c);BufferedImage im=new BufferedImage(1180,720,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();try{c.paint(g);}finally{g.dispose();}}
    static void layout(Component c){if(c instanceof Container k){k.doLayout();for(Component x:k.getComponents())layout(x);}}

    static final String EMPTY_RULES="{\"version\":1,\"constraints\":[]}";
    static final String PAIR_RULES="{\"version\":1,\"constraints\":[{\"id\":\"pair1\",\"polarity\":\"forbid\",\"pairs\":[{\"A\":\"latency=1;\",\"B\":\"mode=a;\"}]}]}";
    static final String FORMULA_RULES="{\"version\":1,\"constraints\":[{\"id\":\"formula1\",\"polarity\":\"require\",\"assert\":{\"any\":[{\"sheet\":\"A\",\"eq\":\"latency=1;\"},{\"sheet\":\"B\",\"eq\":\"mode=b;\"}]}}]}";
    JTabbedPane sieveTabs(){return all(scope(4)).stream().filter(c->c instanceof JTabbedPane t&&t.indexOfTab("Sieve graph")>=0).map(JTabbedPane.class::cast).findFirst().orElseThrow();}
    void sieveGraph()throws Exception {
        SieveWorkbench v=desktop.sieve();waitFuture(edt(v::open));api(4,"Open step 3 scenario");
        edt(()->{v.pair(new SieveCanvas.Value("A","latency=1;"),new SieveCanvas.Value("B","mode=a;"));return null;});trace.add("pair");
        check(4,"Open step 3 scenario",edt(v::opened)!=null,"workbench did not open");
        check(4,"Save for scenario",edt(()->v.document().rules().size())==1,"pair gesture did not create rule");
        if(is("rules","invalid")){
            var before=edt(v::document);try{waitFuture(edt(()->v.applyJson("{ broken")));}catch(ExecutionException expected){}
            check(4,"Validate",edt(v::document).equals(before),"invalid JSON destroyed previous document");
        }else if(is("rules","empty"))waitFuture(edt(()->v.applyJson(EMPTY_RULES)));
        if(is("operation","import")){Path p=write("import.constraints.json",PAIR_RULES);waitFuture(edt(()->v.importFrom(p)));api(4,"Import constraints…","Auto-search…");trace.add("import");}
        waitFuture(edt(v::save));api(4,"Save for scenario");trace.add("save");
        check(4,"Save for scenario",Files.isRegularFile(dir.resolve("small.constraints.json"))&&!edt(v::dirty),"save did not persist/clear dirty flag");
        if(is("operation","undo")){String saved=Files.readString(dir.resolve("small.constraints.json"));edt(()->{v.undo();return null;});trace.add("undo");api(4,"Undo");edt(()->{v.redo();return null;});trace.add("redo");api(4,"Redo");check(4,"Redo",!edt(v::dirty),"redo to saved state remains dirty");check(4,"Undo",Files.readString(dir.resolve("small.constraints.json")).equals(saved),"undo changed persisted rules without save");}
        edt(()->{for(Watch w:watches.values())if(w.def.kind().equals("spinner")&&w.component instanceof JSpinner s)s.setValue(Integer.parseInt(option("scan","100")));return null;});
        waitFuture(edt(()->v.validate(false)));api(4,"Validate");waitFuture(edt(()->v.validate(true)));api(4,"Check impact");
        for(String b:List.of("Fit","+","−","Arrange"))click(4,b);
        edt(()->{paint(v.canvas());sieveTabs().setSelectedIndex(1);return null;});
        // Matrix actions require a selected cell; use the first meaningful cell.
        edt(()->{for(Watch w:watches.values())if(w.def.kind().equals("table")&&w.component instanceof JTable t&&t.getRowCount()>0&&t.getColumnCount()>0){t.changeSelection(0,0,false,false);}return null;});
        click(4,"Add pair at cell");click(4,"Edit cell rule");
        click(4,"Group selected values");click(4,"Cancel work");
        waitFuture(edt(v::save));
        Files.writeString(dir.resolve("export.constraints.json"),Files.readString(dir.resolve("small.constraints.json")));api(4,"Export JSON…");
        notes.add("Face 2 browser launch excluded headless; toolbar component rendered at 1180 px.");
        edt(()->{paint(desktop.component());return null;});
        exercised(4,"Face 2 (Sieve) ↗","offscreen-layout");
    }

    void sieveData()throws Exception {
        SieveWorkbench v=desktop.sieve();waitFuture(edt(v::open));waitFuture(edt(()->v.applyJson(PAIR_RULES)));
        int owner=switch(option("draft","json")){case "data"->2;case "rule"->0;default->3;};
        int target=switch(option("switch","graph")){case "data"->2;case "json"->3;default->0;};
        edt(()->{sieveTabs().setSelectedIndex(owner);if(owner==3)find(4,JTextArea.class,"Sieve execution JSON").append(" ");else if(owner==2)find(4,JButton.class,"Add attribute").doClick(0);else {v.selectRule(0);find(4,JTextField.class,"Rule ID").setText("draft-name");}return null;});trace.add("draft_"+option("draft","json"));
        if(is("sudden","undo")){edt(()->{v.undo();return null;});trace.add("sudden_undo");}
        else if(is("sudden","validate")){try{waitFuture(edt(()->v.validate(false)));}catch(ExecutionException ignored){}trace.add("sudden_validate");}
        if(is("order","before")){
            edt(()->{sieveTabs().setSelectedIndex(target);return null;});trace.add("switch_"+option("switch","graph"));
            check(owner==3?id(4,"#Sieve execution JSON"):id(4,"Apply data"),edt(()->sieveTabs().getSelectedIndex())==owner||target==owner,"pending draft allowed an editor switch");
        }
        String discard=owner==3?"Discard JSON draft":owner==2?"Discard data draft":"Discard draft";
        if(is("resolution","apply")){
            if(owner==3){String draft=edt(()->find(4,JTextArea.class,"Sieve execution JSON").getText());waitFuture(edt(()->v.applyJson(draft)));api(4,"Apply JSON");}
            else if(owner==0){click(4,"Apply rule");waitFor(()->edt(()->v.document().rules().toString()).contains("draft-name"));}
            else {
                boolean refused=edt(()->{var f=SieveWorkbench.class.getDeclaredField("data");f.setAccessible(true);SieveDataEditor editor=(SieveDataEditor)f.get(v);try{editor.draft();return false;}catch(IllegalArgumentException expected){return true;}});
                api(4,"Apply data");check(4,"Apply data",refused,"blank attribute draft unexpectedly validated");click(4,"Discard data draft");
            }trace.add("apply");
        }else {click(4,discard);trace.add("discard");}
        // Data's deliberately blank added attribute is invalid; applying it must retain the draft, then discard.
        if(is("resolution","apply")&&owner==2)trace.add("apply_empty_data_then_discard");
        edt(()->{sieveTabs().setSelectedIndex(target);return null;});trace.add("switch_"+option("switch","graph"));
        check(4,owner==3?"Discard JSON draft":"Discard data draft",edt(()->sieveTabs().getSelectedIndex())==target,"resolved draft still blocks switch");
        // Exercise the parameter and order toolbar in its actual owning editor.
        edt(()->{sieveTabs().setSelectedIndex(2);return null;});
        for(String b:List.of("Add attribute","Remove attribute","Add value","↑","↓","Remove"))click(4,b);
        click(4,"Discard data draft");
        waitFuture(edt(v::save));
    }

    void sieveRules()throws Exception {
        SieveWorkbench v=desktop.sieve();waitFuture(edt(v::open));waitFuture(edt(()->v.applyJson(is("body","formula")?FORMULA_RULES:PAIR_RULES)));
        edt(()->{v.selectRule(0);return null;});
        click(4,"Duplicate");click(4,"↑");click(4,"↓");click(4,"Find on graph");click(4,"Delete");click(4,"New rule");
        click(4,"Discard draft");
        edt(()->{v.selectRule(0);find(4,JTextField.class,"Rule ID").setText(is("edit","invalid")?"":"renamed");find(4,JTextField.class,"Description").setText("dogfood description");return null;});trace.add("edit_rule");
        if(is("resolution","apply")){click(4,"Apply rule");trace.add("apply_rule");if(!is("edit","invalid")){waitFor(()->edt(()->v.document().rules().toString()).contains("renamed"));check(4,"Apply rule",edt(()->v.document().rules().toString()).contains("renamed"),"valid rule rename was not applied");}}
        else {click(4,"Discard draft");trace.add("discard_rule");}
        if(is("resolution","undo")){edt(()->{v.undo();v.redo();return null;});trace.add("undo");trace.add("redo");}
        click(4,"Discard draft");
        if(is("body","formula")){
            edt(()->{v.selectRule(0);JTree tree=find(4,JTree.class,"Parts of the condition");if(tree.getRowCount()>0)tree.setSelectionRow(0);return null;});
            for(String b:List.of("+ condition","all of","any of","not","Up","Down"))click(4,b);
            edt(()->{find(4,JTextField.class,"JSON").setText(is("edit","invalid")?"{ broken":"{\"sheet\":\"A\",\"eq\":\"latency=1;\"}");return null;});
            // Scope the duplicate Apply JSON to the formula panel, not execution JSON.
            Watch jsonApply=watches.values().stream().filter(w->w.def.name().equals("Apply JSON")).findFirst().orElseThrow();
            edt(()->{if(jsonApply.component instanceof JButton b&&b.isEnabled())b.doClick(0);return null;});
            click(4,"Discard draft");
        }
        waitFuture(edt(v::save));
        // Accessibility is a real requirement: a blank accessible name cannot identify an editable field.
        edt(()->{for(Watch w:watches.values())if(w.component!=null&&w.def.name().equals("(unnamed)")&&List.of("text field","choice","spinner").contains(w.def.kind()))w.assertion(!accessible(w.component).isBlank(),"Sieve input has no accessible name");return null;});
    }

    void largeNestedScenario()throws Exception {
        List<List<String>> a=new ArrayList<>(),b=new ArrayList<>();for(int i=0;i<16;i++)a.add(List.of("latency="+(i+1)+";"));for(int i=0;i<4;i++)b.add(List.of("mode=m"+i+";"));
        Files.writeString(scenario,json(Map.of("sheets",new LinkedHashMap<>(Map.of("FW_Seq",List.of(List.of("A","FW_Combi(1)"),List.of("B","FW_Combi(1)")),"A",a,"B",b)))));
    }
    String runState(RunController c){return c.view().state().name();}
    boolean terminal(RunController c){return List.of("COMPLETED","CANCELLED","FAILED").contains(runState(c));}
    void run()throws Exception {
        largeNestedScenario();RunView v=desktop.run();RunController c=v.controller();AtomicBoolean fired=new AtomicBoolean();
        boolean sequences=family.equals("t6_sequences"),pause=sequences||is("control","pause")||is("control","cancel");int threshold=is("timing","later")?8:1;
        c.observe(p->{if(p.committed()>=threshold&&fired.compareAndSet(false,true)){
            if(is("sudden","cancel")&&!sequences){trace.add("sudden_cancel");c.cancel();}
            else if(pause){trace.add("hook_pause");c.pause();}
        }});
        waitFuture(edt(v::start));trace.add("start");api(6,"Run");
        if(pause&&!is("sudden","cancel")||sequences){waitFor(()->runState(c).equals("PAUSED")||terminal(c));}
        if(sequences){
            stepRun(c,option("first","pause"));
            if(options.containsKey("sudden")){if(is("sudden","cancel")){trace.add("sudden_cancel");c.cancel();api(6,"Cancel");}else{edt(()->{v.refresh();return null;});trace.add("sudden_refresh");}}
            stepRun(c,option("second","resume"));stepRun(c,option("third","cancel"));
            logical("x_run_sequence");
        }else if(!is("sudden","cancel")){
            if(is("control","pause")){edt(()->{v.refresh();return null;});check(6,"Pause",runState(c).equals("PAUSED"),"pause hook did not settle");api(6,"Pause");stepRun(c,"resume");}
            else if(is("control","cancel"))stepRun(c,"cancel");
        }
        if(runState(c).equals("PAUSED")||runState(c).equals("PAUSING")){c.resume();trace.add("cleanup_resume");api(6,"Resume");}
        waitFor(()->terminal(c)&&!c.view().report().isEmpty());
        edt(()->{v.refresh();return null;});
        check(6,"Run report",!edt(v::output).isBlank()&&edt(v::status).contains("samples committed"),"terminal report/status missing");
        String kind=option("export","metrics");Path file=dir.resolve("run."+kind);waitFuture(edt(()->v.export(kind,file)));api(6,"Export...");
        check(6,"Export...",Files.size(file)>0&&edt(v::status).contains("exported the run's "+kind),"selected export missing or wrong status");
        if(kind.equals("metrics"))check(6,"Export...",edt(v::status).contains("study it in step 8"),"metrics export lacks step 8 guidance");
        Path kv=kind.equals("metrics")?file:dir.resolve("run-metrics.kv");if(!kind.equals("metrics"))waitFuture(edt(()->v.export("metrics",kv)));
        waitFuture(edt(()->{desktop.analysis().choose(kv,null,null);return desktop.analysis().analyze();}));
        String analyzed=edt(()->desktop.analysis().output());long committed=c.view().committed();
        long verdictLines;try(var lines=Files.lines(kv)){verdictLines=lines.filter(line->!line.isBlank()&&!line.startsWith("#")).count();}
        String oracle=sequences?"x_run_sequence":"x_run_roundtrip";logical(oracle);check(oracle,verdictLines<=committed&&analyzed.contains("("+verdictLines+" samples, 0 without a verdict)"),"Run metrics export/analysis lost verdict-bearing sample count");
        details.put("nested_verdict_lines",verdictLines);details.put("nested_unverdicted",committed-verdictLines);
        details.put("nested_committed",committed);details.put("nested_state",runState(c));details.put("metrics",kv.toString());details.put("analysis_output",analyzed.substring(0,Math.min(1200,analyzed.length())));
        click(6,"Runner settings (step 3)");edt(()->{desktop.runners().python();return null;});api(6,"Use Python");
    }
    void stepRun(RunController c,String action)throws Exception {
        String before=runState(c);trace.add(action);
        switch(action){case "pause"->c.pause();case "resume"->c.resume();case "cancel"->c.cancel();default->throw new IllegalArgumentException(action);}
        api(6,Character.toUpperCase(action.charAt(0))+action.substring(1));
        if(action.equals("pause")&&!List.of("COMPLETED","CANCELLED","CANCELLING","FAILED").contains(before))waitFor(()->runState(c).equals("PAUSED")||terminal(c));
        if(action.equals("cancel"))waitFor(()->terminal(c));
        if(before.equals("CANCELLED"))check("x_run_sequence",runState(c).equals("CANCELLED"),"action after cancellation resurrected run: "+action);
    }

    void campaign()throws Exception {
        largeNestedScenario();CampaignView v=desktop.campaign();CampaignController c=v.controller();AtomicBoolean fired=new AtomicBoolean();
        c.observe((iteration,p)->{if(p.committed()>=1&&fired.compareAndSet(false,true)){
            if(is("sudden","cancel")){trace.add("sudden_cancel");c.cancel();}else if(!is("control","finish")){trace.add("hook_pause");c.pause();}
        }});
        waitFuture(edt(v::start));api(7,"Run campaign");trace.add("campaign_start");
        if(!is("control","finish")&&!is("sudden","cancel")){
            waitFor(()->c.view().state().name().equals("PAUSED")||!c.view().going());api(7,"Pause");
            if(is("control","cancel")){c.cancel();api(7,"Cancel");trace.add("cancel");}else{c.resume();api(7,"Resume");trace.add("resume");}
        }
        waitFor(()->!c.view().going());edt(()->{v.refresh();return null;});
        check(7,"Campaign report",edt(v::output).contains("campaign "),"terminal campaign report missing");
        if(is("control","finish")&&!is("sudden","cancel"))check(7,"Campaign lineage: each iteration's run, counts, winners and front",edt(()->v.lineage().getRowCount())==Integer.parseInt(option("iterations","2")),"lineage count differs from requested iterations");
        details.put("campaign_output",edt(v::output).substring(0,Math.min(1000,edt(v::output).length())));
    }

    void dialogs()throws Exception {
        Path home=Files.createDirectories(dir.resolve("home"));
        if(is("place","example")){Path ex=home.resolve("scenarios/showcase/showcase.xlsx");Files.createDirectories(ex.getParent());Files.copy(Path.of("scenarios/showcase/showcase.xlsx"),ex);}
        try(FileTrail trail=FileTrail.inMemory()){
            Navigator nav=new Navigator(trail,()->is("place","remembered")?scenario:null,new Examples(List.of(home)));
            if(is("place","remembered")){trail.record(scenario);trail.settle(5000);}
            for(Navigator.Purpose purpose:Navigator.Purpose.values()){
                String group=purpose.name().contains("SCENARIO")||purpose.name().contains("DOCUMENT")?"scenario":purpose.name().startsWith("EXPORT")||purpose.name().equals("RESULTS_OPEN")?"run":List.of("PROGRAM_OPEN","IMPORTS_OPEN","WORKDIR_OPEN","ADDON_OPEN").contains(purpose.name())?"python":"other";
                if(!group.equals(option("group","scenario")))continue;
                String wid="x_dialog_"+purpose.name().toLowerCase(Locale.ROOT);logical(wid);
                Path current=is("current","missing")?home.resolve("missing.json"):null;
                edt(()->{
                    JFileChooser chooser=nav.chooser(purpose,current);String title=chooser.getDialogTitle();
                    check(wid,title.startsWith((purpose.open()?"Open ":"Save ")+purpose.what()+" · here: "),"dialog title omits purpose or location: "+title);
                    JComponent guide=chooser.getAccessory();check(wid,guide!=null&&"What to pick".equals(guide.getName()),"missing What to pick accessory");
                    Path example=nav.example(purpose);if(example!=null){for(Component c:all(guide))if(c instanceof JButton b&&b.getText().equals("Show the example")){b.doClick(0);check(wid,example.toFile().equals(chooser.getSelectedFile()),"Show the example did not select example");}}
                    if(purpose==Navigator.Purpose.WORKDIR_OPEN)check(wid,chooser.getFileSelectionMode()==JFileChooser.DIRECTORIES_ONLY,"working directory chooser accepts arbitrary files");
                    return null;
                });
            }
            logical("x_trail");
            if(is("place","remembered"))check("x_trail",nav.place(Navigator.Purpose.SCENARIO_OPEN,null).file().equals(scenario),"in-memory trail did not remember scenario");
        }
    }

    void search()throws Exception {
        FileSeeker.Kind kind=FileSeeker.Kind.valueOf(option("kind","SCENARIO"));Path home=Files.createDirectories(dir.resolve("search-home"));
        Path root=Files.createDirectories(home.resolve("files"));Path location=is("content","hidden")?root.resolve(".git/objects"):is("roots","deep")?root.resolve("a/b/c"):root.resolve("likely");Files.createDirectories(location);
        boolean positive=!is("content","misleading");Path hit=searchFixture(kind,location,positive);
        List<FileSeeker.Root> roots=new ArrayList<>();roots.add(new FileSeeker.Root(root,8,3,"whole fixture"));
        if(is("roots","duplicate")){roots.add(new FileSeeker.Root(root,8,4,"duplicate fixture"));if(!is("content","hidden"))roots.add(new FileSeeker.Root(location,2,0,"likely fixture"));}
        if(kind==FileSeeker.Kind.PYTHON_WORKDIR){roots.clear();roots.add(new FileSeeker.Root(location,0,0,"scenario folder",true));}
        FileSeeker.Budget budget=new FileSeeker.Budget(2000,is("budget","cap")?1:200,is("budget","cap")?1:100);
        AtomicInteger progress=new AtomicInteger();FileSeeker.Result result=FileSeeker.seek(kind,roots,budget,()->is("budget","stop"),progress::set);
        for(String id:List.of("x_search_match","x_search_roots","x_search_budget","x_search_filter","x_search_selection"))logical(id);
        Set<Path> paths=new HashSet<>();for(var f:result.found())paths.add(f.path());
        check("x_search_roots",paths.size()==result.found().size(),"duplicate roots produced duplicate files");
        if(is("budget","stop")){Set<Path> offered=new HashSet<>();for(var r:roots)if(r.itself())offered.add(r.folder());check("x_search_budget",result.folders()==0&&!result.complete()&&result.found().stream().allMatch(f->offered.contains(f.path())),"stop walked folders or returned an undiscovered file");}
        else if(is("budget","cap"))check("x_search_budget",result.folders()<=1&&result.found().size()<=1,"folder/list budget exceeded");
        else {
            boolean expect=kind==FileSeeker.Kind.PYTHON_WORKDIR||(positive||kind==FileSeeker.Kind.METRICS)&&!is("content","hidden");
            check("x_search_match",expect?paths.contains(hit):paths.isEmpty(),"classification mismatch: expected "+(expect?hit:"none")+" got "+paths);
        }
        try(FileTrail trail=FileTrail.inMemory()){
            Navigator nav=new Navigator(trail,()->hit,new Examples(List.of(home)));AutoSearch search=new AutoSearch(nav,home,budget);
            var planned=search.roots(kind,null);check("x_search_roots",!planned.isEmpty(),"AutoSearch produced no roots");
            // Exercise asynchronous dispatch with a pre-set stop; no filesystem outside the fixture is walked.
            var stopped=search.search(kind,null,()->true,n->{},r->{}).get(10,TimeUnit.SECONDS);
            Set<Path> offered=new HashSet<>();for(var r:planned)if(r.itself())offered.add(r.folder());
            check("x_search_budget",stopped.folders()==0&&stopped.found().stream().allMatch(f->offered.contains(f.path())),"AutoSearch walked after pre-set stop");
            AtomicReference<Path> chosen=new AtomicReference<>();
            edt(()->{
                AutoSearch.Results view=new AutoSearch.Results(kind,chosen::set,()->{},()->{});view.show(result);
                check("x_search_filter",view.table().getRowCount()==result.found().size(),"results table count differs from search");
                if(!result.found().isEmpty()){
                    Path first=result.found().getFirst().path();view.filter().setText(first.getFileName().toString().toUpperCase(Locale.ROOT));
                    check("x_search_filter",view.table().getRowCount()>0,"uppercase filter lost matching row");view.table().setRowSelectionInterval(0,0);Path selected=view.selected().path();view.use.doClick(0);check("x_search_selection",selected.equals(chosen.get()),"use did not return selected path");
                }else check("x_search_selection",view.selected()==null,"empty result has a selected object");
                view.filter().setText("no_such_guif_file_123456");check("x_search_filter",view.table().getRowCount()==0,"nonmatching filter did not clear rows");
                return null;
            });
        }
        details.put("search_found",result.found().stream().map(f->f.path().toString()).toList());details.put("search_complete",result.complete());details.put("search_folders",result.folders());
    }
    Path searchFixture(FileSeeker.Kind kind,Path folder,boolean positive)throws Exception {
        Path p;
        switch(kind){
            case SCENARIO:p=folder.resolve("sample.json");Files.writeString(p,positive?"{\"sheets\":{\"FW_Seq\":[],\"A\":[[\"a\"]]}}":"{\"title\":\"ordinary document\"}");break;
            case LIBRARY:p=folder.resolve("generator_trunk");Files.createDirectories(p.resolve("intake"));Files.writeString(p.resolve(positive?"intake/verified_examples.json":"intake/readme.json"),"[]");break;
            case CONSTRAINTS:p=folder.resolve(positive?"sample.constraints.json":"ordinary.json");Files.writeString(p,positive?PAIR_RULES:"{\"title\":\"ordinary document\"}");break;
            case METRICS:p=folder.resolve("run.kv");Files.writeString(p,positive?"candidate_id=a latency=1 FW_VAR=0\n":"these are not measurements\n");break;
            case JACOCO:p=folder.resolve("coverage.xml");Files.writeString(p,positive?"<?xml version=\"1.0\"?><!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\"><report name=\"app\">":"<report name=\"ordinary\"/>");break;
            case COVERAGE_PY:p=folder.resolve("coverage.json");Files.writeString(p,positive?"{\"meta\":{\"branch_coverage\":false,\"show_contexts\":false},\"files\":{}}":"{\"universe_size\":720,\"orders\":[]}");break;
            case GCOV:p=folder.resolve("gcov-summary.out");Files.writeString(p,positive?"File 'a.c'\nLines executed:85.00% of 20\n":"ordinary output\n");break;
            case TEXT:p=folder.resolve("build.log");Files.writeString(p,positive?"BUILD SUCCESS\n"+"text data line\n".repeat(40):"one line\n");break;
            case RULES:p=folder.resolve("config.json");Files.writeString(p,positive?"{\"required_tokens\":[\"status\"]}":"{\"ordinary\":true}");break;
            case ADDON:p=folder.resolve("addon.jar");try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(p))){if(positive){jar.putNextEntry(new JarEntry("META-INF/services/org.ghyura.cf.spi.SutPlugin"));jar.write("example.absent.GuifPlugin\n".getBytes(StandardCharsets.UTF_8));jar.closeEntry();}}break;
            case PYTHON:p=folder.resolve("bin/python3");Files.createDirectories(p.getParent());Files.writeString(p,"#!/bin/sh\nexit 0\n");if(positive)p.toFile().setExecutable(true);break;
            case PYTHON_MODULES:p=folder.resolve("package");Files.createDirectories(p);Files.writeString(p.resolve(positive?"model.py":"README.txt"),"x = 1\n");break;
            case PYTHON_WORKDIR:p=folder;break;
            default:throw new IllegalArgumentException(kind.name());
        }
        return p;
    }

    void shell()throws Exception {
        logical("x_tabs");logical("x_hints");logical("x_zebra");logical("x_lifecycle");
        edt(()->{
            check("x_tabs",desktop.tabs().getTabCount()==9,"expected nine tabs");for(int i=0;i<9;i++){int n=is("visits","reverse")?8-i:i;desktop.tabs().setSelectedIndex(n);check("x_tabs",desktop.tabs().getTitleAt(n).startsWith((n+1)+" · "),"tab title/order mismatch");paint(desktop.component());}
            JToggleButton button=desktop.hintsButton();boolean before=button.isSelected();
            desktop.hints().key((JLayer<JComponent>)desktop.component());
            if(is("hints","f1")){desktop.component().getActionMap().get("cf.hints.toggle").actionPerformed(new ActionEvent(desktop.component(),0,"F1"));check("x_hints",button.isSelected()!=before,"F1 failed to toggle hints");}
            else {if(button.isSelected()!=is("hints","on"))button.doClick(0);check("x_hints",button.isSelected()==is("hints","on"),"hints button state mismatch");}
            @SuppressWarnings("unchecked") JLayer<JComponent> layer=(JLayer<JComponent>)desktop.component();
            desktop.tabs().setSelectedIndex(2);paint(layer);Rectangle bounds=desktop.tabs().getBoundsAt(2);Point point=SwingUtilities.convertPoint(desktop.tabs(),new Point(bounds.x+5,bounds.y+5),layer);
            desktop.hints().hover(layer,point);desktop.hints().settle(layer);
            check("x_hints",button.isSelected()||desktop.hints().text()==null,"disabled hints still visible");
            Object value=is("cells","number")?7:is("cells","boolean")?true:"text";
            JTable table=new JTable(new javax.swing.table.DefaultTableModel(new Object[][]{{value},{value}},new Object[]{"value"}){public Class<?> getColumnClass(int c){return value.getClass();}});
            Color color=table.prepareRenderer(table.getCellRenderer(1,0),1,0).getBackground();
            check("x_zebra",new Color(0xe4ebf5).equals(color),"odd "+option("cells","text")+" row color is "+color);
            check("x_lifecycle",Frame.getFrames().length==0,"headless run created a Frame");return null;
        });
        // Missing prerequisites must produce guidance without starting background executions.
        waitFuture(edt(()->desktop.candidates().preview()));waitFuture(edt(()->desktop.run().start()));waitFuture(edt(()->desktop.campaign().start()));
        for(String message:List.of(edt(()->desktop.candidates().status()),edt(()->desktop.run().output()),edt(()->desktop.campaign().output())))check("x_tabs",message.contains("Choose a scenario in step 3"),"missing-scenario action lacked guidance: "+message);
    }

    void exerciseNonButtons()throws Exception {
        edt(()->{
            for(Watch w:watches.values()){
                Component c=w.component;if(c==null||c instanceof AbstractButton)continue;
                if(c instanceof JTextComponent t){String before=t.getText();t.selectAll();t.setCaretPosition(0);w.exercise("text-selection");w.assertion(before.equals(t.getText()),"selection modified text");}
                else if(c instanceof JSpinner s){Object before=s.getValue();try{Object next=s.getNextValue();if(s.isEnabled()&&next!=null){s.setValue(next);s.setValue(before);w.exercise("value-roundtrip");w.assertion(before.equals(s.getValue()),"spinner failed round trip");}else w.exercise("boundary-inspection");}catch(ClassCastException ex){w.exercise("spinner-next");w.assertion(false,"spinner next value throws "+ex.getClass().getSimpleName()+": "+ex.getMessage());}}
                else if(c instanceof JComboBox<?> b){int before=b.getSelectedIndex();if(b.isEnabled()&&b.getItemCount()>1){b.setSelectedIndex((Math.max(before,0)+1)%b.getItemCount());if(before>=0&&before<b.getItemCount())b.setSelectedIndex(before);w.exercise("choice-roundtrip");}else w.exercise("disabled-or-single-choice");}
                else if(c instanceof JTable t){if(t.getRowCount()>0&&t.getColumnCount()>0){t.changeSelection(0,0,false,false);w.exercise("row-selection");}else w.exercise("empty-table-inspection");}
                else if(c instanceof JList<?> l){if(l.getModel().getSize()>0){l.setSelectedIndex(0);w.exercise("row-selection");}else w.exercise("empty-list-inspection");}
                else if(c instanceof JTree t){if(t.getRowCount()>0){t.setSelectionRow(0);w.exercise("tree-selection");}else w.exercise("empty-tree-inspection");}
                else if(c instanceof JTabbedPane t){int index=t.indexOfTab(w.def.name());if(index>=0){t.setSelectedIndex(index);w.exercise("tab-selection");}}
                if(w.def.tab()==4&&w.def.name().equals("(unnamed)")&&List.of("text field","choice","spinner").contains(w.def.kind()))w.assertion(!accessible(c).isBlank(),"Sieve input has no accessible name");
                w.snapshot("after-exercise");
            }
            // Restore all Sieve drafts before closure; none of these actions shows a modal.
            if(family.startsWith("t4_"))for(String label:List.of("Discard draft","Discard data draft","Discard JSON draft"))for(Component c:all(scope(4)))if(c instanceof JButton b&&label.equals(b.getText())&&b.isEnabled())b.doClick(0);
            return null;
        });
    }
    Navigator.Purpose purpose(Watch w){String n=w.def.name();int tab=w.def.tab(),occ=w.def.occurrence();
        if(tab==1&&n.equals("Open a library..."))return Navigator.Purpose.LIBRARY_OPEN;
        if(tab==2){if(n.equals("Open..."))return Navigator.Purpose.DOCUMENT_OPEN;if(n.equals("Save as..."))return Navigator.Purpose.DOCUMENT_SAVE;if(n.equals("Save DOT or JSON..."))return Navigator.Purpose.GRAPH_SAVE;}
        if(tab==3){if(n.equals("Browse..."))return occ==0?Navigator.Purpose.SCENARIO_OPEN:occ==1?Navigator.Purpose.PROGRAM_OPEN:Navigator.Purpose.WORKDIR_OPEN;if(n.equals("Add..."))return occ==0?Navigator.Purpose.ADDON_OPEN:Navigator.Purpose.IMPORTS_OPEN;}
        if(tab==4){if(n.equals("Import constraints…"))return Navigator.Purpose.CONSTRAINTS_OPEN;if(n.equals("Export JSON…"))return Navigator.Purpose.CONSTRAINTS_SAVE;}
        if(tab==6&&n.equals("Export..."))return Navigator.Purpose.EXPORT_METRICS;
        if(tab==8&&n.equals("Browse..."))return occ==0?Navigator.Purpose.RESULTS_OPEN:Navigator.Purpose.COVERAGE_OPEN;
        if(tab==9){if(n.equals("Browse..."))return Navigator.Purpose.TEXT_OPEN;if(n.equals("Load rules..."))return Navigator.Purpose.RULES_OPEN;if(n.equals("Export CSV..."))return Navigator.Purpose.SCORES_SAVE;}
        return null;
    }
    void dialogProxies()throws Exception {
        edt(()->{
            for(Watch w:watches.values()){
                if(w.component instanceof AbstractButton b&&w.def.name().equals("Auto-search…")){if(b.isEnabled())b.doClick(0);w.exercise("headless-noop-plus-search-scenarios");}
                Navigator.Purpose p=purpose(w);if(p!=null){JFileChooser chooser=desktop.navigator().chooser(p,null);w.exercise("chooser-backing-api");w.assertion(chooser.getAccessory()!=null&&"What to pick".equals(chooser.getAccessory().getName()),"dialog backing API lacks guidance");}
                if(w.component instanceof JCheckBox b){if(b.isEnabled()){boolean before=b.isSelected();b.doClick(0);b.doClick(0);w.exercise("toggle-roundtrip");w.assertion(before==b.isSelected(),"toggle did not round trip");}}
            }
            return null;
        });
    }
    void minimal()throws Exception {
        switch(option("repro","")){
            case "spinner"->{
                waitFuture(edt(()->desktop.sieve().open()));waitFuture(edt(()->desktop.sieve().applyJson(is("rule_body","formula")?FORMULA_RULES:PAIR_RULES)));edt(()->{desktop.sieve().selectRule(0);return null;});
                String direction=option("spinner_direction","next");
                Watch w=watches.get("t4_spinner_unnamed_2");edt(()->{
                    JSpinner spinner=(JSpinner)w.component;
                    details.put("spinner_enabled",spinner.isEnabled());
                    if(!spinner.isEnabled()){w.exercise("disabled-boundary-inspection");w.assertion(true,"");return null;}
                    if(options.containsKey("spinner_value"))spinner.setValue(Long.parseLong(options.get("spinner_value")));
                    w.exercise("spinner-"+direction);
                    try{if(direction.equals("next"))spinner.getNextValue();else spinner.getPreviousValue();w.assertion(true,"");}
                    catch(ClassCastException e){w.assertion(false,"loaded rule: enabled="+spinner.isEnabled()+"; "+direction+" arrow throws ClassCastException");}
                    return null;
                });trace.add("open_"+option("rule_body","pair")+"_rule");trace.add("spinner_"+direction);
            }
            case "accessibility"->{edt(()->{for(Watch w:watches.values())if(w.component!=null&&w.def.name().equals("(unnamed)")&&List.of("text field","choice","spinner").contains(w.def.kind())){w.exercise("accessible-name-inspection");w.assertion(!accessible(w.component).isBlank(),"Sieve input has no accessible name");}return null;});trace.add("inspect_accessible_names");}
            case "coverage_plan"->{
                Path log=dir.resolve("plan.log");Process process=new ProcessBuilder("java","-Djava.awt.headless=true","-jar",Path.of("fw-app/target/fw-app.jar").toAbsolutePath().toString(),"plan","--scenario",scenario.toString(),"--set","cf.coverage.strength=2","--set","cf.coverage.budget=99","--set","cf.keys.encoding=SIGNED_BYTE","--scan","100").redirectErrorStream(true).redirectOutput(log.toFile()).start();
                if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new TimeoutException("plan subprocess");}
                String output=Files.readString(log);api(3,"Validate and plan");check(3,"Validate and plan",process.exitValue()==0,"plan with budget >= rows failed: "+output.substring(0,Math.min(350,output.length())));details.put("plan_exit",process.exitValue());trace.add("plan_with_budget_exceeding_rows");
            }
            case "wrong_metrics"->{
                Path file=write("checkpoint-disguised.kv","<cf-checkpoint schema=\"1\" payloadSha256=\"\">\n");
                waitFuture(edt(()->{desktop.analysis().choose(file,null,null);return desktop.analysis().analyze();}));
                String output=edt(()->desktop.analysis().output());api(8,"Analyze");check(8,"Analysis report",output.contains("metrics for step 8 (.kv)"),"checkpoint named .kv was analyzed as a passing sample instead of refused");details.put("analysis_output",output);trace.add("analyze_checkpoint_named_kv");
            }
            default->throw new IllegalArgumentException("unknown repro");
        }
    }
    void clickBound(String wid)throws Exception {
        Watch w=watches.get(wid);edt(()->{AbstractButton b=(AbstractButton)w.component;w.assertion(b.isEnabled(),"required action disabled in its prepared state");if(b.isEnabled())b.doClick(0);return null;});trace.add(w.def.name().replace(' ','_'));settleSieve();
    }
    void sieveControls()throws Exception {
        SieveWorkbench v=desktop.sieve();waitFuture(edt(v::open));
        if(family.equals("t4_graph")){
            CompletableFuture<?> job=edt(()->{var f=v.validate(true);Watch w=watches.get("t4_button_cancel_work");AbstractButton b=(AbstractButton)w.component;w.assertion(b.isEnabled(),"Cancel work disabled during impact task");if(b.isEnabled())b.doClick(0);return f;});
            try{waitFuture(job);}catch(ExecutionException|CancellationException expected){}trace.add("impact");trace.add("cancel_work");
            check("t4_button_cancel_work",edt(v::output).contains("CANCEL")||!edt(v::output).isBlank(),"cancelled impact lacks feedback");return;
        }
        String rules="{\"version\":1,\"constraints\":[{\"id\":\"guarded\",\"polarity\":\"forbid\",\"pairs\":[{\"A\":\"latency=1;\",\"B\":\"mode=a;\"}],\"condition\":{\"all\":[{\"sheet\":\"A\",\"present\":true},{\"sheet\":\"B\",\"present\":true}]}}]}";
        waitFuture(edt(()->v.applyJson(rules)));edt(()->{v.selectRule(0);return null;});click(4,"Duplicate");clickBound("t4_button_up_2");clickBound("t4_button_down_2");
        edt(()->{v.selectRule(0);for(Component c:all(scope(4)))if(c instanceof JTabbedPane t&&t.indexOfTab("Only when")>=0)t.setSelectedIndex(t.indexOfTab("Only when"));return null;});
        Watch treeWatch=watches.get("t4_tree_parts_of_the_condition");JTree tree=(JTree)treeWatch.component;
        edt(()->{for(int i=0;i<tree.getRowCount();i++)tree.expandRow(i);tree.setSelectionRow(0);return null;});
        clickBound("t4_button_condition");
        for(String wid:List.of("t4_button_all_of","t4_button_any_of","t4_button_not")){edt(()->{tree.setSelectionRow(0);return null;});clickBound(wid);}
        // Restore a two-child group, then move a selected child in each direction.
        click(4,"Discard draft");edt(()->{v.selectRule(0);for(int i=0;i<tree.getRowCount();i++)tree.expandRow(i);return null;});
        edt(()->{tree.setSelectionRow(1);return null;});clickBound("t4_button_down_3");
        edt(()->{for(int i=0;i<tree.getRowCount();i++)tree.expandRow(i);tree.setSelectionRow(2);return null;});clickBound("t4_button_up_3");
        clickBound("t4_button_delete_2");
        if(is("resolution","apply")){click(4,"Apply rule");trace.add("apply_guard");}
        else {click(4,"Discard draft");trace.add("discard_guard");}
        if(is("resolution","undo")){edt(()->{v.undo();v.redo();return null;});trace.add("undo");trace.add("redo");}
        waitFuture(edt(v::save));
    }
}
