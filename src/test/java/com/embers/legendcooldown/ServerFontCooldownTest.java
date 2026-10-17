package com.embers.legendcooldown;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerFontCooldownTest {
    @Test void bothWeaponReportsShareTheFontFailureAndNowParse() throws Exception {
        try(var input=getClass().getResourceAsStream("/kingmc-all-cooldowns.json")){
            assertNotNull(input);
            var rows=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();
            var counts=new java.util.HashMap<String,Integer>();
            for(var element:rows){
                var row=element.getAsJsonObject();String name=row.get("name").getAsString();
                var parsed=ServerCooldownText.read(row.get("raw").getAsString(),true);
                assertNotNull(parsed);assertEquals(name,parsed.name());assertEquals(row.get("seconds").getAsDouble(),parsed.seconds());
                counts.merge(name,1,Integer::sum);
            }
            assertEquals(730,counts.get("Thundermaul"));assertEquals(695,counts.get("Veilblade"));
        }
    }
    @Test void everyRealRejectedServerTimerNowParses() throws Exception {
        try(var input=getClass().getResourceAsStream("/kingmc-cooldown-text.json")){
            assertNotNull(input);
            var rows=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();
            assertEquals(730,rows.size());
            for(var element:rows){
                var row=element.getAsJsonObject();
                var parsed=ServerCooldownText.read(row.get("raw").getAsString(),true);
                assertNotNull(parsed,"A recorded server timer must survive font padding");
                assertEquals(row.get("name").getAsString(),parsed.name());
                assertEquals(row.get("seconds").getAsDouble(),parsed.seconds());
                assertEquals(ServerCooldownText.Kind.REMAINING,parsed.kind());
            }
        }
    }
    @Test void longFontPaddingDoesNotDiscardAValidShortTimer(){
        String padding=new String(Character.toChars(0xD0001)).repeat(2000);
        assertEquals("Skystrider",ServerCooldownText.read(padding+"Skystrider - 11.2s"+padding,true).name());
        assertEquals("",ServerCooldownText.clean("x".repeat(2049)));
        assertNull(ServerCooldownText.read("x".repeat(32769),true));
    }
    @Test void validChineseAndAccentedItemNamesSurviveGlyphRemoval(){
        assertEquals("Épée 神器 - 3s",ServerCooldownText.clean("Épée 神器 - 3s"+new String(Character.toChars(0xCFFAE))));
        assertEquals("Épée 神器",ServerCooldownText.read("Épée 神器 - 3s",true).name());
    }
}
