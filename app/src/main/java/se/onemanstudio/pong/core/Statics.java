package se.onemanstudio.pong.core;

public final class Statics {
    /*
     * Game play state.
     */
    public static final int GAME_INITIALIZING = 0;
    public static final int GAME_READY = 1;
    public static final int GAME_PLAYING = 2;
    public static final int GAME_WON = 3;
    public static final int GAME_LOST = 4;
    public static final int GAME_P1_WON = 5;
    public static final int GAME_P2_WON = 6;

    public static final float BOTTOM_PERC = 43 / 100.0f;
    public static final float BORDER_WIDTH_PERC = 2 / 100.0f;

    /*
     * The paddle attributes. The width of the paddle is configurable based on skill level.
     */
    public static final int DEFAULT_PADDLE_WIDTH = (int) (Statics.ARENA_WIDTH * Statics.PADDLE_WIDTH_PERC * Statics.PADDLE_DEFAULT_WIDTH);

    /*
     * The ball. The diameter is configurable, either for different skill levels or for amusement value.
     */
    public static final int DEFAULT_BALL_DIAMETER = (int) (Statics.ARENA_WIDTH * Statics.BALL_WIDTH_PERC);

    /*
     * Size of the "arena". We pretend we have a fixed-size screen with this many pixels in it. Everything gets scaled to the viewport before display, so this is just an artificial construct that allows us to work with integer values. It also allows us to save and restore values in a
     * screen-dimension-independent way, which would be useful if a saved game were moved between devices through The Cloud. The values here are completely arbitrary. I find it easier to read debug output with 3-digit integer values than, say, floating point numbers between 0.0 and 1.0. What really
     * matters is the proportion of width to height, since that defines the shape of the play area.
     */
    public static final float ARENA_WIDTH = 768.0f;
    public static final float ARENA_HEIGHT = 1024.0f;

    public static final float BORDER_WIDTH = (int) (BORDER_WIDTH_PERC * ARENA_WIDTH);

    /*
     * The top / right position of the score digits. The digits are part of the arena, drawn "under" the ball, and we want them to be as far up and to the right as possible without interfering with the border. The text size is specified in terms of the height of a single digit. That is, we scale the
     * font texture proportionally so the height matches the target. The idea is to have N fixed-width "cells" for the digits, where N is determined by the highest possible score.
     */
    public static final float SCORE_TOP = Statics.ARENA_HEIGHT - Statics.BORDER_WIDTH * 2;
    public static final float SCORE_RIGHT = Statics.ARENA_WIDTH - Statics.BORDER_WIDTH * 2;
    public static final float SCORE_HEIGHT_PERC = 5 / 100.0f;

    /*
     * Storage for collision detection results.
     */
    public static final int HIT_FACE_NONE = 0;
    public static final int HIT_FACE_VERTICAL = 1;
    public static final int HIT_FACE_HORIZONTAL = 2;
    public static final int HIT_FACE_SHARPCORNER = 3;

    /*
     * Vertical position for the paddle, and paddle dimensions. The height (i.e. thickness) is a % of the arena height, and the width is a unit size, based on % of arena width. The width can be increased or decreased based on skill level. We want the paddle to be a little higher up on the screen
     * than it would be in a mouse-based game because there needs to be enough room for the player's finger under the paddle. Depending on the screen dimensions and orientation there may or may not be some touch space outside the viewport, but we can't rely on that.
     */
    public static final float PADDLE_VERTICAL_PERC = 12 / 100.0f;
    public static final float PADDLE_HEIGHT_PERC = 1 / 100.0f;
    public static final float PADDLE_WIDTH_PERC = 2 / 100.0f;
    public static final int PADDLE_DEFAULT_WIDTH = 6;

    /*
     * Events that can happen when the ball moves.
     */
    public static final int EVENT_NONE = 0;
    public static final int EVENT_BALL_LOST_AT_P1_SIDE = 2;
    public static final int EVENT_BALL_LOST_AT_P2_SIDE = 3;

    public static final boolean DEBUG_COLLISIONS = true;                                                                                    // enable increased logging
    public static final boolean SHOW_DEBUG_STUFF = false;                                                                                    // enable on-screen debugging

    /*
     * Ball dimensions. Internally it's just a rect, but we'll give it a circular texture so it looks round. Size is a percentage of the arena width. This can also be adjusted for skill level, up to a fairly goofy level.
     */
    public static final float BALL_WIDTH_PERC = 2.5f / 100.0f;
}
