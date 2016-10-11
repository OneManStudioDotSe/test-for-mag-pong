
package se.onemanstudio.pong.entities;

import se.onemanstudio.pong.core.BasicAlignedRect;


/*
 * Rects used for drawing the border and background. We want the background to be a solid not-quite-black color, with easily visible borders that the ball will bounce off of. We draw the arena
 * background and borders separately. We will touch each pixel only once (not including the glClear). This option gives us the best performance for the visual appearance we want. Also, by defining the
 * border rects as individual entities, we have something to hand to the collision detection code, so we can use the general rect collision algorithm instead of having separate "did I hit a border"
 * tests. Border 0 is special -- it's the bottom of the screen, and colliding with it means you lose the ball. A more general solution would be to create a "Border" class and define any special
 * characteristics there, but that's overkill for this game.
 */
public class Borders {
    private static Borders sInstance;
    public final int NUM_BORDERS = 4;
    public final int BOTTOM_BORDER = 0;
    public final int UPPER_BORDER = 3;
    public BasicAlignedRect mBorders[];


    public static Borders getInstance() {
        if (sInstance == null) {
            sInstance = new Borders();
        }

        return sInstance;
    }


    public Borders() {
        mBorders = new BasicAlignedRect[NUM_BORDERS];
    }

}
