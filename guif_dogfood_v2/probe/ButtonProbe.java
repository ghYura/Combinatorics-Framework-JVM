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
import org.ghyura.cf.generation.RenderedSource;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.*;
import javax.swing.text.JTextComponent;

/** Headless test-only package peer. All component access is confined to the EDT. */
public final class ButtonProbe {
    static final Path OUT=Path.of("target/guif-dogfood-v2").toAbsolutePath();
    static final AtomicLong EVENT_NUMBER=new AtomicLong();
    static final Map<String,Integer> CODES=new LinkedHashMap<>();
    static {try(var in=ButtonProbe.class.getResourceAsStream("/codes.tsv")){if(in!=null)for(String line:new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\n")){String[] a=line.split("\t");CODES.put(a[0],Integer.valueOf(a[1]));}}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static final class Watch {
        final String id,tab,path,text,type,hint; AbstractButton b; Component top;
        final List<Map<String,Object>> samples=new ArrayList<>();
        final List<String> failures=new ArrayList<>();
        final List<Map<String,Object>> events=new ArrayList<>();
        int actions,changes,assertions;
        /** A broken must: ready but not pressable, a disabled button's action, a press that throws or fires nothing, an event off the EDT. */
        boolean hard;
        Watch(String id,String tab,String path,AbstractButton b){this.id=id;this.tab=tab;this.path=path;this.b=b;
            text=Objects.toString(b.getText(),"");type=b.getClass().getSimpleName();hint=Objects.toString(Hints.own(b),Objects.toString(b.getToolTipText(),""));
            attach(b);}
        void attach(AbstractButton button){b=button;top=root(button);button.addActionListener(e->{actions++;record("action");});button.addPropertyChangeListener(e->{if(Set.of("enabled","visible","text").contains(e.getPropertyName())){changes++;record(e.getPropertyName());}});button.addItemListener(e->record("selected"));button.getModel().addChangeListener(e->record("model"));}
        void record(String kind){if(!SwingUtilities.isEventDispatchThread()){failures.add("State/action event delivered outside EDT: "+kind);hard=true;}events.add(new LinkedHashMap<>(Map.of("seq",EVENT_NUMBER.incrementAndGet(),"kind",kind,"enabled",b.isEnabled(),"selected",b.isSelected())));}
        /** Still in the window it was found in: an editor that swaps its body out takes its buttons with it. */
        boolean attached(){return root(b)==top;}
        Map<String,Object> data(){Map<String,Object> d=new LinkedHashMap<>();d.put("id",id);d.put("tab",tab);d.put("path",path);d.put("text",text);d.put("type",type);d.put("hint",hint);d.put("actions",actions);d.put("changes",changes);d.put("assertions",assertions);d.put("hard",hard);d.put("failures",failures);d.put("samples",samples);d.put("events",events);return d;}
    }
    static Component root(Component c){while(c.getParent()!=null)c=c.getParent();return c;}
    /** Shown whenever its own tab is: a tab page is hidden only while another tab is chosen, which reveal() undoes. */
    static boolean shown(Component c){for(Component p=c;p!=null;p=p.getParent()){if(p.getParent() instanceof JTabbedPane)continue;if(!p.isVisible())return false;}return true;}
    static boolean pressable(Watch w){return w.b.isEnabled()&&shown(w.b)&&w.attached();}
    /** Each semantic element has independent model/property listeners, even when it is not a button. */
    final class ElementWatch {
        final String id,tab,path,type,name;final Component c;final List<Map<String,Object>> events=new ArrayList<>();final List<String> failures=new ArrayList<>();int omitted;
        ElementWatch(String id,String tab,String path,Component c){this.id=id;this.tab=tab;this.path=path;this.c=c;type=c.getClass().getSimpleName();name=accessible(c);
            c.addPropertyChangeListener(e->{if(Set.of("enabled","visible","text","model","value","selectedItem","editable","ancestor").contains(e.getPropertyName()))record("property:"+e.getPropertyName());});
            c.addHierarchyListener(e->record("hierarchy"));
            if(c instanceof JTextComponent t){t.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){record("document:insert");}public void removeUpdate(DocumentEvent e){record("document:remove");}public void changedUpdate(DocumentEvent e){record("document:change");}});t.addCaretListener(e->record("caret"));}
            if(c instanceof JComboBox<?> b){b.addActionListener(e->record("choice"));b.addItemListener(e->record("item"));}
            if(c instanceof JSpinner s)s.addChangeListener(e->record("value"));
            if(c instanceof JTabbedPane t)t.addChangeListener(e->record("tab"));
            if(c instanceof JList<?> l){l.addListSelectionListener(e->record("selection"));listenList(l.getModel());l.addPropertyChangeListener("model",e->listenList(l.getModel()));}
            if(c instanceof JTable t){t.getSelectionModel().addListSelectionListener(e->record("row-selection"));t.getColumnModel().getSelectionModel().addListSelectionListener(e->record("column-selection"));t.getModel().addTableModelListener(e->record("rows"));t.addPropertyChangeListener("model",e->t.getModel().addTableModelListener(x->record("rows")));}
            if(c instanceof JTree t){t.addTreeSelectionListener(e->record("tree-selection"));listenTree(t.getModel());t.addPropertyChangeListener("model",e->listenTree(t.getModel()));}
            record("attached");
        }
        void listenList(ListModel<?> m){m.addListDataListener(new ListDataListener(){public void intervalAdded(ListDataEvent e){record("list:add");}public void intervalRemoved(ListDataEvent e){record("list:remove");}public void contentsChanged(ListDataEvent e){record("list:change");}});}
        void listenTree(javax.swing.tree.TreeModel m){m.addTreeModelListener(new TreeModelListener(){public void treeNodesChanged(TreeModelEvent e){record("tree:change");}public void treeNodesInserted(TreeModelEvent e){record("tree:insert");}public void treeNodesRemoved(TreeModelEvent e){record("tree:remove");}public void treeStructureChanged(TreeModelEvent e){record("tree:structure");}});}
        void record(String kind){
            if(!SwingUtilities.isEventDispatchThread()){failures.add("Listener outside EDT: "+kind);return;}
            if(events.size()>=512){omitted++;return;}
            String value="";int count=-1,selected=-1;
            if(c instanceof JTextComponent t)value=t.getText();
            else if(c instanceof JLabel l)value=Objects.toString(l.getText(),"");
            else if(c instanceof JComboBox<?> b)value=Objects.toString(b.getSelectedItem(),"");
            else if(c instanceof JSpinner s)value=Objects.toString(s.getValue(),"");
            else if(c instanceof JTable t){count=t.getRowCount();selected=t.getSelectedRow();}
            else if(c instanceof JList<?> l){count=l.getModel().getSize();selected=l.getSelectedIndex();}
            else if(c instanceof JTree t){count=t.getRowCount();selected=t.getLeadSelectionRow();}
            else if(c instanceof JTabbedPane t){count=t.getTabCount();selected=t.getSelectedIndex();}
            events.add(new LinkedHashMap<>(Map.of("seq",EVENT_NUMBER.incrementAndGet(),"event",state,"kind",kind,"enabled",c.isEnabled(),"visible",visible(c),"value",value.substring(0,Math.min(160,value.length())),"rows",count,"selected",selected)));
        }
        Map<String,Object> data(){return Map.of("id",id,"tab",tab,"path",path,"type",type,"name",name,"events",events,"omitted_events",omitted,"failures",failures);}
    }
    final String family; final Map<String,String> options;
    final Map<String,Watch> watches=new LinkedHashMap<>();
    final IdentityHashMap<AbstractButton,Watch> bound=new IdentityHashMap<>();
    final Map<String,ElementWatch> elements=new LinkedHashMap<>();
    final Set<Component> elementBound=Collections.newSetFromMap(new IdentityHashMap<>());
    final List<Map<String,Object>> trace=new ArrayList<>();
    final List<String> notes=new ArrayList<>();
    final long began=System.nanoTime();
    Desktop desktop; Session session; Path dir,scenario; String state="initial"; int infrastructure;
    ButtonProbe(String family,Map<String,String> options){this.family=family;this.options=new LinkedHashMap<>(options);}
    public static ButtonProbe execute(String family,Map<String,String> options){ButtonProbe p=new ButtonProbe(family,options);
        try{p.prepare();p.drive();}catch(Throwable e){p.error(e);}finally{p.finish();}return p;}
    static <T>T edt(Callable<T> call)throws Exception{if(SwingUtilities.isEventDispatchThread())return call.call();FutureTask<T> t=new FutureTask<>(call);SwingUtilities.invokeLater(t);return t.get(20,TimeUnit.SECONDS);}
    static void waitFor(Callable<Boolean> predicate)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(!predicate.call()){if(System.nanoTime()>end)throw new TimeoutException("state did not settle");Thread.sleep(5);}}
    static void await(CompletableFuture<?> f)throws Exception{if(f!=null)f.get(30,TimeUnit.SECONDS);}
    static List<Component> all(Component c){List<Component> r=new ArrayList<>();r.add(c);if(c instanceof Container k)for(Component x:k.getComponents())r.addAll(all(x));return r;}
    static String accessible(Component c){return c.getAccessibleContext()==null?"":Objects.toString(c.getAccessibleContext().getAccessibleName(),"");}
    static String slug(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");}
    static boolean visible(Component c){for(Component p=c;p!=null;p=p.getParent())if(!p.isVisible())return false;return true;}
    static String path(Component c,Component scope){List<String> r=new ArrayList<>();for(Component p=c.getParent();p!=null&&p!=scope;p=p.getParent()){
        if(p instanceof JComponent j&&j.getBorder() instanceof TitledBorder b)r.add(b.getTitle());
        if(p.getParent() instanceof JTabbedPane t){int n=t.indexOfComponent(p);if(n>=0)r.add(t.getTitleAt(n));}}
        Collections.reverse(r);return String.join(" / ",r);}
    void bind(Component root,String tab){Map<String,Integer> counts=new LinkedHashMap<>();for(Component c:all(root))if(c instanceof AbstractButton b&&b.getText()!=null&&!b.getText().isBlank()){
        String p=path(b,root),stem=tab+"_"+slug(p+"_"+b.getText());int n=counts.merge(stem,1,Integer::sum);String id=stem+"_"+n;
        if(!bound.containsKey(b)){Watch w=watches.get(id);if(w==null){w=new Watch(id,tab,p,b);watches.put(id,w);}else w.attach(b);bound.put(b,w);}}}
    void bindElements(Component root,String tab){Map<String,Integer> counts=new HashMap<>();for(Component c:all(root)){
        if(!(c instanceof JTextComponent||c instanceof JComboBox||c instanceof JSpinner||c instanceof JTable||c instanceof JList||c instanceof JTree||c instanceof JTabbedPane||c instanceof JLabel))continue;
        String p=path(c,root),stem="element_"+tab+"_"+slug(p+"_"+c.getClass().getSimpleName()+"_"+accessible(c));int n=counts.merge(stem,1,Integer::sum);
        if(elementBound.add(c)){String id=stem+"_"+n;if(elements.containsKey(id))id+="_instance"+elements.keySet().stream().filter(s->s.startsWith(stem+"_"+n)).count();elements.put(id,new ElementWatch(id,tab,p,c));}
    }}
    void bindAll(){for(int i=0;i<desktop.tabs().getTabCount();i++){String tab="t"+String.format("%02d",i+1);bind(desktop.tabs().getComponentAt(i),tab);bindElements(desktop.tabs().getComponentAt(i),tab);}
        bindElements(desktop.tabs(),"shell");
        if(!bound.containsKey(desktop.hintsButton())){Watch w=new Watch("shell_hints","shell","",desktop.hintsButton());watches.put(w.id,w);bound.put(w.b,w);}}
    Component scope(int tab){return desktop.tabs().getComponentAt(tab-1);}
    <T extends Component>T find(int tab,Class<T> type,String label){return find(scope(tab),type,label,0);}
    static <T extends Component>T find(Component root,Class<T> type,String label,int occurrence){int n=0;for(Component c:all(root))if(type.isInstance(c)&&(label.equals(accessible(c))||label.equals(c.getName())||c instanceof AbstractButton b&&(label.equals(b.getText())||Set.of("Back","Next").contains(label)&&b.getText().startsWith(label+":")))){if(n++==occurrence)return type.cast(c);}throw new IllegalArgumentException("missing "+type.getSimpleName()+" "+label+" #"+occurrence);}
    AbstractButton button(int tab,String label){return find(tab,AbstractButton.class,label);}
    void sample(String event)throws Exception{edt(()->{state=event;bindAll();for(Watch w:watches.values())w.samples.add(new LinkedHashMap<>(Map.of("event",event,"enabled",w.b.isEnabled(),"visible",visible(w.b),"selected",w.b.isSelected(),"actions",w.actions)));return null;});}
    /** Expected is whether a user can press it: enabled, shown and still in its window. Ready but not pressable is hard. */
    void expect(AbstractButton b,boolean expected,String reason){Watch w=bound.get(b);if(w==null)throw new IllegalStateException("unbound button");w.assertions++;boolean can=pressable(w),ok=can==expected;
        Map<String,Object> s=new LinkedHashMap<>(Map.of("event",state,"enabled",b.isEnabled(),"pressable",can,"expected",expected,"visible",visible(b),"reason",reason,"ok",ok));w.samples.add(s);
        if(!ok){w.failures.add(state+": "+reason+"; expected="+expected+", actual="+can+(b.isEnabled()==can?"":" (enabled, but not shown)"));if(expected)w.hard=true;}
        if(!b.isEnabled()){int before=w.actions;b.doClick(0);if(w.actions!=before){w.failures.add(state+": disabled button emitted an action");w.hard=true;}}}
    void expect(int tab,String label,boolean expected,String reason){expect(button(tab,label),expected,reason);}
    void click(int tab,String label)throws Exception{edt(()->{AbstractButton b=button(tab,label);event(label,tab,b.isEnabled());b.doClick(0);return null;});}
    Path write(String name,String contents)throws Exception{Path p=dir.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,contents);return p;}
    Session session(String... settings){List<ConfigResolver.Override> o=new ArrayList<>();for(String s:settings){int i=s.indexOf('=');o.add(new ConfigResolver.Override(s.substring(0,i),s.substring(i+1),"guif-v2"));}return Session.resolve(null,Map.of(),o);}
    String option(String key,String fallback){return options.getOrDefault(key,fallback);}
    boolean is(String key,String value){return value.equals(options.get(key));}
    void prepare()throws Exception{
        if(!GraphicsEnvironment.isHeadless())throw new IllegalStateException("headless required");Files.createDirectories(OUT.resolve("fixtures"));dir=Files.createTempDirectory(OUT.resolve("fixtures"),family+"-");
        scenario=write("small.json","{\"sheets\":{\"FW_Seq\":[[\"A\",\"FW_Combi(1)\"],[\"B\",\"FW_Combi(1)\"]],\"A\":[[\"latency=1;\"],[\"latency=2;\"]],\"B\":[[\"mode=a;\"],[\"mode=b;\"]]}}");
        session=session("cf.execution.sut=inline-metrics","cf.execution.workers=1","cf.keys.encoding=SIGNED_BYTE","cf.plan.autoPromoteSingleVerb=false","cf.pipeline.ordinal.window=1","cf.analysis.goals=latency:min","cf.analysis.discover=false","cf.campaign.iterations=2","cf.campaign.stopWhenStable=99","cf.run.id=nested-v2");
        edt(()->{FlatDesktopLauncher.installLookAndFeel();desktop=new Desktop(session,null,FileTrail.inMemory(),true);bindAll();return null;});sample("initial");
    }
    void drive()throws Exception {
        switch(family){
            case "inventory" -> {}
            case "library" -> library();
            case "authoring" -> authoring();
            case "scenario_runner" -> scenarioRunner();
            case "candidates" -> candidates();
            case "formatting" -> formatting();
            case "run","campaign" -> execution();
            case "analysis" -> analysis();
            case "scorer" -> scorer();
            case "wizard" -> wizard();
            case "sieve_graph","sieve_data","sieve_rules","sieve_formula" -> sieve();
            case "search" -> search();
            case "dialog_buttons" -> dialogButtons();
            default -> {if(family.startsWith("sweep_"))sweep();else throw new IllegalArgumentException(family);}
        }
        edt(()->{expect(desktop.hintsButton(),true,"Hints remain available independently of scenario state");desktop.hintsButton().doClick(0);desktop.hintsButton().doClick(0);return null;});
    }
    static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    void tabs(int tab)throws Exception{edt(()->{desktop.tabs().setSelectedIndex(tab-1);return null;});}
    void subtab(int tab,String title)throws Exception{edt(()->{for(Component c:all(scope(tab)))if(c instanceof JTabbedPane t&&t.indexOfTab(title)>=0){t.setSelectedIndex(t.indexOfTab(title));return null;}throw new IllegalArgumentException(title);});}
    void readyScenario()throws Exception{edt(()->{desktop.scenario().setScenario(scenario);return null;});}
    void always(int tab,String... labels){for(String s:labels)expect(tab,s,true,"This action can be offered in the current state");}
    /** The Use beside a file box offers a newer remembered file; the in-memory trail of a fresh desktop remembers none. */
    void noSuggestion(int tab){expect(tab,"Use",false,"No newer remembered file than the box holds is offered");}
    void awaitOutcome(CompletableFuture<?> f)throws Exception{try{await(f);}catch(ExecutionException|CancellationException e){notes.add("Expected operation outcome: "+e.getClass().getSimpleName());}}
    void pending(Object owner)throws Exception{List<CompletableFuture<?>> fs=edt(()->{List<CompletableFuture<?>> r=new ArrayList<>();for(var f:owner.getClass().getDeclaredFields())if(CompletableFuture.class.isAssignableFrom(f.getType())){f.setAccessible(true);if(f.get(owner) instanceof CompletableFuture<?> p)r.add(p);}return r;});for(var f:fs)awaitOutcome(f);}
    void event(String action,int tab,boolean enabled){Map<String,Object> e=new LinkedHashMap<>(Map.of("seq",EVENT_NUMBER.incrementAndGet(),"action",action,"tab",tab,"before",state,"enabled",enabled));if(tab==6||tab==7)e.put("controller_state",executionState(tab==7));trace.add(e);}
    void library()throws Exception{
        tabs(1);LibraryView v=desktop.library();
        edt(()->{always(1,"Open a library...","Auto-search…","Open the example library","Use Python","Runner settings (step 3)");libraryState(false);return null;});
        Path root=Files.createDirectories(dir.resolve("library/intake")).getParent();
        if(!is("fixture","empty")){Files.copy(scenario,root.resolve("small.json"));Files.writeString(root.resolve("intake/verified_examples.json"),"[{\"id\":\"small\",\"title\":\"Small\",\"spec\":\"small.json\",\"candidates\":\"4\",\"pass\":\"4\"}]");}
        await(edt(()->v.open(root)));sample("library loaded");
        boolean selected=!is("fixture","empty")&&!is("selection","none");
        final boolean initiallySelected=selected;edt(()->{if(initiallySelected){v.selectSuite(0);v.selectScenario(0);}else ((JTable)field(v,"table")).clearSelection();return null;});
        if(is("sudden","clear")){edt(()->{event("FW_Optional clear selection",1,true);((JTable)field(v,"table")).clearSelection();return null;});selected=false;}
        sample("selection established");final boolean usable=selected;
        edt(()->{libraryState(usable);return null;});
        if(usable&&is("action","check")){
            // the count runs off the event thread: in the turn that starts it, a second count has nothing to start
            edt(()->{AbstractButton b=button(1,"Check the candidate count");event("Check the candidate count",1,b.isEnabled());b.doClick(0);expect(b,false,"A second count cannot start while one runs");return null;});
            waitFor(()->edt(()->!(Boolean)field(v,"busy")));
        }else click(1,is("action","check")?"Check the candidate count":"Open in the Scenario tab");
        sample("after selection action");
        edt(()->{((JTable)field(v,"table")).clearSelection();libraryState(false);return null;});
        click(1,"Runner settings (step 3)");
    }
    void libraryState(boolean selected){expect(1,"Open in the Scenario tab",selected,"Opening a selected example requires a selected scenario");expect(1,"Check the candidate count",selected,"Counting the selected example requires a selected scenario");}
    void authoring()throws Exception{
        tabs(2);AuthoringView v=desktop.authoring();
        click(2,"New");sample("new document");
        edt(()->{always(2,"New","Open...","Auto-search…","Save","Save as...","Validate","Add sheet","Load from the document","Apply to the document","Add verdict");expect(2,"Remove verdict",false,"Removing a verdict needs a selected verdict row");graphState(v,"A new document has no dependency graph yet");return null;});
        await(edt(()->v.open(scenario)));sample("document opened");
        edt(()->{v.selectSheet(1);JTable t=find(2,JTable.class,"Cells of sheet A");if(is("selection","none"))t.clearSelection();else{v.selectCell(0,0);find(2,JList.class,"Cell templates").setSelectedIndex(0);}return null;});
        sample("cell selection");
        edt(()->{boolean row=!is("selection","none");always(2,"Rename","Remove","Insert row");expect(2,"Remove row",row,"Deleting a row requires a selected row");expect(2,"Insert into the selected cell",row,"Template insertion requires a selected cell and template");return null;});
        if(!is("selection","none")){click(2,"Insert row");click(2,"Remove row");}
        click(2,"Add verdict");edt(()->{JTable t=find(2,JTable.class,"FW_CUSTOM_VAR: verdict codes and their names");t.setRowSelectionInterval(0,0);expect(2,"Remove verdict",true,"Selected verdict can be removed");return null;});click(2,"Remove verdict");
        sample("verdict removed");edt(()->{expect(2,"Remove verdict",false,"No verdict remains selected after removal");return null;});
        if(is("sudden","new")){event("FW_Optional New",2,true);click(2,"New");}
        Path target=dir.resolve("saved."+option("format","json"));await(edt(()->v.save(target)));awaitOutcome(edt(v::validate));
        // the real Save DOT button acts only on a drawn graph (FW_Optional New leaves none): saving goes through the same guard
        click(2,"Load from the document");click(2,"Apply to the document");edt(()->{v.showGraph();if(v.graphView().graph()!=null)v.saveGraph(dir.resolve("graph.dot"));return null;});
        sample("saved and validated");edt(()->{always(2,"Save","Save as...","Validate");graphState(v,"The dependency graph follows the last validation");return null;});
    }
    void graphState(AuthoringView v,String reason){boolean graph=v.graphView().graph()!=null;expect(2,"Copy DOT",graph,reason+": copying needs a drawn graph");expect(2,"Save DOT or JSON...",graph,reason+": saving needs a drawn graph");}
    void scenarioRunner()throws Exception{
        tabs(3);edt(()->{always(3,"Browse...","Auto-search…","Use the example","Apply runner settings");expect(3,"Validate and plan",false,"Validation needs a scenario file in the box");noSuggestion(3);return null;});
        for(String runner:List.of(option("runner","python"),"generated-java","python")){
            edt(()->{find(3,JComboBox.class,"Runner").setSelectedItem(runner);return null;});sample("runner "+runner);
            edt(()->{for(Watch w:watches.values())if(w.tab.equals("t03")&&w.path.startsWith("Runner settings")){
                boolean gated=w.hint.toLowerCase(Locale.ROOT).contains("python")&&!w.text.equals("Apply runner settings");
                expect(w.b,!gated||runner.equals("python"),gated?"Python chooser buttons follow the selected runner":"Runner/add-on actions remain available");
            }return null;});
        }
        edt(()->{find(3,JComboBox.class,"Runner").setSelectedItem("inline-metrics");return null;});click(3,"Apply runner settings");
        Path chosen=is("input","invalid")?write("broken.json","{bad"):scenario;
        edt(()->{desktop.scenario().setScenario(chosen);return null;});
        edt(()->{button(3,"Validate and plan").doClick(0);expect(3,"Validate and plan",false,"Validation cannot be re-entered while its worker is pending");return null;});
        waitFor(()->edt(()->button(3,"Validate and plan").isEnabled()));sample("validation completed");edt(()->{expect(3,"Validate and plan",true,"Validation becomes available after success or error");return null;});
        click(3,"Use the example");
    }
    void candidateState(boolean ready,boolean busy,boolean more){expect(5,"Preview",ready&&!busy,"Preview needs a scenario and no active preview");expect(5,"Next page",ready&&!busy&&more,"Next page needs another page of the current scenario");expect(5,"Cancel",busy,"Cancellation needs an outstanding preview");}
    void candidates()throws Exception{
        tabs(5);CandidatesView v=desktop.candidates();
        edt(()->{candidateState(false,false,false);always(5,"Executable only");return null;});readyScenario();
        edt(()->{v.settings("100",is("page","all")?20:1,true);return null;});sample("scenario chosen");
        CompletableFuture<?> f=edt(()->{button(5,"Preview").doClick(0);candidateState(true,true,false);return (CompletableFuture<?>)field(v,"pending");});
        if(is("sudden","cancel")){event("FW_Optional Cancel",5,true);click(5,"Cancel");}
        awaitOutcome(f);sample("preview callback");
        // A cancelled generation can finish a small page before cancellation wins; re-preview creates a deterministic page.
        if(is("sudden","cancel"))await(edt(v::preview));
        boolean more=!is("page","all");
        edt(()->{candidateState(true,false,more);expect(5,"Auto-format",false,"Selecting a candidate is required before formatting");expect(5,"Original",false,"Unformatted source has nothing to restore");return null;});
        edt(()->{v.select(0);return null;});sample("candidate selected");
        edt(()->{expect(5,"Auto-format",true,"A complete nonempty selected source can be formatted");return null;});
        if(is("sudden","clear")){edt(()->{event("FW_Optional clear selection",5,true);for(Component c:all(scope(5)))if(c instanceof JTable t&&accessible(t).startsWith("Candidates"))t.clearSelection();return null;});}
        if(is("change","replace")){
            Path alternate=write("other.json","{\"sheets\":{\"A\":[[\"other\"]]}}");edt(()->{desktop.scenario().setScenario(alternate);return null;});sample("scenario changed after preview");
            edt(()->{expect(5,"Next page",false,"Pagination from the previous scenario must be invalidated when step 3 changes");return null;});
            // what a press does then: the status names the file it pages
            if(more){click(5,"Next page");pending(v);notes.add("After step 3 changed to "+alternate.getFileName()+", Next page showed: "+edt(v::status));sample("next page after the change");}
        }else if(more){click(5,"Next page");pending(v);sample("next page completed");}
        await(edt(v::close));sample("preview closed");edt(()->{candidateState(false,false,false);return null;});
    }
    void formatting()throws Exception{
        tabs(5);CandidateCodeView v=edt(()->(CandidateCodeView)field(desktop.candidates(),"source"));
        String language=option("language","JSON");String code=switch(language){case "JAVA"->"class X{int n=1;}";case "XML"->"<root><item a=\"1\"/></root>";case "PYTHON"->"value= 1\n";case "TEXT"->"unrecognized content";default->"{\"a\":1,\"b\":[2,3]}";};
        boolean truncated=is("input","truncated"),blank=is("input","blank");
        edt(()->{v.language(CodeFormatter.Language.valueOf(language));v.show(new RenderedSource(blank?"":code,List.of(),truncated));return null;});sample("source selected");
        edt(()->{expect(5,"Auto-format",!blank&&!truncated,"Formatting requires complete nonempty source");expect(5,"Original",false,"Original is disabled before formatting starts");return null;});
        if(blank||truncated)return;
        CompletableFuture<?> f=edt(()->{button(5,"Auto-format").doClick(0);expect(5,"Auto-format",false,"Only one formatting request may run at a time");expect(5,"Original",true,"Restore is available during formatting");return (CompletableFuture<?>)field(v,"pending");});
        if(is("sudden","original")){event("FW_Optional Original",5,true);click(5,"Original");}
        if(is("sudden","select"))edt(()->{event("FW_Optional source selection",5,true);v.show(new RenderedSource("{\"changed\":true}",List.of(),false));return null;});
        awaitOutcome(f);sample("formatter callback");
        edt(()->{expect(5,"Auto-format",true,"Formatter completion or rejection restores availability");boolean changed=!v.source().equals(code);boolean restore=changed&&!options.containsKey("sudden");expect(5,"Original",restore,"Original follows whether this current source has a formatted representation");return null;});
        click(5,"Original");sample("restored");edt(()->{expect(5,"Original",false,"Restore consumes the formatted state");return null;});
        await(edt(v::close));sample("formatter closed");edt(()->{expect(5,"Auto-format",false,"Closed preview rejects new formatting");expect(5,"Original",false,"Closed preview rejects restoration");return null;});
    }
    void largeScenario()throws Exception{List<List<String>> a=new ArrayList<>(),b=new ArrayList<>();for(int n=0;n<16;n++){a.add(List.of("latency="+(n+1)+";"));b.add(List.of("mode=m"+n+";"));}Map<String,Object>s=new LinkedHashMap<>();s.put("FW_Seq",List.of(List.of("A","FW_Combi(1)"),List.of("B","FW_Combi(1)")));s.put("A",a);s.put("B",b);Files.writeString(scenario,json(Map.of("sheets",s)));}
    String executionState(boolean campaign){return Objects.toString(campaign?desktop.campaign().controller().view().state():desktop.run().controller().view().state(),"IDLE");}
    /** A run goes until its state is terminal: DRAINING, VALIDATING and READY go too, as the run tab reads them. */
    boolean going(boolean campaign){if(campaign)return desktop.campaign().controller().view().going();org.ghyura.cf.campaign.RunState s=desktop.run().controller().view().state();return s!=null&&!s.terminal();}
    void executionButtons(boolean campaign,boolean ready){int tab=campaign?7:6;String s="";boolean live=false;String start=campaign?"Run campaign":"Run";
        // a run moves on its own threads: refresh between two equal readings, so the buttons show the state expected from
        for(int i=0;i<100;i++){String before=executionState(campaign);boolean was=going(campaign);refreshNow(campaign);s=executionState(campaign);live=going(campaign);if(s.equals(before)&&live==was)break;}
        expect(tab,start,ready&&!live,"Start requires a selected scenario and no active execution");
        expect(tab,"Pause",live&&s.equals("RUNNING"),"Pause is available only while execution is running");
        expect(tab,"Resume",live&&(s.equals("PAUSED")||s.equals("PAUSING")),"Resume is available only after a pause request");
        expect(tab,"Cancel",live,"Cancel is available until execution is terminal");
        if(!campaign)expect(6,"Export...",!live&&List.of("COMPLETED","CANCELLED","FAILED").contains(s),"Export requires a terminal run");
    }
    void refresh(boolean campaign)throws Exception{edt(()->{refreshNow(campaign);return null;});}
    void refreshNow(boolean campaign){if(campaign)desktop.campaign().refresh();else desktop.run().refresh();}
    void execution()throws Exception{
        boolean campaign=family.equals("campaign");int tab=campaign?7:6;tabs(tab);refresh(campaign);
        edt(()->{executionButtons(campaign,false);if(!campaign)always(6,"Use Python","Runner settings (step 3)");return null;});
        largeScenario();readyScenario();AtomicBoolean paused=new AtomicBoolean();
        int threshold=is("timing","later")?8:1;
        if(campaign)desktop.campaign().controller().observe((i,p)->{if(p.committed()>=threshold&&paused.compareAndSet(false,true))desktop.campaign().controller().pause();});
        else desktop.run().controller().observe(p->{if(p.committed()>=threshold&&paused.compareAndSet(false,true))desktop.run().controller().pause();});
        sample("scenario ready");refresh(campaign);edt(()->{executionButtons(campaign,true);return null;});
        click(tab,campaign?"Run campaign":"Run");waitFor(()->executionState(campaign).equals("PAUSED")||!going(campaign)&&paused.get());refresh(campaign);sample("progress-hook pause");
        edt(()->{executionButtons(campaign,true);return null;});
        String[] steps={option("first","resume"),option("second","pause"),option("third","cancel")};
        for(int n=0;n<steps.length;n++){
            if(n==1&&options.containsKey("sudden")){
                if(is("sudden","cancel")){event("FW_Optional Cancel",tab,true);click(tab,"Cancel");waitFor(()->!going(campaign));}
                else{event("FW_Optional refresh",tab,true);refresh(campaign);}
            }
            refresh(campaign);sample("before "+steps[n]+" #"+n);edt(()->{executionButtons(campaign,true);return null;});
            String label=Character.toUpperCase(steps[n].charAt(0))+steps[n].substring(1);click(tab,label);
            if(steps[n].equals("pause"))waitFor(()->!going(campaign)||executionState(campaign).equals("PAUSED"));
            if(steps[n].equals("cancel"))waitFor(()->!going(campaign));
        }
        if(going(campaign)){if(campaign)desktop.campaign().controller().resume();else desktop.run().controller().resume();}
        waitFor(()->!going(campaign));refresh(campaign);sample("terminal");edt(()->{executionButtons(campaign,true);return null;});
        for(String label:List.of("Pause","Resume","Cancel"))click(tab,label);
        if(!campaign){
            Path metrics=dir.resolve("run.kv");await(edt(()->desktop.run().export("metrics",metrics)));
            await(edt(()->{desktop.analysis().choose(metrics,null,null);return desktop.analysis().analyze();}));notes.add("Run metrics export analyzed in step 8: "+Files.size(metrics)+" bytes");
            click(6,"Runner settings (step 3)");
        }
    }
    void analysis()throws Exception{
        tabs(8);AnalysisView v=desktop.analysis();
        edt(()->{always(8,"Browse...","Auto-search…","Use the example");noSuggestion(8);expect(8,"Analyze",false,"Analysis requires a metrics file");return null;});
        Path kv=write("metrics.kv",is("input","invalid")?"not a metrics corpus":"candidate_id=a latency=2 FW_VAR=0\ncandidate_id=b latency=4 FW_VAR=0\n");
        for(String coverage:List.of("none",option("coverage","jacoco"),"none")){
            edt(()->{JComboBox<?> choice=find(8,JComboBox.class,"Code coverage (optional)");if(coverage.equals("none"))choice.setSelectedIndex(0);else choice.setSelectedItem(coverage);return null;});
            sample("coverage "+coverage);
            edt(()->{expect(find(scope(8),AbstractButton.class,"Browse...",1),!coverage.equals("none"),"Coverage chooser requires a selected coverage format");expect(find(scope(8),AbstractButton.class,"Auto-search…",1),!coverage.equals("none"),"Coverage search requires a selected coverage format");return null;});
        }
        edt(()->{v.choose(kv,null,null);expect(8,"Analyze",true,"A chosen metrics file can be analyzed");return null;});
        CompletableFuture<?> f=edt(()->{CompletableFuture<?> p=v.analyze();expect(8,"Analyze",false,"Analysis is unavailable while its worker runs");return p;});awaitOutcome(f);
        sample("analysis callback");edt(()->{expect(8,"Analyze",true,"Analysis recovers after success or failure");return null;});
        click(8,"Use the example");
    }
    void scorer()throws Exception{
        tabs(9);ScorerView v=desktop.scorer();
        edt(()->{always(9,"Browse...","Auto-search…","Load rules...");noSuggestion(9);expect(9,"Score",false,"Scoring requires a text file");expect(9,"Auto-plan",false,"Auto-plan requires a text file");expect(9,"Export CSV...",false,"Export needs scored lines");expect(9,"Optimization charts",false,"Charts need scoring results");return null;});
        Path text=write("text.txt","latency 10 success\nlatency 20 failure\nlatency 30 success\n");
        edt(()->{v.choose(text,is("rules","invalid")?"{bad":"{}");expect(9,"Score",true,"Chosen text may be scored");expect(9,"Auto-plan",true,"Chosen text may be planned");return null;});
        CompletableFuture<?> f=edt(()->{CompletableFuture<?> p=v.score();expect(9,"Score",false,"Scoring is unavailable while a score job runs");return p;});awaitOutcome(f);sample("score callback");
        boolean valid=!is("rules","invalid");edt(()->{expect(9,"Score",true,"Scoring can retry after callback");expect(9,"Export CSV...",valid,"Export needs a successful scoring report");expect(9,"Optimization charts",valid,"Charts need a successful scoring report");return null;});
        if(!valid){edt(()->{v.choose(text,"{}");return null;});await(edt(v::score));}
        await(edt(()->v.exportCsv(dir.resolve("scores.csv"))));edt(v::charts);
        awaitOutcome(edt(v::autoPlan));sample("planned and exported");
        // a later scoring that fails: Export and the charts would still offer the earlier report
        edt(()->{v.choose(text,"{bad");return null;});awaitOutcome(edt(v::score));sample("a later scoring failed");
        edt(()->{expect(9,"Export CSV...",false,"Export offers the report of the text and rules in the boxes, which failed to score");expect(9,"Optimization charts",false,"The charts show the report of the text and rules in the boxes, which failed to score");return null;});
        if(is("sudden","clear")){edt(()->{event("FW_Optional clear text path",9,true);find(9,JTextField.class,"Text file to score").setText("");return null;});sample("text cleared");edt(()->{expect(9,"Score",false,"Clearing the required path revokes Score");expect(9,"Auto-plan",false,"Clearing the required path revokes Auto-plan");return null;});}
    }
    void wizard()throws Exception{
        tabs(10);WizardView v=desktop.wizard();
        edt(()->{for(Watch w:watches.values())if(w.tab.equals("t10")&&w.path.contains("Situation"))expect(w.b,true,"Situation can be selected before any design exists");expect(10,"Back",false,"First wizard page has no predecessor");for(String s:List.of("Use it in step 3","Show its test cases (step 5)","Open it in step 2"))expect(10,s,false,"A handoff requires a saved scenario");return null;});
        GuidedScenario.Situation situation=GuidedScenario.Situation.valueOf(option("situation","WORKFLOW"));
        edt(()->{v.fill(situation);return null;});sample("example filled");
        edt(()->{always(10,"Add a factor");designReady(v);return null;});
        int count=edt(()->v.design().factors().size());
        if(count==0){click(10,"Add a factor");count=1;}
        final int size=count;int selected=is("selection","last")?count-1:0;
        edt(()->{v.select(selected);expect(10,"Remove the factor",true,"Selected factor may be removed");expect(10,"Move up",selected>0,"Moving up requires a predecessor");expect(10,"Move down",selected<size-1,"Moving down requires a successor");expect(10,"Remove the rule",false,"Rule removal needs an explicit selection");return null;});
        for(int page=0;page<edt(()->v.pages().getTabCount());page++){int at=page;edt(()->{v.pages().setSelectedIndex(at);expect(10,"Back",at>0,"Back follows wizard page position");for(Watch w:watches.values())if(w.tab.equals("t10")&&w.text.startsWith("Next"))expect(w.b,at<v.pages().getTabCount()-1,"Next follows wizard page position");return null;});}
        for(GuidedScenario.Use use:GuidedScenario.Use.values()){
            edt(()->{v.answer(use,2,0,2,true,true,false);return null;});sample("variation "+use);
            edt(()->{always(10,"Exactly one of them","A fixed number of them","Any number of them, within bounds","It may or may not happen: a sudden action");expect(10,"A value may appear more than once",use==GuidedScenario.Use.EXACTLY,"Repetition is available for fixed groups of at least two");boolean several=use!=GuidedScenario.Use.ONE;expect(10,"No: they form a group, and a+b is the same as b+a",several,"Ordering choice requires several values");expect(10,"Yes: they form a sequence, and a+b differs from b+a",several,"Ordering choice requires several values");return null;});
        }
        edt(()->{v.answer(GuidedScenario.Use.ONE,1,0,1,false,false,false);return null;});
        for(GuidedScenario.Selection choice:GuidedScenario.Selection.values()){
            edt(()->{v.choose(choice);return null;});sample("selection strategy "+choice);
            edt(()->{always(10,"Every combination","Every pair of values (pairwise)","Every triple of values","The strongest covering within a number of rows");expect(10,"Search harder for fewer test cases (set-cover)",choice!=GuidedScenario.Selection.ALL,"Set-cover only applies to covering selection");boolean sequence=!v.design().of(GuidedScenario.Role.SEQUENCE).isEmpty();expect(10,"Run every order of each sequence of actions with each kept combination",choice!=GuidedScenario.Selection.ALL&&sequence,"All-orders needs covering selection and a sequence factor");return null;});
        }
        edt(()->{v.choose(GuidedScenario.Selection.ALL);return null;});
        CompletableFuture<?> f=edt(()->{CompletableFuture<?> p=v.check();expect(10,"Check with the Framework",false,"Check cannot be re-entered during counting");expect(10,"Save the scenario...",false,"Save waits for current wizard work");return p;});awaitOutcome(f);
        await(edt(()->v.save(dir.resolve("wizard."+option("format","json")))));sample("wizard saved");
        edt(()->{designReady(v);always(10,"Use it in step 3","Show its test cases (step 5)","Open it in step 2");return null;});
        click(10,"Use it in step 3");click(10,"Show its test cases (step 5)");pending(desktop.candidates());click(10,"Open it in step 2");pending(desktop.authoring());
        click(10,"Add a check sheet where your test decides its verdict");click(10,"Run a bootstrap once, first, before all the test cases (FW_RunMeFirstOnce)");
    }
    /** Checking and saving need a design without problems, as the design's own list of problems says. */
    void designReady(WizardView v){boolean ready=v.design().problems().isEmpty();expect(10,"Check with the Framework",ready,"Checking needs a design without problems");expect(10,"Save the scenario...",ready,"Saving needs a design without problems");}
    static final String PAIR_RULES="{\"version\":1,\"constraints\":[{\"id\":\"pair1\",\"polarity\":\"forbid\",\"pairs\":[{\"A\":\"latency=1;\",\"B\":\"mode=a;\"}]}]}";
    static final String WHEN_RULES="{\"version\":1,\"params\":{\"A\":{\"latency=1;\":{\"cost\":1},\"latency=2;\":{\"cost\":2}}},\"constraints\":[{\"id\":\"cost\",\"polarity\":\"forbid\",\"sheets\":[\"A\",\"B\"],\"when\":\"A.cost > 1\"}]}";
    JTabbedPane sieveTabs(){return all(scope(4)).stream().filter(c->c instanceof JTabbedPane t&&t.indexOfTab("Sieve graph")>=0).map(JTabbedPane.class::cast).findFirst().orElseThrow();}
    AbstractButton scopedButton(Component root,String text){return find(root,AbstractButton.class,text,0);}
    void scopedClick(Component root,String text)throws Exception{edt(()->{AbstractButton b=scopedButton(root,text);event(text,4,b.isEnabled());b.doClick(0);return null;});}
    void sieve()throws Exception{
        tabs(4);SieveWorkbench v=desktop.sieve();
        if(family.equals("sieve_graph"))edt(()->{for(String s:List.of("Open step 3 scenario","Import constraints…","Auto-search…","Export JSON…","Save for scenario","Face 2 (Sieve) ↗","Undo","Redo","Validate","Check impact","Cancel work","Group selected values","Add pair at cell","Edit cell rule","New rule","Duplicate","Delete","Find on graph"))expect(4,s,false,"This action needs an opened scenario, history, active work or a selection");return null;});
        readyScenario();edt(()->{expect(4,"Open step 3 scenario",true,"A step 3 scenario can now be opened");return null;});
        CompletableFuture<?> opening=edt(()->{button(4,"Open step 3 scenario").doClick(0);for(Watch w:watches.values())if(w.tab.equals("t04"))expect(w.b,w.text.equals("Cancel work"),"Sieve work locks every other button until its callback");return (CompletableFuture<?>)field(v,"pending");});await(opening);sample("sieve opened");
        if(family.equals("sieve_graph"))sieveGraph(v);
        else if(family.equals("sieve_data"))sieveData(v);
        else if(family.equals("sieve_rules"))sieveRules(v);
        else sieveFormula(v);
    }
    void sieveGraph(SieveWorkbench v)throws Exception{
        edt(()->{always(4,"Open step 3 scenario","Import constraints…","Auto-search…","Export JSON…","Save for scenario","Face 2 (Sieve) ↗","Validate","Check impact","Fit","+","−","Arrange","New rule");expect(4,"Undo",false,"No edit history exists immediately after opening");expect(4,"Redo",false,"No undone operation exists");expect(4,"Cancel work",false,"No work remains after the callback");expect(4,"Group selected values",false,"Grouping needs at least two selected values");return null;});
        for(String action:List.of("Fit","+","−","Arrange"))click(4,action);
        edt(()->{v.pair(new SieveCanvas.Value("A","latency=1;"),new SieveCanvas.Value("B","mode=a;"));return null;});pending(v);sample("pair added");
        edt(()->{expect(4,"Undo",true,"An applied pair edit is undoable");expect(4,"Redo",false,"A new edit clears redo history");always(4,"Duplicate","Delete","Find on graph");return null;});
        for(String action:List.of(option("first","Undo"),option("second","Redo"))){
            click(4,action);sample("history "+action);
            edt(()->{boolean hasUndo=!((Deque<?>)field(v,"undo")).isEmpty(),hasRedo=!((Deque<?>)field(v,"redo")).isEmpty();expect(4,"Undo",hasUndo,"Undo reflects available edit history");expect(4,"Redo",hasRedo,"Redo reflects available undone history");return null;});
        }
        if(options.containsKey("sudden")){event("FW_Optional "+option("sudden",""),4,true);click(4,is("sudden","validate")?"Validate":"Undo");pending(v);}
        await(edt(()->v.applyJson(PAIR_RULES)));edt(()->{v.selectRule(0);return null;});sample("one rule selected");
        edt(()->{for(Watch w:watches.values())if(w.tab.equals("t04")&&w.path.isEmpty()&&(w.text.equals("↑")||w.text.equals("↓")))expect(w.b,false,"A sole rule has no preceding or following rule");return null;});
        click(4,"Duplicate");pending(v);sample("duplicate selected");click(4,"Find on graph");
        subtab(4,"Bond matrix");Component matrix=edt(()->sieveTabs().getComponentAt(1));
        edt(()->{JTable t=(JTable)field(v,"matrix");t.clearSelection();expect(4,"Add pair at cell",false,"A matrix pair requires a selected cell");expect(4,"Edit cell rule",false,"Editing requires a selected cell with a rule");if(t.getRowCount()>0&&t.getColumnCount()>1)t.changeSelection(0,1,false,false);return null;});sample("matrix cell selected");
        edt(()->{expect(4,"Add pair at cell",true,"Selected matrix value pair can form a rule");return null;});click(4,"Add pair at cell");pending(v);click(4,"Edit cell rule");
        click(4,"Discard draft");await(edt(v::save));sample("sieve saved");
        CompletableFuture<?> f=edt(()->{CompletableFuture<?> p=v.validate(true);expect(4,"Cancel work",true,"Impact counting exposes cancellation while pending");return p;});click(4,"Cancel work");awaitOutcome(f);
        sample("impact cancelled");edt(()->{expect(4,"Cancel work",false,"Cancellation is disabled after callback");expect(4,"Save for scenario",true,"Save recovers after cancellation");return null;});
    }
    void sieveData(SieveWorkbench v)throws Exception{
        subtab(4,"Parameters / orders");Component root=edt(()->sieveTabs().getComponentAt(2));sample("data editor active");
        edt(()->{for(String s:List.of("Add attribute","Add value","Apply data"))expect(scopedButton(root,s),true,"Opened scenario permits data editing");for(String s:List.of("Remove attribute","↑","↓","Remove"))expect(scopedButton(root,s),false,"Selection-dependent data action has no selection");expect(scopedButton(root,"Discard data draft"),false,"There is no draft to discard");expect(4,"Apply rule",false,"Rule editor is read-only while data editor owns the view");return null;});
        scopedClick(root,"Add attribute");sample("attribute added");
        edt(()->{JTable table=(JTable)field(field(v,"data"),"table");table.setRowSelectionInterval(0,0);expect(scopedButton(root,"Remove attribute"),true,"Selected attribute can be removed");expect(scopedButton(root,"Discard data draft"),true,"An added attribute creates a discardable draft");return null;});
        scopedClick(root,"Remove attribute");scopedClick(root,"Discard data draft");
        subtab(4,"Value orders");scopedClick(root,"Add value");scopedClick(root,"Add value");
        edt(()->{JList<?> list=find(root,JList.class,"Ranked values",0);list.setSelectedIndex(is("selection","last")?list.getModel().getSize()-1:0);int i=list.getSelectedIndex();expect(scopedButton(root,"↑"),i>0,"Moving a value up requires an earlier rank");expect(scopedButton(root,"↓"),i>=0&&i<list.getModel().getSize()-1,"Moving down requires a later rank");expect(scopedButton(root,"Remove"),true,"Selected ranked value can be removed");return null;});
        scopedClick(root,is("selection","last")?"↑":"↓");scopedClick(root,"Remove");
        if(is("sudden","undo")){event("FW_Optional Undo during data draft",4,true);click(4,"Undo");}
        if(is("resolution","apply")){scopedClick(root,"Apply data");pending(v);}else scopedClick(root,"Discard data draft");
        scopedClick(root,"Discard data draft");subtab(4,"Sieve graph");sample("data editor released");
        edt(()->{expect(4,"New rule",true,"Rule creation recovers after data draft resolution");return null;});
    }
    void sieveRules(SieveWorkbench v)throws Exception{
        await(edt(()->v.applyJson(PAIR_RULES)));edt(()->{v.selectRule(0);return null;});sample("rule selected");
        SieveRuleEditor editor=edt(()->(SieveRuleEditor)field(v,"editor"));Component root=editor.component();
        edt(()->{always(4,"Apply rule","Use chosen sheets","Add row");expect(4,"Discard draft",false,"An unchanged rule has no draft to discard");expect(4,"Remove row",false,"Removing pair rows requires selection");expect(4,"Adjacent",true,"A pair rule supports adjacency");expect(4,"Within",true,"A pair rule supports distance");return null;});
        click(4,"Add row");edt(()->{JTable table=(JTable)field(editor,"table");table.setRowSelectionInterval(table.getRowCount()-1,table.getRowCount()-1);expect(4,"Remove row",true,"Selected rule row can be removed");return null;});click(4,"Remove row");
        click(4,"Adjacent");click(4,"Within");click(4,"Use chosen sheets");click(4,"Discard draft");
        ConditionEditor condition=edt(()->(ConditionEditor)field(editor,"guard"));Component c=condition.component();
        edt(()->{for(String s:List.of("all of","any of","not","Delete","Up","Down"))expect(scopedButton(c,s),false,"Empty condition has no selected part");expect(scopedButton(c,"+ condition"),true,"An empty condition can gain its first test");expect(scopedButton(c,"Apply JSON"),true,"Condition JSON can be supplied directly");return null;});
        scopedClick(c,"+ condition");sample("condition created");edt(()->{condition.select(List.of());for(String s:List.of("all of","any of","not","Delete"))expect(scopedButton(c,s),true,"Selected condition supports structural edits");expect(scopedButton(c,"Up"),false,"A root condition has no earlier sibling");expect(scopedButton(c,"Down"),false,"A root condition has no later sibling");return null;});
        scopedClick(c,"all of");scopedClick(c,"+ condition");sample("condition group");
        edt(()->{condition.select(List.of(0));expect(scopedButton(c,"Up"),false,"First child cannot move up");expect(scopedButton(c,"Down"),true,"First of two children can move down");return null;});scopedClick(c,"Down");scopedClick(c,"Up");scopedClick(c,"any of");scopedClick(c,"not");scopedClick(c,"Delete");
        scopedClick(c,"Apply JSON");click(4,"Discard draft");
        edt(()->{find(4,JTextField.class,"Rule ID").setText("changed");return null;});sample("rule draft");
        if(is("sudden","switch")){event("FW_Optional editor switch",4,true);subtab(4,"Advanced JSON");}
        if(is("resolution","apply")){click(4,"Apply rule");pending(v);}else click(4,"Discard draft");
        click(4,"Discard draft");subtab(4,"Advanced JSON");sample("JSON owns editor");
        edt(()->{expect(4,"Apply rule",false,"Rule editing is disabled under Advanced JSON");expect(4,"Adjacent",false,"Gates are disabled under Advanced JSON");return null;});
        Component raw=edt(()->sieveTabs().getComponentAt(3));edt(()->{expect(scopedButton(raw,"Apply JSON"),true,"JSON application is allowed for validation even when unchanged");expect(scopedButton(raw,"Discard JSON draft"),false,"Unchanged JSON has no draft to discard");find(4,JTextArea.class,"Sieve execution JSON").append(" ");return null;});
        sample("JSON dirty");edt(()->{expect(scopedButton(raw,"Discard JSON draft"),true,"Edited JSON can be discarded");return null;});scopedClick(raw,"Discard JSON draft");subtab(4,"Sieve graph");
    }
    void sieveFormula(SieveWorkbench v)throws Exception{
        await(edt(()->v.applyJson(WHEN_RULES)));edt(()->{v.selectRule(0);return null;});sample("formula rule");
        SieveRuleEditor editor=edt(()->(SieveRuleEditor)field(v,"editor"));FormulaEditor f=edt(()->(FormulaEditor)field(editor,"formula"));Component root=f.component();
        edt(()->{expect(scopedButton(root,"Text"),true,"Text mode is available");expect(scopedButton(root,"Blocks"),true,"A supported formula can enter Blocks mode");return null;});
        if(is("mode","text")){scopedClick(root,"Text");edt(()->{f.setText("A.cost >= 1");return null;});}
        scopedClick(root,"Blocks");sample("formula blocks");
        edt(()->{f.select("");expect(scopedButton(root,"Delete"),true,"Selected formula root can be removed");expect(scopedButton(root,"Move…"),false,"Formula root cannot be moved inside itself");expect(scopedButton(root,"Move here"),false,"No block has been picked up");return null;});
        scopedClick(root,"Delete");sample("empty formula");
        edt(()->{for(Watch w:watches.values())if(w.b.getParent()!=null&&all(root).contains(w.b)&&!Set.of("Text","Blocks","Delete","Move…","Move here").contains(w.text))expect(w.b,w.text.equals("and / or")||w.text.equals("not")||w.text.equals("compare"),"An empty formula root accepts boolean blocks only");expect(scopedButton(root,"Delete"),false,"Empty slot has no block to delete");return null;});
        edt(()->{f.show("A.cost > 1",List.of("A"),s->List.of("cost"));f.toBlocks();f.select("a");return null;});sample("numeric operand");
        edt(()->{expect(scopedButton(root,"Move…"),true,"Selected non-root operand can be picked up");return null;});scopedClick(root,"Move…");scopedClick(root,"Text");scopedClick(root,"Blocks");click(4,"Discard draft");
        if(is("slot","operand")){edt(()->{f.toBlocks();f.select("b");f.delete();return null;});sample("empty numeric operand");edt(()->{for(String text:List.of("+ − ×","sheet.param","number"))expect(scopedButton(root,text),true,"Numeric empty operand accepts numeric blocks");return null;});click(4,"Discard draft");}
    }
    void search()throws Exception{
        AtomicInteger chosen=new AtomicInteger(),stopped=new AtomicInteger(),closed=new AtomicInteger();
        AutoSearch.Results view=edt(()->new AutoSearch.Results(FileSeeker.Kind.SCENARIO,p->chosen.incrementAndGet(),stopped::incrementAndGet,closed::incrementAndGet));
        edt(()->{bind(view.component(),"search");bindElements(view.component(),"search");expect(view.use,false,"An empty search has no selectable result");expect(view.stop,true,"An active search can be stopped");expect(view.close,true,"Search can always be closed");return null;});
        if(is("sudden","stop")){edt(()->{event("FW_Optional Stop",0,true);view.stop.doClick(0);expect(view.stop,false,"Stopping cannot be requested twice");return null;});}
        var result=FileSeeker.seek(FileSeeker.Kind.SCENARIO,List.of(new FileSeeker.Root(dir,1,0,"isolated fixture")),new FileSeeker.Budget(1000,50,50),()->false,n->{});
        edt(()->{view.show(result);expect(view.stop,false,"Finished search cannot be stopped");if(!is("selection","none")&&view.table().getRowCount()>0)view.table().setRowSelectionInterval(0,0);else view.table().clearSelection();expect(view.use,!is("selection","none"),"Use follows a selected search result");return null;});
        if(!is("selection","none"))edt(()->{view.use.doClick(0);return null;});
        edt(()->{view.filter().setText("no_such_match_837");expect(view.use,false,"Filtering away the selection disables Use");view.filter().setText("");if(view.table().getRowCount()>0)view.table().setRowSelectionInterval(0,0);expect(view.use,true,"Selecting after filter recovery enables Use");view.use.doClick(0);view.close.doClick(0);return null;});
    }
    void dialogButtons()throws Exception{
        readyScenario();
        for(Navigator.Purpose purpose:Navigator.Purpose.values()){
            edt(()->{
                JFileChooser chooser=desktop.navigator().chooser(purpose,scenario);JComponent accessory=chooser.getAccessory();
                if(accessory!=null){String tab="dialog_"+purpose.name().toLowerCase(Locale.ROOT);bind(accessory,tab);bindElements(accessory,tab);
                    for(Component c:all(accessory))if(c instanceof AbstractButton b&&!Objects.toString(b.getText(),"").isBlank()){expect(b,true,"Available example can be selected in the chooser");event("dialog example "+purpose,0,b.isEnabled());b.doClick(0);}
                }
                return null;
            });
        }
    }
    void reveal(Component c){List<Component> chain=new ArrayList<>();for(Component p=c;p!=null;p=p.getParent())chain.add(p);Collections.reverse(chain);for(Component p:chain)if(p.getParent() instanceof JTabbedPane t){int i=t.indexOfComponent(p);if(i>=0)t.setSelectedIndex(i);}}
    /** Presses as a user can: a hidden button is not pressed; a native dialog the press opens is the headless boundary. */
    void pressNow(Watch w){reveal(w.b);boolean can=pressable(w);event("press "+w.id,0,can);
        if(w.b.isEnabled()&&!can){notes.add(w.id+": enabled, but not shown, so not pressed");return;}
        int before=w.actions;try{w.b.doClick(0);}
        catch(java.awt.HeadlessException e){notes.add(w.id+": native UI boundary reached after button action");}
        catch(RuntimeException e){w.failures.add("Press threw "+e.getClass().getSimpleName()+": "+Objects.toString(e.getMessage(),""));w.hard=true;}
        if(can&&w.actions==before){w.failures.add("Enabled press emitted no ActionEvent");w.hard=true;}}
    void press(Watch w)throws Exception{edt(()->{pressNow(w);return null;});}

    // ---- the per-button sweep -----------------------------------------------------------------------------------------
    /** probe/preconditions.tsv: each button, what it needs, the steps that leave that missing ("-": nothing can), the steps that meet it. */
    static final Map<String,String[]> NEEDS=new LinkedHashMap<>();
    static {try(var in=ButtonProbe.class.getResourceAsStream("/preconditions.tsv")){if(in!=null)for(String line:new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\n"))if(!line.isBlank()){String[] a=line.split("\t",-1);NEEDS.put(a[0],a);}}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static final String TWO_PAIR_RULES="{\"version\":1,\"constraints\":[{\"id\":\"pair1\",\"polarity\":\"forbid\",\"pairs\":[{\"A\":\"latency=1;\",\"B\":\"mode=a;\"}]},{\"id\":\"pair2\",\"polarity\":\"forbid\",\"pairs\":[{\"A\":\"latency=2;\",\"B\":\"mode=b;\"}]}]}";
    static final String MIXED_RULES="{\"version\":1,\"params\":{\"A\":{\"latency=1;\":{\"cost\":1},\"latency=2;\":{\"cost\":2}}},\"constraints\":[{\"id\":\"cost\",\"polarity\":\"forbid\",\"sheets\":[\"A\",\"B\"],\"when\":\"A.cost > 1\"},{\"id\":\"pair1\",\"polarity\":\"forbid\",\"pairs\":[{\"A\":\"latency=1;\",\"B\":\"mode=a;\"}]}]}";
    /** Work whose ready state lasts only the event-thread turn that starts it; a run parked in RUNNING until the check is done. */
    Runnable dispatch; CountDownLatch hold;
    void sweep()throws Exception{
        String ident=option("button","");boolean ready=is("setup","ready");String[] need=NEEDS.get(ident);
        if(need==null||need[2].equals("family"))throw new IllegalArgumentException("No sweep precondition row for "+ident);
        if(!ready&&need[2].equals("-"))throw new IllegalArgumentException(ident+" needs nothing, so it has no missing state; the constraints forbid this row");
        // a reproducer may name another setup that leaves the same need missing
        String plan=options.containsKey("plan")?option("plan",""):ready?need[3]:need[2];int tab=ident.startsWith("t")?Integer.parseInt(ident.substring(1,3)):0;
        if(tab>0)tabs(tab);
        for(String step:plan.split(" "))if(!step.isBlank()&&!step.equals("initial")){step(step);edt(()->{bindAll();return null;});}
        sample((ready?"needs met: ":"needs missing: ")+plan);
        Watch w=watches.get(ident);
        if(w==null)throw new IllegalStateException(ident+" never appeared during the setup "+plan);
        edt(()->{reveal(w.b);return null;});
        if(is("sudden","away")){edt(()->{event("FW_Optional visit another tab",tab,true);desktop.tabs().setSelectedIndex(tab%desktop.tabs().getTabCount());reveal(w.b);return null;});sample("back from another tab");}
        String reason=(ready?"Has what it needs: ":"Lacks what it needs: ")+need[1];
        edt(()->{if(dispatch!=null){dispatch.run();expect(w.b,ready,reason);pressNow(w);}else expect(w.b,ready,reason);return null;});
        if(hold!=null)hold.countDown();
        if(dispatch==null)press(w);
        settle(tab);
        sample("after target press");
        // a reproducer may go on as a user would, to show what the press left behind
        if(options.containsKey("then")){for(String step:option("then","").split(" "))if(!step.isBlank())step(step);settle(tab);sample("after the follow-up");}
    }
    /** Lets the work a press started end, so the next sample and the close see a settled desktop. */
    void settle(int tab)throws Exception{
        pending(desktop.sieve());pending(desktop.candidates());pending(desktop.authoring());
        waitFor(()->edt(()->!(Boolean)field(desktop.library(),"busy")));
        // each view's own work flag: a button that needs input stays off without it, so it cannot tell that work ended
        if(tab==3)waitFor(()->edt(()->!(Boolean)field(desktop.scenario(),"validating")));
        if(tab==8)waitFor(()->edt(()->!(Boolean)field(desktop.analysis(),"analyzing")));
        if(tab==9)waitFor(()->edt(()->!(Boolean)field(desktop.scorer(),"scoring")));
        // the wizard's own work flag: Check stays off for a design with problems, so it cannot tell that work ended
        if(tab==10)waitFor(()->edt(()->!(Boolean)field(desktop.wizard(),"busy")));
    }
    SieveRuleEditor editor()throws Exception{return (SieveRuleEditor)field(desktop.sieve(),"editor");}
    ConditionEditor guard()throws Exception{return (ConditionEditor)field(editor(),"guard");}
    FormulaEditor formula()throws Exception{return (FormulaEditor)field(editor(),"formula");}
    Component dataRoot()throws Exception{return edt(()->((SieveDataEditor)field(desktop.sieve(),"data")).component());}
    JTable cells(){for(Component c:all(scope(2)))if(c instanceof JTable t&&accessible(t).startsWith("Cells of sheet"))return t;throw new IllegalArgumentException("missing the cells table");}
    void remember(Path file)throws Exception{edt(()->{desktop.navigator().trail().record(file);desktop.navigator().refreshHints();return null;});}
    static final String METRICS="candidate_id=a latency=1 FW_VAR=0\ncandidate_id=b latency=2 FW_VAR=0\n";
    /** One named step of a setup; each leaves the desktop in a state a user reaches by the same actions. */
    @SuppressWarnings("unchecked")
    void step(String s)throws Exception{
        switch(s){
            case "scenario"->readyScenario();
            case "large"->largeScenario();
            case "library_selected"->{Path root=Files.createDirectories(dir.resolve("library/intake")).getParent();Files.copy(scenario,root.resolve("small.json"),StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(root.resolve("intake/verified_examples.json"),"[{\"id\":\"small\",\"title\":\"Small\",\"spec\":\"small.json\",\"candidates\":\"4\",\"pass\":\"4\"}]");
                await(edt(()->desktop.library().open(root)));edt(()->{desktop.library().selectSuite(0);desktop.library().selectScenario(0);return null;});}
            case "authoring_open"->await(edt(()->desktop.authoring().open(scenario)));
            case "authoring_new"->edt(()->{desktop.authoring().newDocument();return null;});
            case "sheet_selected"->edt(()->{desktop.authoring().selectSheet(1);return null;});
            case "no_sheet"->edt(()->{find(2,JList.class,"Sheets").clearSelection();return null;});
            case "cell_selected"->edt(()->{desktop.authoring().selectCell(0,0);return null;});
            case "no_cell"->edt(()->{cells().clearSelection();return null;});
            case "template_selected"->edt(()->{find(2,JList.class,"Cell templates").setSelectedIndex(0);return null;});
            case "verdict_selected"->edt(()->{button(2,"Add verdict").doClick(0);find(2,JTable.class,"FW_CUSTOM_VAR: verdict codes and their names").setRowSelectionInterval(0,0);return null;});
            case "graph"->awaitOutcome(edt(()->desktop.authoring().validate()));
            case "python","not_python"->edt(()->{find(3,JComboBox.class,"Runner").setSelectedItem(s.equals("python")?"python":"inline-metrics");return null;});
            case "remembered_scenario"->remember(write("other.json",Files.readString(scenario)));
            case "remembered_metrics"->remember(write("remembered.kv",METRICS));
            case "remembered_text"->remember(write("remembered.txt","latency 10 success\nlatency 20 failure\n"));
            case "metrics_chosen"->{Path kv=write("metrics.kv",METRICS);edt(()->{desktop.analysis().choose(kv,null,null);return null;});}
            case "coverage_format"->{Path kv=write("metrics.kv",METRICS);edt(()->{desktop.analysis().choose(kv,"jacoco",dir.resolve("coverage.xml").toString());return null;});}
            case "text_chosen"->{Path text=write("text.txt","latency 10 success\nlatency 20 failure\nlatency 30 success\n");edt(()->{desktop.scorer().choose(text,"{}");return null;});}
            case "scored"->{step("text_chosen");await(edt(()->desktop.scorer().score()));}
            case "score_fails"->{Path text=write("text.txt","latency 10 success\nlatency 20 failure\nlatency 30 success\n");edt(()->{desktop.scorer().choose(text,"{bad");return null;});awaitOutcome(edt(()->desktop.scorer().score()));}
            case "sieve_open"->{readyScenario();await(edt(()->desktop.sieve().open()));}
            case "rules"->await(edt(()->desktop.sieve().applyJson(PAIR_RULES)));
            case "rules2"->await(edt(()->desktop.sieve().applyJson(TWO_PAIR_RULES)));
            case "mixed"->await(edt(()->desktop.sieve().applyJson(MIXED_RULES)));
            case "rule0","rule1"->edt(()->{desktop.sieve().selectRule(s.equals("rule0")?0:1);return null;});
            case "no_rule"->edt(()->{desktop.sieve().selectRule(-1);return null;});
            case "undo"->edt(()->{desktop.sieve().undo();return null;});
            case "work"->dispatch=()->desktop.sieve().validate(true);
            case "two_chosen"->edt(()->{SieveCanvas c=desktop.sieve().canvas();c.choose(new SieveCanvas.Value("A","latency=1;"));c.choose(new SieveCanvas.Value("B","mode=a;"));
                notes.add("Two values are chosen as two shift-clicks choose them (SieveCanvas.choose); the group button itself is pressed.");return null;});
            case "matrix"->subtab(4,"Bond matrix");
            case "matrix_cell"->edt(()->{((JTable)field(desktop.sieve(),"matrix")).changeSelection(0,1,false,false);return null;});
            case "data"->subtab(4,"Parameters / orders");
            case "attribute"->scopedClick(dataRoot(),"Add attribute");
            case "attribute_selected"->edt(()->{((JTable)field(field(desktop.sieve(),"data"),"table")).setRowSelectionInterval(0,0);return null;});
            case "orders"->subtab(4,"Value orders");
            case "two_values"->{Component root=dataRoot();scopedClick(root,"Add value");scopedClick(root,"Add value");}
            case "value_first","value_last","no_value"->{Component root=dataRoot();edt(()->{JList<?> l=find(root,JList.class,"Ranked values",0);if(s.equals("no_value"))l.clearSelection();else l.setSelectedIndex(s.equals("value_first")?0:l.getModel().getSize()-1);return null;});}
            case "json"->subtab(4,"Advanced JSON");
            case "json_draft"->edt(()->{find(4,JTextArea.class,"Sieve execution JSON").append(" ");return null;});
            case "rule_draft"->edt(()->{find(4,JTextField.class,"Rule ID").setText("changed");return null;});
            case "one_sheet","two_sheets"->edt(()->{JList<?> l=(JList<?>)field(editor(),"sheets");if(s.equals("one_sheet"))l.setSelectedIndex(0);else l.setSelectionInterval(0,1);return null;});
            case "pair_row"->edt(()->{((JTable)field(editor(),"table")).setRowSelectionInterval(0,0);return null;});
            case "guard_leaf"->edt(()->{ConditionEditor c=guard();c.add();c.select(List.of());return null;});
            case "guard_group"->edt(()->{ConditionEditor c=guard();c.add();c.join("all");c.add();return null;});
            case "guard_child0","guard_child1"->edt(()->{guard().select(List.of(s.endsWith("0")?0:1));return null;});
            case "formula"->edt(()->{FormulaEditor f=formula();f.show("1 > 2",List.of("A","B"),x->List.of("cost"));f.toBlocks();return null;});
            case "formula_root"->edt(()->{formula().select("");return null;});
            case "formula_bool_slot"->edt(()->{FormulaEditor f=formula();f.select("");f.delete();return null;});
            case "formula_num_slot"->edt(()->{FormulaEditor f=formula();f.select("b");f.delete();return null;});
            case "formula_operand"->edt(()->{formula().select("a");return null;});
            case "formula_armed"->edt(()->{FormulaEditor f=formula();f.select("a");f.armMove();f.select("b");f.delete();return null;});
            case "preview","preview_all"->{CandidatesView v=desktop.candidates();edt(()->{v.settings("100",s.equals("preview")?1:50,true);return null;});await(edt(v::preview));}
            case "previewing"->{CandidatesView v=desktop.candidates();edt(()->{v.settings("100",1,true);return null;});dispatch=v::preview;}
            case "candidate"->edt(()->{desktop.candidates().select(0);return null;});
            case "step3_changed"->{Path other=write("other.json","{\"sheets\":{\"A\":[[\"other\"]]}}");edt(()->{desktop.scenario().setScenario(other);return null;});}
            case "formatted"->{CandidateCodeView c=edt(()->(CandidateCodeView)field(desktop.candidates(),"source"));edt(()->{c.show(new RenderedSource("{\"a\":1}",List.of(),false));c.language(CodeFormatter.Language.JSON);return null;});await(edt(c::format));}
            case "run_paused","campaign_paused"->startPaused(s.startsWith("campaign"));
            case "run_running","campaign_running"->startHeld(s.startsWith("campaign"));
            case "run_ended"->{await(edt(()->desktop.run().start()));waitFor(()->!executionState(false).equals("IDLE")&&!going(false));refresh(false);}
            case "wizard_filled","wizard_own"->edt(()->{desktop.wizard().fill(s.equals("wizard_own")?GuidedScenario.Situation.OWN:GuidedScenario.Situation.WORKFLOW);return null;});
            case "factor_first","factor_last"->edt(()->{WizardView v=desktop.wizard();v.select(s.equals("factor_first")?0:v.design().factors().size()-1);return null;});
            case "use_one"->edt(()->{desktop.wizard().answer(GuidedScenario.Use.ONE,1,0,1,false,false,false);return null;});
            case "use_exactly"->edt(()->{desktop.wizard().answer(GuidedScenario.Use.EXACTLY,2,0,2,false,false,false);return null;});
            case "use_range"->edt(()->{desktop.wizard().answer(GuidedScenario.Use.RANGE,1,0,2,false,false,false);return null;});
            case "wizard_rule"->edt(()->{((JTable)field(desktop.wizard(),"ruleTable")).setRowSelectionInterval(0,0);return null;});
            case "every","pairs"->edt(()->{desktop.wizard().choose(s.equals("every")?GuidedScenario.Selection.ALL:GuidedScenario.Selection.PAIRS);return null;});
            case "saved"->await(edt(()->desktop.wizard().save(dir.resolve("wizard.json"))));
            case "page_first","page_second","page_last"->edt(()->{JTabbedPane p=desktop.wizard().pages();p.setSelectedIndex(s.equals("page_first")?0:s.equals("page_second")?1:p.getTabCount()-1);return null;});
            case "note_sieve"->{pending(desktop.sieve());notes.add(edt(()->"Sieve view "+((JTabbedPane)field(desktop.sieve(),"views")).getTitleAt(((JTabbedPane)field(desktop.sieve(),"views")).getSelectedIndex())+" says: "+desktop.sieve().output()));}
            default->{if(!s.startsWith("press:"))throw new IllegalArgumentException("Unknown setup step "+s);
                Watch other=watches.get(s.substring(6));if(other==null)throw new IllegalArgumentException("Unknown button to press: "+s);press(other);}
        }
    }
    void startPaused(boolean campaign)throws Exception{AtomicBoolean hook=new AtomicBoolean();
        if(campaign)desktop.campaign().controller().observe((i,p)->{if(p.committed()>=1&&hook.compareAndSet(false,true))desktop.campaign().controller().pause();});
        else desktop.run().controller().observe(p->{if(p.committed()>=1&&hook.compareAndSet(false,true))desktop.run().controller().pause();});
        if(campaign)await(edt(()->desktop.campaign().start()));else await(edt(()->desktop.run().start()));
        waitFor(()->executionState(campaign).equals("PAUSED"));refresh(campaign);}
    /** Parks the run's own thread inside its progress callback, so it stays RUNNING until the check is done; the EDT never parks. */
    void startHeld(boolean campaign)throws Exception{CountDownLatch latch=new CountDownLatch(1);AtomicBoolean parked=new AtomicBoolean();hold=latch;
        java.util.function.Consumer<org.ghyura.cf.campaign.Progress> park=p->{if(!SwingUtilities.isEventDispatchThread()&&p.state()==org.ghyura.cf.campaign.RunState.RUNNING&&p.committed()>=2&&parked.compareAndSet(false,true)){
            try{latch.await(20,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}};
        if(campaign)desktop.campaign().controller().observe((i,p)->park.accept(p));else desktop.run().controller().observe(park::accept);
        if(campaign)await(edt(()->desktop.campaign().start()));else await(edt(()->desktop.run().start()));
        waitFor(parked::get);refresh(campaign);}
    void error(Throwable e){while(e.getCause()!=null&&(e instanceof ExecutionException||e instanceof java.lang.reflect.InvocationTargetException))e=e.getCause();infrastructure=-1;java.io.StringWriter w=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(w));notes.add(w.toString());}
    void finish(){try{if(desktop!=null)await(edt(desktop::close));}catch(Throwable e){error(e);}}
    public int infrastructureCode(){return infrastructure;}
    public boolean violated(String id){Watch w=watches.get(id);return w!=null&&!w.failures.isEmpty();}
    /** The first broken must (ready but not pressable, and the like) names the verdict; else the first missing prerequisite. */
    public int verdict(){if(infrastructure!=0)return infrastructure;for(Watch w:watches.values())if(w.hard)return CODES.getOrDefault(w.id,-7);for(Watch w:watches.values())if(!w.failures.isEmpty())return CODES.getOrDefault(w.id,-7);for(ElementWatch w:elements.values())if(!w.failures.isEmpty())return CODES.getOrDefault(w.id,-7);return 0;}
    public void emit(int verdict)throws Exception{Map<String,Object> e=new LinkedHashMap<>();e.put("family",family);e.put("options",options);e.put("verdict",verdict);e.put("trace",trace);e.put("notes",notes);e.put("buttons",watches.values().stream().map(Watch::data).toList());e.put("elapsed_ms",(System.nanoTime()-began)/1_000_000);
        e.put("elements",elements.values().stream().map(ElementWatch::data).toList());Files.writeString(dir.resolve("evidence.json"),json(e));long failures=watches.values().stream().mapToLong(w->w.failures.size()).sum();System.out.println("app=guif-v2 evidence_id="+dir.getFileName()+" failures="+failures+" elapsed_ms="+(System.nanoTime()-began)/1_000_000+" FW_VAR="+verdict);}
    static String json(Object o){if(o==null)return "null";if(o instanceof Number||o instanceof Boolean)return o.toString();if(o instanceof Map<?,?> m)return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).toList())+"}";if(o instanceof Collection<?> c)return "["+String.join(",",c.stream().map(ButtonProbe::json).toList())+"]";String s=o.toString();return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    public static void main(String[] args)throws Exception{Map<String,String> o=new LinkedHashMap<>();for(int i=1;i<args.length;i++){int at=args[i].indexOf('=');o.put(args[i].substring(0,at),args[i].substring(at+1));}ButtonProbe p=execute(args.length==0?"inventory":args[0],o);p.emit(p.infrastructure);if(p.infrastructure!=0)throw new IllegalStateException(String.join("\n",p.notes));}
}
