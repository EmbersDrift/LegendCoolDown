package com.embers.legendcooldown;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
    @Test void fractionalCoordinatesRoundTripWithoutJumping() {
        var box = HudLayout.create(854, 480, 100, 43, 2, 1.25f, 32.123, 67.891);
        assertEquals(32.123, HudLayout.percent(box.x(), 854, box.width()), 1e-9);
        assertEquals(67.891, HudLayout.percent(box.y(), 480, box.height()), 1e-9);
    }
    @Test void dragClampsAtEveryScreenEdge() {
        assertEquals(0, HudLayout.percent(-20, 400, 100));
        assertEquals(100, HudLayout.percent(500, 400, 100));
        assertEquals(0, HudLayout.percent(20, 100, 100));
        assertEquals(0, HudLayout.percent(20, 100, 200));
    }
    @Test void rendererAndHitBoxIncludeWrappedColumns() {
        var box = HudLayout.create(854, 240, 100, 43, 7, 2, 100, 100);
        assertEquals(2, box.perColumn());
        assertEquals(818, box.width());
        assertEquals(172, box.height());
        assertEquals(36, box.x());
        assertTrue(box.contains(850, 239));
        assertTrue(box.resizeHandle(854, 240));
        assertFalse(box.contains(20, 20));
    }
    @Test void relativePositionSurvivesWindowResize() {
        var a = HudLayout.create(854, 480, 100, 43, 2, 1, 50, 50);
        var b = HudLayout.create(1280, 720, 100, 43, 2, 1, 50, 50);
        assertEquals(427, a.x() + a.width() / 2);
        assertEquals(240, a.y() + a.height() / 2);
        assertEquals(640, b.x() + b.width() / 2);
        assertEquals(360, b.y() + b.height() / 2);
    }
    @Test void editedAnchorDoesNotJumpWhenTimersAppearOrExpire() {
        var one = HudLayout.create(854, 480, 100, 43, 1, 1, 25, 25, true);
        var several = HudLayout.create(854, 480, 150, 43, 4, 1, 25, 25, true);
        assertEquals(one.x(), several.x());
        assertEquals(one.y(), several.y());
        var edge = HudLayout.create(854, 480, 150, 43, 4, 1, 100, 100, true);
        assertEquals(854, edge.x() + edge.width());
        assertEquals(480, edge.y() + edge.height());
    }
}
