
package se.onemanstudio.pong.engine;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.ConditionVariable;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import se.onemanstudio.pong.core.BasicAlignedRect;
import se.onemanstudio.pong.core.Statics;
import se.onemanstudio.pong.core.TexturedAlignedRect;
import se.onemanstudio.pong.managers.TextResources;
import se.onemanstudio.pong.tools.Util;


/**
 * Main game display class. The methods here expect to run on the Renderer thread. Calling them from other threads must be done through GLSurfaceView.queueEvent().
 */
public class GameSurfaceRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "GameSurfaceRenderer";
    public static final boolean EXTRA_CHECK = true;                 // enable additional assertions

    /*
     * Orthographic projection matrix. Must be updated when the available screen area changes (e.g. when the device is rotated).
     */
    public static final float mProjectionMatrix[] = new float[16];

    /*
     * Size and position of the GL viewport, in screen coordinates.
     * If the viewport covers the entire screen, the offsets will be zero and the width/height values will match the size of the display.
     * (This is one of the few places where we deal in actual pixels.)
     */
    private int mViewportWidth, mViewportHeight;
    private int mViewportXoff, mViewportYoff;

    private GameSurfaceView mSurfaceView;
    private GameState mGameState;
    private TextResources.Configuration mTextConfig;


    /**
     * Constructs the Renderer. We need references to the GameState, so we can tell it to update and draw things, and to the SurfaceView, so we can tell it to stop animating when the game is over.
     */
    public GameSurfaceRenderer(GameState gameState, GameSurfaceView surfaceView, TextResources.Configuration textConfig) {
        mSurfaceView = surfaceView;
        mGameState = gameState;
        mTextConfig = textConfig;
    }


    /**
     * Handles initialization when the surface is created. This generally happens when the activity is started or resumed. In particular, this is called whenever the device is rotated. All OpenGL
     * state, including programs, must be (re-)generated here.
     */
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        if (EXTRA_CHECK) {
            Util.checkGlError("onSurfaceCreated start");
        }

        // Generate programs and data
        BasicAlignedRect.createProgram();
        TexturedAlignedRect.createProgram();

        // Allocate objects associated with the various graphical elements
        GameState gameState = mGameState;

        gameState.setTextResources(new TextResources(mTextConfig));

        gameState.allocBorders();
        gameState.allocPaddleForPlayer1();
        gameState.allocPaddleForPlayer2();

        gameState.allocBall();
        gameState.allocScore();
        gameState.allocMessages();
        gameState.allocDebugStuff();

        // Restore game state from static storage
        gameState.restore();

        // Set the background color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Disable depth testing -- we're 2D only
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // Don't need backface culling.  (If you're feeling pedantic, you can turn it on to make sure we're defining our shapes correctly)
        if (EXTRA_CHECK) {
            GLES20.glEnable(GLES20.GL_CULL_FACE);
        } else {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
        }

        if (EXTRA_CHECK) {
            Util.checkGlError("onSurfaceCreated end");
        }
    }


    /**
     * Updates the configuration when the underlying surface changes. Happens at least once after every onSurfaceCreated(). If we visit the home screen and immediately return, onSurfaceCreated() may
     * not be called, but this method will.
     */
    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        /*
         * We want the viewport to be proportional to the arena size. That way a 10x10 object in arena coordinates will look square on the screen, and our round ball will look round.
         * If we wanted to fill the entire screen with our game, we would want to adjust the size of the arena itself, not just stretch it to fit the boundaries.
         * This can have subtle effects on gameplay, e.g. the time it takes the ball to travel from the top to the bottom of the screen will be different on a device with a 16:9 display than on
         * a 4:3 display. Other games might address this differently, e.g. a side-scroller could display a bit more of the level on the left and right.
         * We do want to fill as much space as we can, so we should either be pressed up against the left/right edges or top/bottom.
         * Our game plays best in portrait mode. We could force the app to run in portrait mode (by setting a value in AndroidManifest, or by setting the projection to rotate the world to match the
         * longest screen dimension), but that's annoying, especially on devices that don't rotate easily (e.g. plasma TVs).
         */

        if (EXTRA_CHECK) {
            Util.checkGlError("onSurfaceChanged start");
        }

        float arenaRatio = Statics.ARENA_HEIGHT / Statics.ARENA_WIDTH;
        int x, y, viewWidth, viewHeight;

        if (height > (int) (width * arenaRatio)) {
            // limited by narrow width; restrict height
            viewWidth = width;
            viewHeight = (int) (width * arenaRatio);
            x = 0;
            y = (height - viewHeight) / 2;
        } else {
            // limited by short height; restrict width
            viewHeight = height;
            viewWidth = (int) (height / arenaRatio);
            x = (width - viewWidth) / 2;
            y = 0;
        }

        Log.d(TAG, "onSurfaceChanged w=" + width + " h=" + height + " --> x=" + x + " y=" + y + " gw=" + viewWidth + " gh=" + viewHeight);

        GLES20.glViewport(x, y, viewWidth, viewHeight);

        mViewportWidth = viewWidth;
        mViewportHeight = viewHeight;
        mViewportXoff = x;
        mViewportYoff = y;

        // Create an orthographic projection that maps the desired arena size to the viewport dimensions.
        // If we reversed {0, ARENA_HEIGHT} to {ARENA_HEIGHT, 0}, we'd have (0,0) in the upper-left corner instead of the bottom left, which is more familiar for 2D graphics work.
        // It might cause brain ache if we want to mix in 3D elements though.
        Matrix.orthoM(mProjectionMatrix, 0, 0, Statics.ARENA_WIDTH, 0, Statics.ARENA_HEIGHT, -1, 1);

        // Nudge game state after the surface change.
        mGameState.surfaceChanged();

        if (EXTRA_CHECK) {
            Util.checkGlError("onSurfaceChanged end");
        }
    }


    /**
     * Advances game state, then draws the new frame.
     */
    @Override
    public void onDrawFrame(GL10 unused) {
        GameState gameState = mGameState;

        gameState.calculateNextFrame();

        // Simulate slow game state update, to see impact on animation.
        //        try {
        //            Thread.sleep(33);
        //        } catch (InterruptedException ie) {
        //        }

        if (EXTRA_CHECK) {
            Util.checkGlError("onDrawFrame start");
        }

        // Clear entire screen to background color.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Draw the various elements.
        gameState.drawBorders();
        gameState.drawPaddlePlayer1();
        gameState.drawPaddlePlayer2();

        // Enable alpha blending.
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE /* GL_SRC_ALPHA */, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        gameState.drawScore();
        gameState.drawBall();
        gameState.drawMessages();
        gameState.drawDebugStuff();

        // Turn alpha blending off.
        GLES20.glDisable(GLES20.GL_BLEND);

        if (EXTRA_CHECK)
            Util.checkGlError("onDrawFrame end");

        // Stop animating at 60fps (or whatever the refresh rate is) if the game is over.
        //Once we do this, we won't get here again unless something explicitly asks the system to render a new frame.
        // It's a bit clunky to be polling for this, but it needs to be controlled by GameState, otherwiser we would need to have access to the GLSurfaceView.
        if (!gameState.isAnimating()) {
            Log.d(TAG, "Game over, stopping animation");
            mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }


    /**
     * Handles pausing of the game Activity. This is called by the View (via queueEvent) at pause time. It tells GameState to save its state.
     *
     * @param syncObj Object to notify when we have finished saving state.
     */
    public void onViewPause(ConditionVariable syncObj) {
        mGameState.save();

        syncObj.open();
    }


    /**
     * Updates state after the player touches the screen. Call through queueEvent().
     */
    public void touchEvent(float x, float y) {
        /*
         * We chiefly care about the 'x' value, which is used to set the paddle position.
         * We might want to limit based on the 'y' value because it's a little weird to be controlling the
         * paddle from the top half of the screen, but there's no need to do so.
         * We need to re-scale x,y from window coordinates to arena coordinates.
         * The viewport may not fill the entire device window, so it's possible to get values that are outside the arena range.
         * If we were directly implementing other on-screen controls, we'd need to check for touches here.
         */

        float arenaX = (x - mViewportXoff) * (Statics.ARENA_WIDTH / (float) mViewportWidth);

        float arenaY = (y - mViewportYoff) * (Statics.ARENA_HEIGHT / (float) mViewportHeight);

//        Log.v(TAG, "ARENA WxH: " + Statics.ARENA_WIDTH + "x" + Statics.ARENA_HEIGHT + "touch at (" + (int) x + "," + (int) y + ") --> arenaX=" + (int) arenaX + " --> arenaY=" + (int) arenaY);

        if (arenaY >= Statics.ARENA_HEIGHT / 2) {
//            Log.d(TAG, "Move player 1");
            mGameState.movePaddlePlayer1(arenaX);
        } else {
//            Log.d(TAG, "Move player 2");
            mGameState.movePaddlePlayer2(arenaX);
        }
    }
}
