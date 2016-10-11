
package se.onemanstudio.pong.core;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.FloatBuffer;

import se.onemanstudio.pong.engine.GameSurfaceRenderer;
import se.onemanstudio.pong.tools.Util;


/**
 * A rectangle drawn as an outline rather than filled. Useful for debugging.
 */
public class OutlineAlignedRect extends BasicAlignedRect {
    private static FloatBuffer sOutlineVertexBuffer = getOutlineVertexArray();


    @Override
    public void draw() {
        if (GameSurfaceRenderer.EXTRA_CHECK) {
            Util.checkGlError("draw start");
        }

        // Set the program.  We use the same one as BasicAlignedRect.
        GLES20.glUseProgram(sProgramHandle);
        Util.checkGlError("glUseProgram");

        // Enable the "a_position" vertex attribute.
        GLES20.glEnableVertexAttribArray(sPositionHandle);
        Util.checkGlError("glEnableVertexAttribArray");

        // Connect mVertexBuffer to "a_position".
        GLES20.glVertexAttribPointer(sPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, sOutlineVertexBuffer);
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
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, VERTEX_COUNT);

        // Disable vertex array and program.
        GLES20.glDisableVertexAttribArray(sPositionHandle);
        GLES20.glUseProgram(0);

        if (GameSurfaceRenderer.EXTRA_CHECK) {
            Util.checkGlError("draw end");
        }
    }
}
