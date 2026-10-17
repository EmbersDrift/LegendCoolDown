package com.embers.legendcooldown;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Explicit, temporary local capture. It does not record player chat or account tokens. */
final class CooldownDiagnostics {
    private static boolean active;
    private static long started;
    private static String startedAt="";
    private static Map<String,Object> initial=Map.of();
    private static CooldownTraceBuffer buffer=new CooldownTraceBuffer(2000);
    private static Path lastExport;
    private static String exportError="";
    private static boolean exporting;
    private static long exportSequence;
    static boolean active(){return active;}
    static int count(){return buffer.size();}
    static int dropped(){return buffer.dropped();}
    static long secondsLeft(){return active?Math.max(0,180-(System.nanoTime()-started)/1_000_000_000L):0;}
    static String exportStatus(){return exporting?"Exporting":!exportError.isEmpty()?exportError:lastExport==null?"":lastExport.toString();}
    static Path lastExport(){return lastExport;}
    static void start(){
        buffer=new CooldownTraceBuffer(2000);started=System.nanoTime();startedAt=Instant.now().toString();
        initial=context();active=true;lastExport=null;exportError="";
        record("capture","start",null,"Capture expires automatically after 180 seconds");
    }
    static void stop(){if(active){record("capture","stop",null,"Capture stopped");active=false;export();}}
    static void tick(){if(active && secondsLeft()==0)stop();}
    static void record(String source,String raw,ServerCooldownText.Signal signal,String decision){
        if(!active)return;
        String parsed=signal==null?"":signal.kind()+" name="+signal.name()+" seconds="+signal.seconds();
        buffer.add((System.nanoTime()-started)/1_000_000L,source,raw,parsed,decision);
    }
    static void used(ItemStack stack){if(active)record("item-use",item(stack).toString(),null,"Observed item interaction; not a confirmed cooldown start");}
    private static Map<String,Object> item(ItemStack stack){
        var data=new LinkedHashMap<String,Object>();
        if(stack.isEmpty())return data;
        data.put("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        data.put("name",CooldownTraceBuffer.limit(stack.getHoverName().getString(),256));
        data.put("count",stack.getCount());
        data.put("customModelData",String.valueOf(stack.get(DataComponents.CUSTOM_MODEL_DATA)));
        data.put("itemModel",String.valueOf(stack.get(DataComponents.ITEM_MODEL)));
        data.put("hasProfile",stack.has(DataComponents.PROFILE));
        var lore=stack.get(DataComponents.LORE);
        if(lore!=null)data.put("lore",lore.lines().stream().limit(20).map(c->CooldownTraceBuffer.limit(c.getString(),256)).toList());
        return data;
    }
    private static Map<String,Object> context(){
        var client=Minecraft.getInstance();var data=new LinkedHashMap<String,Object>();
        data.put("clientBuild",FabricLoader.getInstance().getModContainer(LegendCooldown.ID).orElseThrow().getMetadata().getVersion().getFriendlyString());
        data.put("minecraft",FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
        data.put("server",client.getCurrentServer()==null?"singleplayer":client.getCurrentServer().ip);
        var config=LegendCooldown.config();
        data.put("hudVisible",config.hudVisible);data.put("readServerText",config.cooldownServerText);
        var widget=config;
        data.put("cooldownWidget",Map.of("x",widget.x,"y",widget.y,"scale",widget.scale,"enabled",widget.enabled));
        data.put("cooldownStyle",config.cooldownStyle);
        data.put("guiWidth",client.getWindow().getGuiScaledWidth());data.put("guiHeight",client.getWindow().getGuiScaledHeight());
        data.put("guiScale",client.getWindow().getGuiScale());
        data.put("selectedResourcePacks",List.copyOf(client.getResourcePackRepository().getSelectedIds()));
        if(client.player!=null){
            var inventory=new ArrayList<Map<String,Object>>();
            for(int i=0;i<client.player.getInventory().getContainerSize();i++){
                var stack=client.player.getInventory().getItem(i);
                if(!stack.isEmpty()){var info=item(stack);info.put("slot",i);inventory.add(info);}
            }
            data.put("inventory",inventory);
        }
        data.put("activeCooldowns",ApolloCooldowns.visible(false).stream().map(c->Map.of(
            "name",c.name(),"secondsLeft",c.secondsLeft(System.nanoTime()),"icon",item(c.icon()))).toList());
        return data;
    }
    static void export(){
        if(exporting)return;
        var data=new LinkedHashMap<String,Object>();data.put("startedAt",startedAt);data.put("exportedAt",Instant.now().toString());
        data.put("initial",initial);data.put("current",context());data.put("events",buffer.snapshot());data.put("dropped",buffer.dropped());
        String json=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data);
        Path file=FabricLoader.getInstance().getGameDir().resolve("logs").resolve("legendcooldown-diagnostics-"+
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+(++exportSequence)+".json");
        exporting=true;
        CompletableFuture.runAsync(()->{
            try{Files.createDirectories(file.getParent());Files.writeString(file,json,StandardCharsets.UTF_8);
                Minecraft.getInstance().execute(()->{lastExport=file;exportError="";exporting=false;});
            }catch(Exception error){Minecraft.getInstance().execute(()->{exportError=error.getMessage();exporting=false;});}
        });
    }
}
