package org.totipo.android;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import org.totipo.*;
import org.totipo.android.sync.*;
import org.totipo.android.provider.ProviderSnapshot.*;
import static org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;

/** Standalone physical qualification. Only cache-isolated public vaults and file-backed
 * transport are touched; original app owner, SAF grant and M3D evidence are retained.
 * This qualifies product UI/controller + real Java, not SAF or Syncthing transport.
 * Updated for automatic publication; historical results remain in their original reports. */
public final class TokenLifecycleRegression extends Instrumentation {
    private AndroidVaultController controller;
    private MainActivity activity;
    private Path base;
    private Object original;
    private int checks;
    private String step = "setup";
    private Object field(Object value, String name) throws Exception {
        var f=value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    private void check(boolean condition, String label) {
        step=label; if (!condition) throw new AssertionError(label); checks++;
        Bundle out=new Bundle();out.putString("stream","LIFECYCLE "+label+" PASS\n");sendStatus(0,out);
    }
    private boolean idle() {
        try { synchronized(controller) { return !(Boolean)field(controller,"operating") && !(Boolean)field(controller,"viewQueued") && !(Boolean)field(controller,"providerActive") && !(Boolean)field(controller,"syncPending") && !(Boolean)field(controller,"syncDrainQueued"); } }
        catch(Exception e){throw new AssertionError(e);}
    }
    private void await(BooleanSupplier condition) {
        long end=SystemClock.uptimeMillis()+120000;
        while(SystemClock.uptimeMillis()<end) { if(condition.getAsBoolean()) return; SystemClock.sleep(10); }
        throw new AssertionError("timeout at "+step);
    }
    private ForegroundVaultCoordinatorAccess vault() throws Exception { return new ForegroundVaultCoordinatorAccess(field(controller,"vault")); }
    private final class ForegroundVaultCoordinatorAccess {
        final Object value; ForegroundVaultCoordinatorAccess(Object value){this.value=value;}
        VaultSession session() throws Exception {return (VaultSession)field(value,"session");}
        org.totipo.android.reconcile.ForegroundVaultCoordinator coordinator(){return (org.totipo.android.reconcile.ForegroundVaultCoordinator)value;}
    }
    private void command(BooleanSupplier action) { runOnMainSync(()->check(action.getAsBoolean(),"command_admitted")); await(this::idle); }
    private org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken row(TokenId id) {
        return controller.snapshot().view().tokens().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow();
    }
    private void open(TokenId id,TokenChange.Kind kind) {
        runOnMainSync(()->{
            try { var method=MainActivity.class.getDeclaredMethod("openTokenChange",TokenId.class,TokenChange.Kind.class);method.setAccessible(true);method.invoke(activity,id,kind); }
            catch(Exception e){throw new AssertionError(e);}
        });
    }
    private AlertDialog dialog() {try{return (AlertDialog)field(activity,"tokenDialog");}catch(Exception e){throw new AssertionError(e);}}
    private List<EditText> edits(View view) {
        List<EditText> result=new ArrayList<>(); if(view instanceof EditText edit)result.add(edit);
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(edits(group.getChildAt(i)));return result;
    }
    private void positive() {runOnMainSync(()->dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick());await(this::idle);}
    private Map<String,String> inventory(Path root) throws Exception {
        Map<String,String> result=new TreeMap<>();try(var paths=Files.walk(root)){for(Path path:paths.filter(Files::isRegularFile).toList())
            result.put(root.relativize(path).toString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));}return result;
    }
    private void verifyAutomaticPublication(FixturePort port, Map<String,String> before) throws Exception {
        await(this::idle);
        check(controller.dailySyncStatus().isEmpty(),"automatic_sync_quiet");
        var after=inventory(port.root);
        check(after.size()>before.size(),"automatic_sync_gains_objects");
        for(var entry:before.entrySet()) check(entry.getValue().equals(after.get(entry.getKey())),"immutable_provider_history_retained");
        check(after.size()==vault().coordinator().outboundSnapshot().size()+1,"all_local_objects_in_fixture_folder");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle output=new Bundle();int status=0;
        try {
            base=Files.createTempDirectory(getTargetContext().getCacheDir().toPath(),"token-lifecycle-");
            var port=new FixturePort(Files.createDirectory(base.resolve("transport")));
            Handler main=new Handler(Looper.getMainLooper());
            controller=new AndroidVaultController(new LocalReplicaOwner(base.resolve("local")),new AndroidVaultController.Dispatcher(){
                public void post(Runnable r){main.post(r);}
                public Runnable after(long ms,Runnable r){main.postDelayed(r,ms);return ()->main.removeCallbacks(r);}
                public void assertDispatchThread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("UI thread");}
                public void assertWorkerThread(){if(Looper.myLooper()==Looper.getMainLooper())throw new AssertionError("worker thread");}
            },new AndroidVaultController.Backend(),new TotpPresentation.Time(){
                public Instant wall(){return Instant.ofEpochSecond(59);}public long elapsedMillis(){return SystemClock.elapsedRealtime();}
            },null,new SyncFolderBinding(port));
            await(this::idle);command(()->controller.create(new char[0]));
            Path root=base.resolve("local/totipo-vault");Files.write(port.root.resolve("vault"),vault().coordinator().snapshotVault());
            for(String account:List.of("edit@example.test","delete@example.test"))command(()->controller.addToken(
                new AddTokenRequest("Totipo lifecycle public fixture",account,TotpAlgorithm.SHA1,6,30,"AEAQCAI".toCharArray())));
            await(()->controller.snapshot().view().tokens().size()==2);await(this::idle);
            var tokens=controller.snapshot().view().tokens();TokenId edit=tokens.stream().filter(t->t.alternatives().get(0).account().startsWith("edit")).findFirst().orElseThrow().id();
            TokenId delete=tokens.stream().filter(t->!t.id().equals(edit)).findFirst().orElseThrow().id();
            Object application=getTargetContext().getApplicationContext();var appField=TotipoApplication.class.getDeclaredField("vaultController");appField.setAccessible(true);
            original=appField.get(application);runOnMainSync(()->{try{appField.set(application,controller);}catch(Exception e){throw new AssertionError(e);}});
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runOnMainSync(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));waitForIdleSync();
            await(()->activity.hasWindowFocus());check(activity.hasWindowFocus(),"unlocked_foreground_activity");
            var session=vault().session();Map<String,String> before=inventory(port.root);
            open(edit,TokenChange.Kind.EDIT);check(dialog()!=null,"edit_dialog_visible");
            runOnMainSync(()->{var fields=edits(dialog().getWindow().getDecorView());check(fields.size()==2,"metadata_only_no_secret_field");
                check(fields.get(1).getText().toString().equals("edit@example.test"),"edit_seeded_account");fields.get(1).setText("edited@example.test");});
            positive();await(()->row(edit).alternatives().get(0).account().equals("edited@example.test"));waitForIdleSync();
            check(controller.tokenChangeResult()==TokenChange.Result.SAVED,"edit_saved");
            check(vault().session()==session,"edit_same_session");check(controller.snapshot().revealedCode()==null,"edit_no_reveal");verifyAutomaticPublication(port,before);
            before=inventory(port.root);
            await(()->{
                if(controller.snapshot().revealedCode()!=null)return true;
                if(idle())runOnMainSync(()->controller.showCode(delete));
                return false;
            });check(controller.snapshot().revealedCode()!=null,"delete_revealed_fixture");
            open(delete,TokenChange.Kind.DELETE);check(dialog()!=null,"delete_confirmation_visible");
            check(row(delete).alternatives().get(0).status()==TokenStatus.ACTIVE,"confirmation_has_not_authored");
            runOnMainSync(()->dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick());check(row(delete).alternatives().get(0).status()==TokenStatus.ACTIVE,"delete_cancel_no_change");
            open(delete,TokenChange.Kind.DELETE);positive();await(()->row(delete).alternatives().get(0).status()==TokenStatus.TOMBSTONED);waitForIdleSync();
            check(controller.snapshot().revealedCode()==null,"delete_concealed");
            await(()->{try{return ((TokenListAdapter)field(activity,"tokens")).getCount()==1;}catch(Exception e){throw new AssertionError(e);}});
            check(((TokenListAdapter)field(activity,"tokens")).getCount()==1,"delete_absent_from_live_list");
            check(vault().session()==session,"delete_same_session");verifyAutomaticPublication(port,before);
            // Fork complete encrypted Java-authored history to an independent Java session/store.
            Path branch=Files.createDirectory(base.resolve("branch"));Files.write(branch.resolve("vault"),vault().coordinator().snapshotVault());Files.createDirectory(branch.resolve("objects-v1"));
            for(var object:vault().coordinator().outboundSnapshot())Files.write(branch.resolve("objects-v1").resolve(object.id().hex()),object.representation());
            try(var remote=((OpenResult.Opened)Totipo.open(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(branch,new org.totipo.storage.nio.NioDurability()),new char[0])).session()){
                await(()->remote.state().observation() instanceof ObservationProgress.Finished);
                try(var update=remote.state().update(remote.state().token(edit).orElseThrow().alternatives().get(0))){check(update.account("remote@example.test").save() instanceof SaveResult.Saved,"independent_java_branch_authored");}
            }
            open(edit,TokenChange.Kind.EDIT);runOnMainSync(()->edits(dialog().getWindow().getDecorView()).get(1).setText("local@example.test"));positive();
            await(()->row(edit).alternatives().get(0).account().equals("local@example.test"));await(this::idle);
            try(var files=Files.list(branch.resolve("objects-v1"))){for(Path path:files.toList()){Path target=port.root.resolve("objects-v1").resolve(path.getFileName());if(!Files.exists(target))Files.copy(path,target);}}
            command(()->controller.sync());await(()->row(edit).conflict());waitForIdleSync();
            check(TokenListAdapter.rowText(row(edit)).contains("Token conflict"),"real_conflict_rendered");
            runOnMainSync(()->check(!controller.showCode(edit),"conflict_reveal_rejected"));check(controller.snapshot().revealedCode()==null,"conflict_no_arbitrary_code");
            before=inventory(port.root);open(edit,TokenChange.Kind.RESOLVE);check(dialog()!=null,"resolve_chooser_visible");
            check(!dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled(),"resolve_no_preselection");
            int option=-1;for(int i=0;i<row(edit).alternatives().size();i++)if(row(edit).alternatives().get(i).account().equals("remote@example.test"))option=i;
            final int choice=option;check(choice>=0,"complete_remote_alternative_present");
            runOnMainSync(()->{var list=dialog().getListView();list.performItemClick(list.getChildAt(choice),choice,list.getAdapter().getItemId(choice));});positive();
            await(()->!row(edit).conflict());waitForIdleSync();check(row(edit).alternatives().get(0).account().equals("remote@example.test"),"chosen_value_rendered");
            check(vault().session()==session,"resolve_same_session");verifyAutomaticPublication(port,before);
            runOnMainSync(()->activity.finish());waitForIdleSync();command(()->controller.lock());controller.shutdown();
            appField.set(application,original);original=null;
            output.putString("stream","TOKEN LIFECYCLE PASS checks="+checks+"; physical UI/controller + released Java; isolated file transport\n");status=-1;
        }catch(Throwable failure){if(controller!=null)output.putString("controller_state",controller.snapshot().state().toString());output.putString("location",Arrays.toString(Arrays.copyOf(failure.getStackTrace(),Math.min(4,failure.getStackTrace().length))));output.putString("stream","TOKEN LIFECYCLE FAIL step="+step+" type="+failure.getClass().getSimpleName()+" location="+Arrays.toString(Arrays.copyOf(failure.getStackTrace(),Math.min(3,failure.getStackTrace().length)))+" state="+(controller==null?"none":controller.snapshot().state())+"\n");}
        finally{
            try{if(activity!=null)runOnMainSync(()->activity.finish());if(controller!=null){runOnMainSync(()->controller.lock());await(this::idle);controller.shutdown();}
                if(original!=null){var f=TotipoApplication.class.getDeclaredField("vaultController");f.setAccessible(true);f.set(getTargetContext().getApplicationContext(),original);}
                if(base!=null)try(var files=Files.walk(base)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
            }catch(Throwable cleanup){status=0;output.putString("cleanup","FAIL");}
        }finish(status,output);
    }
    /** File-backed isolated transport. Production writer, identity gate and import-before-publish Sync
     * are exercised; Android SAF IPC and external Syncthing remain M3D's responsibility. */
    static final class FixturePort implements SyncFolderBinding.Port,ProviderObjectWriter.Port {
        volatile int scans;
        final Path root;final Tree tree=new Tree("lifecycle-fixture","content://lifecycle-fixture/tree/root","root");
        FixturePort(Path root)throws Exception{this.root=root;Files.createDirectories(root.resolve("objects-v1"));}
        Document doc(String id,String parent,boolean directory){return new Document(tree,"content://lifecycle-fixture/"+id,id,parent,id,directory?"vnd.android.document/directory":"application/octet-stream",null,8L);}
        public SyncFolderBinding.Stored load(){return new SyncFolderBinding.Stored(tree.locator(),true,true);}
        public boolean save(SyncFolderBinding.Stored v){return true;}public boolean validTree(String uri){return uri.equals(tree.locator());}
        public SyncFolderBinding.Grants grants(String uri){return new SyncFolderBinding.Grants(true,true);}
        public void take(String u,boolean r,boolean w){}public void release(String u,boolean r,boolean w){}public void probe(String u){}
        public Scan scan(String uri){scans++;try{
            String epoch=UUID.randomUUID().toString();var directory=doc("objects-v1","root",true);var vault=doc("vault","root",false);
            List<Bytes> objects=new ArrayList<>();try(var files=Files.list(root.resolve("objects-v1"))){for(Path path:files.toList())objects.add(new Bytes(epoch,doc(path.getFileName().toString(),"objects-v1",false),1024,ByteState.PRESENT,Files.readAllBytes(path),Issue.NONE));}
            var vaults=Files.exists(root.resolve("vault"))?List.of(new Bytes(epoch,vault,87,ByteState.PRESENT,Files.readAllBytes(root.resolve("vault")),Issue.NONE)):List.<Bytes>of();
            return new Scan(epoch,tree,new Listing(epoch,"root",vaults.isEmpty()?List.of(directory):List.of(vault,directory),COMPLETE,List.of()),
                List.of(new Directory(directory,new Listing(epoch,"objects-v1",objects.stream().map(Bytes::document).toList(),COMPLETE,List.of()),objects)),COMPLETE,List.of(),vaults);
        }catch(Exception e){throw new IllegalStateException();}}
        public Document create(Document parent,String name)throws Exception{Files.createFile(root.resolve("objects-v1").resolve(name));return doc(name,"objects-v1",false);}
        public Document metadata(Document created){return created;}
        public OutputStream output(Document created)throws Exception{return Files.newOutputStream(root.resolve("objects-v1").resolve(created.id()));}
        public Bytes readBack(Document created)throws Exception{return new Bytes("readback",created,1024,ByteState.PRESENT,Files.readAllBytes(root.resolve("objects-v1").resolve(created.id())),Issue.NONE);}
    }
}
