
package se.onemanstudio.pong.core;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.FloatBuffer;

import se.onemanstudio.pong.engine.GameSurfaceRenderer;
import se.onemanstudio.pong.tools.Util;


/**
 * Represents a two-dimensional axis-aligned solid-color rectangle.
 */
public class BasicAlignedRect extends BaseRect {
    private static final String TAG = "BasicAlignedRect";

    /*
     * Because we're not doing rotation, we can compute vertex positions with simple arithmetic. For every rectangle drawn on screen, we have to compute the position of four vertices. This is how we do it:
     * (3) Merge the position and scale values with the projection matrix to form a classic model/view/projection matrix, and send that in a uniform. We're back to shoving
     * per-object data through a uniform, but we've merged the position and size computation into a matrix multiplication that we have to do anyway.
     * Approach #3 folds the position and size computation into the MVP matrix. Instead of applying those to each vertex, we apply it once per object, and then "hide" the
     * work in the matrix multiplication that we have to do anyway. (OTOH, we have so few vertices per object that the generation of the MVP matrix may actually be a net loss.)
     * This all sounds very complicated... and we've only scratched the surface. There are a lot of factors to consider if you're pushing the limits of the hardware. The same app
     * could be compute-limited on one device and bandwidth-limited on another. Since this game is *nowhere near* the bandwidth or compute capacity of any device capable
     * of OpenGL ES 2.0, this decision is not crucial. Approach #3 is the easiest, and we're not going to bother with VBOs or other memory-management features.
     */

    static final String VERTEX_SHADER_CODE = "uniform mat4 u_mvpMatrix;" + "attribute vec4 a_position;" + "void main() {" + "  gl_Position = u_mvpMatrix * a_position;" + "}";
    static final String FRAGMENT_SHADER_CODE = "precision mediump float;" + "uniform vec4 u_color;" + "void main() {" + "  gl_FragColor = u_color;" + "}";

    // Reference to vertex data.
    static FloatBuffer sVertexBuffer = getVertexArray();

    // Handles to the GL program and various components of it.
    static int sProgramHandle = -1;
    static int sColorHandle = -1;
    static int sPositionHandle = -1;
    static int sMVPMatrixHandle = -1;

    // RGBA color vector.
    float[] mColor = new float[4];

    /*
     * Scratch storage for the model/view/projection matrix. We don't actually need to retain
     * it between calls, but we also don't want to re-allocate space for it every time we draw
     * this object.
     * Because all of our rendering happens on a single thread, we can make this static instead
     * of per-object. To avoid clashes within a thread, this should only be used in draw().
     */
    static float[] sTempMVP = new float[16];


    /**
     * Creates the GL program and associated references.
     */
    public static void createProgram() {
        sProgramHandle = Util.createProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE);
        Log.d(TAG, "Created program " + sProgramHandle);

        // get handle to vertex shader's a_position member
        sPositionHandle = GLES20.glGetAttribLocation(sProgramHandle, "a_position");
        Util.checkGlError("glGetAttribLocation");

        // get handle to fragment shader's u_color member
        sColorHandle = GLES20.glGetUniformLocation(sProgramHandle, "u_color");
        Util.checkGlError("glGetUniformLocation");

        // get handle to transformation matrix
        sMVPMatrixHandle = GLES20.glGetUniformLocation(sProgramHandle, "u_mvpMatrix");
        Util.checkGlError("glGetUniformLocation");
    }


    /**
     * Sets the color.
     */
    public void setColor(float r, float g, float b) {
        Util.checkGlError("setColor start");
        mColor[0] = r;
        mColor[1] = g;
        mColor[2] = b;
        mColor[3] = 1.0f;
    }


    /**
     * Returns a four-element array with the RGBA color info. The caller must not modify the values in the returned array.
     */
    public float[] getColor() {
        /*
         * Normally this sort of function would make a copy of the color data and return that, but
         * we want to avoid allocating objects. We could also implement this as four separate
         * methods, one for each component, but that's slower and annoying.
         */
        return mColor;
    }


    /**
     * Draws the rect.
     */
    public void draw() {
        if (GameSurfaceRenderer.EXTRA_CHECK)
            Util.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(sProgramHandle);
        Util.checkGlError("glUseProgram");

        // Enable the "a_position" vertex attribute.
        GLES20.glEnableVertexAttribArray(sPositionHandle);
        Util.checkGlError("glEnableVertexAttribArray");

        // Connect mVertexBuffer to "a_position".
        GLES20.glVertexAttribPointer(sPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, sVertexBuffer);
        Util.checkGlError("glVertexAttribPointer");

        // Compute model/view/projection matrix.
        float[] mvp = sTempMVP; // scratch storage
        Matrix.multiplyMM(mvp, 0, GameSurfaceRenderer.mProjectionMatrix, 0, mModelView, 0);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(sMVPMatrixHandle, 1, false, mvp, 0);
        Util.checkGlError("glUniformMatrix4fv");

        // Copy the color vector into the program.
        GLES20.glUniform4fv(sColorHandle, 1, mColor, 0);
        Util.checkGlError("glUniform4fv ");

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

        // Disable vertex array and program.
        GLES20.glDisableVertexAttribArray(sPositionHandle);
        GLES20.glUseProgram(0);

        if (GameSurfaceRenderer.EXTRA_CHECK) {
            Util.checkGlError("draw end");
        }
    }
}
