package com.embers.legendcooldown;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import com.embers.legendcooldown.mixin.ItemCooldownsAccessor;
import com.embers.legendcooldown.mixin.BossHealthOverlayAccessor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Runs only with runSmoke. No production files or game instances are touched. */
public final class SmokeTest implements ClientModInitializer {
    private int tick;
    private int worldTick;
    private boolean loadingWorld;
    private boolean worldCreated;
    private final List<String> checks=new ArrayList<>();
    private static byte[] utf(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks.add(label);}
    private static String type(String s){return "type.googleapis.com/lunarclient.apollo.cooldown.v1."+s;}
    private void json(String s){ApolloCooldowns.readJson(utf(s));}
    private void reset(){json("{\"@type\":\""+type("ResetCooldownsMessage")+"\"}");}
    private static byte[] var(long v){var b=new ByteArrayOutputStream();do{int n=(int)(v&127);v>>>=7;b.write(v==0?n:n|128);}while(v!=0);return b.toByteArray();}
    private static byte[] join(byte[]... rows){var b=new ByteArrayOutputStream();for(var r:rows)b.writeBytes(r);return b.toByteArray();}
    private static byte[] field(int key,byte[] value){return join(var(key*8+2),var(value.length),value);}
    private Object gui(Minecraft c)throws Exception{return c.getClass().getField("gui").get(c);}
    private Object hud(Minecraft c)throws Exception {Object g=gui(c);try{return g.getClass().getField("hud").get(g);}catch(NoSuchFieldException e){return g;}}
    private void screen(Minecraft c,Screen s)throws Exception{
        try{c.getClass().getMethod("setScreen",Screen.class).invoke(c,s);}
        catch(NoSuchMethodException e){Object g=gui(c);g.getClass().getMethod("setScreen",Screen.class).invoke(g,s);}
    }
    private Screen currentScreen(Minecraft c)throws Exception{
        try{return (Screen)c.getClass().getField("screen").get(c);}
        catch(NoSuchFieldException e){Object g=gui(c);return (Screen)g.getClass().getMethod("screen").invoke(g);}
    }
    private void capture(Minecraft c,String name)throws Exception{
        // This overload exists in all three target versions.
        var method=Arrays.stream(Screenshot.class.getMethods()).filter(m->m.getName().equals("grab")&&m.getParameterCount()==5).findFirst().orElseThrow();
        Object target;
        try { target=c.getClass().getMethod("getMainRenderTarget").invoke(c); }
        catch(NoSuchMethodException e) { target=c.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(c.gameRenderer); }
        method.invoke(null,c.gameDirectory,name,target,1,(java.util.function.Consumer<Component>) message->{});
    }
    private void protocolChecks(Minecraft c)throws Exception {
        reset();
        json("{\"@type\":\""+type("DisplayCooldownMessage")+"\",\"name\":\"Test weapon\",\"duration\":\"12.5s\",\"icon\":{\"item_stack\":{\"item_name\":\"diamond_sword\",\"custom_model_data\":42}}}");
        check(ApolloCooldowns.visible(false).size()==1,"Apollo JSON display");
        check(ApolloCooldowns.visible(false).getFirst().secondsLeft(System.nanoTime())>10,"Fractional duration");
        json("{\"@type\":\""+type("RemoveCooldownMessage")+"\",\"name\":\"Test weapon\"}");
        check(ApolloCooldowns.visible(false).isEmpty(),"Apollo JSON remove");
        byte[] duration=join(var(8),var(20));
        byte[] message=join(field(1,utf("Proto weapon")),field(2,duration));
        ApolloCooldowns.readProto(join(field(1,utf(type("DisplayCooldownMessage"))),field(2,message)));
        check(ApolloCooldowns.visible(false).size()==1,"Apollo protobuf display");
        ApolloCooldowns.readProto(new byte[]{10,(byte)255});
        check(ApolloCooldowns.visible(false).size()==1,"Malformed payload preserves state");
        reset(); check(ApolloCooldowns.visible(false).isEmpty(),"Apollo reset");
        var nativeCooldowns=new ItemCooldowns();
        nativeCooldowns.addCooldown(Identifier.parse("minecraft:ender_pearl"),20);
        nativeCooldowns.tick();
        check(((ItemCooldownsAccessor)nativeCooldowns).legendcooldown$tickCount()==1,"Native cooldown mixins applied");
        nativeCooldowns.removeCooldown(Identifier.parse("minecraft:ender_pearl"));
        Object h=hud(c);
        h.getClass().getMethod("setOverlayMessage",Component.class,boolean.class).invoke(h,Component.literal("Skystrider - 11.2s"),false);
        Object bosses=h.getClass().getMethod("getBossOverlay").invoke(h);
        check(((BossHealthOverlayAccessor)bosses).legendcooldown$events()!=null,"Boss bar accessor applied");
        checks.add("Action bar listener applied");
        var settings=new CooldownSettingsScreen(null);screen(c,settings);
        check(settings.children().size()==14,"Vanilla settings buttons and sliders initialized");
    }
    private ApolloCooldowns.Cooldown named(String name){return ApolloCooldowns.visible(false).stream().filter(row->row.name().equals(name)).findFirst().orElseThrow();}
    private void worldChecks(Minecraft c)throws Exception {
        reset();ServerArtifactCooldowns.clear();
        var artifact=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_SWORD);
        artifact.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Skystrider"));
        artifact.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(List.of(41f),List.of(),List.of(),List.of()));
        c.player.getInventory().setItem(2,artifact);ApolloCooldowns.rememberUsed(artifact);
        Object h=hud(c);
        h.getClass().getMethod("setOverlayMessage",Component.class,boolean.class).invoke(h,Component.literal("Skystrider - 31.2s"),false);
        check(named("Skystrider").icon().getItem()==net.minecraft.world.item.Items.GOLDEN_SWORD,"Real action bar reaches cooldown reader");
        check(named("Skystrider").icon().get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA).floats().getFirst()==41f,"Server text binds full custom item model");
        c.player.getInventory().setItem(2,net.minecraft.world.item.ItemStack.EMPTY);
        check(!ApolloCooldowns.iconForRender(named("Skystrider"),c).isEmpty(),"Consumed item retains icon snapshot");
        var bosses=((BossHealthOverlayAccessor)h.getClass().getMethod("getBossOverlay").invoke(h)).legendcooldown$events();
        var id=UUID.randomUUID();
        bosses.put(id,new net.minecraft.client.gui.components.LerpingBossEvent(id,Component.literal("Skystrider → 30.0s"),.9f,
                net.minecraft.world.BossEvent.BossBarColor.YELLOW,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS,false,false,false));
        ServerArtifactCooldowns.tick(c);
        check(named("Skystrider").secondsLeft(System.nanoTime())<=30,"Boss bar updates cooldown");
        bosses.remove(id);
        ServerArtifactCooldowns.receive("Your Skystrider is now available!",false);
        check(ApolloCooldowns.visible(false).isEmpty(),"Ready message clears timer");
        var pearl=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL);
        c.player.getInventory().setItem(3,pearl);
        var nativeCooldowns=c.player.getCooldowns();nativeCooldowns.addCooldown(pearl,600);
        check(ApolloCooldowns.visible(false).size()==1&&ApolloCooldowns.visible(false).getFirst().vanilla(),"Player native cooldown appears");
        json("{\"@type\":\""+type("DisplayCooldownMessage")+"\",\"name\":\"Pearl\",\"duration\":\"30s\",\"icon\":{\"item_stack\":{\"item_name\":\"ender_pearl\"}}}");
        check(ApolloCooldowns.visible(false).size()==1&&!ApolloCooldowns.visible(false).getFirst().vanilla(),"Apollo and native timer deduplicated");
        reset();nativeCooldowns.removeCooldown(nativeCooldowns.getCooldownGroup(pearl));
        check(ApolloCooldowns.visible(false).isEmpty(),"Native removal leaves no stale timer");
        c.player.getInventory().setItem(2,artifact);ApolloCooldowns.rememberUsed(artifact);
        h.getClass().getMethod("setOverlayMessage",Component.class,boolean.class).invoke(h,Component.literal("Skystrider - 90.5s"),false);
        LegendCooldown.config().cooldownShowNames=true;
        LegendCooldown.config().cooldownStyle="RING";
        screen(c,null);
    }
    private net.minecraft.client.input.MouseButtonEvent mouse(double x, double y) {
        return new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(0,0));
    }
    private void drag(HudEditorScreen editor,double sx,double sy,double tx,double ty) {
        check(editor.mouseClicked(mouse(sx,sy),false),"Editor mouse press accepted");
        editor.mouseDragged(mouse(tx,ty),tx-sx,ty-sy);
        editor.mouseReleased(mouse(tx,ty));
    }
    private void editorChecks(Minecraft c)throws Exception {
        reset();ServerArtifactCooldowns.clear();
        var config=LegendCooldown.config();config.enabled=false;config.scale=1;config.x=25;config.y=35;
        var settings=new CooldownSettingsScreen(null);screen(c,settings);
        var entry=settings.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b
                && b.getMessage().getString().equals(Component.translatable("screen.legendcooldown.editor").getString()))
                .map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();
        settings.mouseClicked(mouse(entry.getX()+10,entry.getY()+10),false);
        check(currentScreen(c) instanceof HudEditorScreen,"Settings button opens visual HUD editor");
        var editor=(HudEditorScreen)currentScreen(c);
        var box=editor.bounds();
        check(box.height()>0&&ApolloCooldowns.visible(false).isEmpty(),"Editor has preview with HUD disabled and no active cooldowns");
        drag(editor,box.x()+20,box.y()+20,box.x()+45,box.y()+35);
        var moved=editor.bounds();
        check(Math.abs(moved.x()-box.x()-25)<.001&&Math.abs(moved.y()-box.y()-15)<.001,"Mouse drag moves both axes without jumping");
        drag(editor,moved.x()+20,moved.y()+20,-500,-500);
        check(editor.bounds().x()==0&&editor.bounds().y()==0,"Dragging clamps at top and left screen edges");
        box=editor.bounds();drag(editor,box.x()+20,box.y()+20,10000,10000);
        box=editor.bounds();
        check(box.x()+box.width()<=editor.width+.001&&box.y()+box.height()<=editor.height+.001,"Dragging clamps at bottom and right screen edges");
        // Move the module away from footer buttons before exercising its resize handle.
        config.x=20;config.y=25;
        box=editor.bounds();float oldScale=config.scale;
        drag(editor,box.x()+box.width(),box.y()+box.height(),box.x()+box.width()+20,box.y()+box.height()+20);
        check(config.scale>oldScale,"Corner drag enlarges cooldown module");
        box=editor.bounds();oldScale=config.scale;
        drag(editor,box.x()+box.width(),box.y()+box.height(),box.x()+box.width()-20,box.y()+box.height()-20);
        check(config.scale<oldScale,"Corner drag shrinks cooldown module");
        box=editor.bounds();oldScale=config.scale;
        editor.mouseScrolled(box.x()+10,box.y()+10,0,1);
        check(config.scale>oldScale,"Mouse wheel enlarges cooldown module");
        box=editor.bounds();oldScale=config.scale;
        editor.mouseScrolled(box.x()+10,box.y()+10,0,-1);
        check(config.scale<oldScale,"Mouse wheel shrinks cooldown module");
        oldScale=config.scale;editor.mouseScrolled(-10,-10,0,1);
        check(config.scale==oldScale,"Scrolling outside module does not change scale");
        config.save();var loaded=CooldownConfig.load();
        check(Math.abs(loaded.x-config.x)<1e-9&&Math.abs(loaded.y-config.y)<1e-9&&loaded.scale==config.scale&&loaded.hudAnchorToScreen,"Dragged position and scale survive config reload");
        editor.onClose();check(currentScreen(c)==settings,"Closing editor returns to settings");
        config.cooldownStyle="RING";
        editor=new HudEditorScreen(null);screen(c,editor);
        var resetButton=(net.minecraft.client.gui.components.Button)editor.children().getFirst();
        editor.mouseClicked(mouse(resetButton.getX()+10,resetButton.getY()+10),false);
        check(config.x==2&&config.y==25&&config.scale==1,"Reset button restores default position and scale");
    }
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("legendcooldown.smoke"))return;
        org.apache.logging.log4j.core.config.Configurator.setLevel("legendcooldown",org.apache.logging.log4j.Level.DEBUG);
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            try {
                // Wait for the initial asynchronous resource reload before opening screens.
                if(++tick>2400)throw new AssertionError("Smoke timed out");
                if(tick%100==0)LegendCooldown.LOGGER.info("SMOKE progress tick={}, screen={}, worldCreated={}, player={}",tick,currentScreen(c)==null?"none":currentScreen(c).getClass().getSimpleName(),worldCreated,c.player!=null);
                if(tick==260 && c.player==null)capture(c,"world-progress.png");
                if(tick==120)screen(c,new CooldownSettingsScreen(null));
                if(tick==150){capture(c,"settings.png");screen(c,new CooldownDiagnosticsScreen(null));CooldownDiagnostics.start();}
                if(tick==180){capture(c,"diagnostics.png");CooldownDiagnostics.stop();}
                if(tick==220){
                    check(CooldownDiagnostics.lastExport()!=null,"Diagnostic report exported");
                    loadingWorld=true;
                    net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.testWorld(c,()->{throw new AssertionError("Test world cancelled");});
                }
                if(loadingWorld && !worldCreated && currentScreen(c) instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen create){
                    worldCreated=true;
                    var method=create.getClass().getDeclaredMethod("onCreate");method.setAccessible(true);method.invoke(create);
                }
                // 26.2's test-world preset enables experiments; accept only this test world's warning.
                if(loadingWorld && worldCreated && c.player==null && currentScreen(c) instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm
                        && confirm.getTitle().getString().toLowerCase(Locale.ROOT).contains("experimental")){
                    var field=net.minecraft.client.gui.screens.ConfirmScreen.class.getDeclaredField("callback");field.setAccessible(true);
                    ((it.unimi.dsi.fastutil.booleans.BooleanConsumer)field.get(confirm)).accept(true);
                }
                if(loadingWorld && c.player!=null){
                    worldTick++;
                    if(worldTick==20){protocolChecks(c);worldChecks(c);}
                    if(worldTick==40){capture(c,"hud-ring.png");LegendCooldown.config().cooldownStyle="ICON";}
                    if(worldTick==60){capture(c,"hud-icon.png");LegendCooldown.config().cooldownStyle="BAR";}
                    if(worldTick==80){capture(c,"hud-bar.png");LegendCooldown.config().cooldownStyle="TEXT";}
                    if(worldTick==100)capture(c,"hud-text.png");
                    if(worldTick==120)editorChecks(c);
                    if(worldTick==140)capture(c,"hud-editor.png");
                    if(worldTick==160){
                    LegendCooldown.config().enabled=true;LegendCooldown.config().save();
                    Path output=c.gameDirectory.toPath().resolve("smoke-result.txt");
                    Files.write(output,checks,StandardCharsets.UTF_8);
                    LegendCooldown.LOGGER.info("SMOKE PASS: {}",checks);c.stop();
                    }
                }
            } catch(Throwable error) {
                LegendCooldown.LOGGER.error("SMOKE FAILED",error);
                try{Files.writeString(c.gameDirectory.toPath().resolve("smoke-failure.txt"),error.toString());}catch(Exception ignored){}
                c.stop();
            }
        });
    }
}
