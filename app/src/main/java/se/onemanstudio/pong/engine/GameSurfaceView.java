
package se.onemanstudio.pong.engine;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.ConditionVariable;
import android.view.MotionEvent;

import se.onemanstudio.pong.managers.TextResources;


/**
 * View object for the GL surface. Wraps the renderer.
 */
public class GameSurfaceView extends GLSurfaceView {
    private GameSurfaceRenderer mRenderer;
    private final ConditionVariable syncObj = new ConditionVariable();


    /**
     * Prepares the OpenGL context and starts the Renderer thread.
     */
    public GameSurfaceView(Context context, GameState gameState, TextResources.Configuration textConfig) {
        super(context);

        setEGLContextClientVersion(2); // Request OpenGL ES 2.0

        //Create our Renderer object, and tell the GLSurfaceView code about it. 
        //This also starts the renderer thread, which will be calling the various callback methods in the GameSurfaceRenderer class.
        mRenderer = new GameSurfaceRenderer(gameState, this, textConfig);

        setRenderer(mRenderer);
    }


    @Override
    public void onPause() {
        //We call a "pause" function in our Renderer class, which tells it to save state and go to sleep. 
        //Because it's running in the Renderer thread, we call it through queueEvent(), which doesn't wait for the code to actually execute. 
        //In theory the application could be killed shortly after we return from here.
        //This would be bad if it happened while the Renderer thread was still saving off important state. We need to wait for it to finish.
        super.onPause();

        syncObj.close();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.onViewPause(syncObj);
            }
        });

        syncObj.block();
    }


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getAction() & MotionEvent.ACTION_MASK;

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                int touchCounter = e.getPointerCount();
                for (int t = 0; t < touchCounter; t++) {

                    final float x = e.getX(t);
                    final float y = e.getY(t);

                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            mRenderer.touchEvent(x, y);
                        }
                    });
                }
                break;

            case MotionEvent.ACTION_DOWN:
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                break;

            case MotionEvent.ACTION_POINTER_UP:
                break;

            case MotionEvent.ACTION_UP:
                break;
        }

        return true;
    }
}
