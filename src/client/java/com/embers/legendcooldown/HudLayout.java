package com.embers.legendcooldown;

/** Shared screen-space geometry for HUD drawing and mouse interaction. */
record HudLayout(double x, double y, double width, double height,
                 int cardWidth, int rowHeight, int perColumn, float scale) {
    static HudLayout create(int screenWidth, int screenHeight, int cardWidth, int rowHeight,
                            int count, float scale, double horizontal, double vertical) {
        return create(screenWidth, screenHeight, cardWidth, rowHeight, count, scale, horizontal, vertical, false);
    }
    static HudLayout create(int screenWidth, int screenHeight, int cardWidth, int rowHeight,
                            int count, float scale, double horizontal, double vertical, boolean anchorToScreen) {
        int perColumn = Math.max(1, (int)(screenHeight / scale / rowHeight));
        int rows = Math.min(count, perColumn);
        int columns = (count + perColumn - 1) / perColumn;
        double width = Math.max(0, columns * (cardWidth + 3) - 3) * (double)scale;
        double height = rows * rowHeight * (double)scale;
        double spaceX = Math.max(0, screenWidth - width), spaceY = Math.max(0, screenHeight - height);
        return new HudLayout(anchorToScreen ? Math.clamp(screenWidth * horizontal / 100, 0, spaceX) : spaceX * horizontal / 100,
                anchorToScreen ? Math.clamp(screenHeight * vertical / 100, 0, spaceY) : spaceY * vertical / 100,
                width, height, cardWidth, rowHeight, perColumn, scale);
    }
    boolean contains(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
    boolean resizeHandle(double mx, double my) {
        return Math.abs(mx - x - width) <= 7 && Math.abs(my - y - height) <= 7;
    }
    static double percent(double position, double screenSize, double contentSize) {
        double space = screenSize - contentSize;
        return space <= 0 ? 0 : Math.clamp(position / space * 100, 0, 100);
    }
}
