package se.onemanstudio.pong;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import se.onemanstudio.pong.engine.GameState;
import se.onemanstudio.pong.engine.GameSurfaceView;
import se.onemanstudio.pong.managers.TextResources;

/**
 * Activity for the actual game. This is largely just a wrapper for our GLSurfaceView.
 */
public class GameActivity extends Activity {
    private static final String TAG = "GameActivity";

    private static final int DIFFICULTY_MIN = 0;
    private static final int DIFFICULTY_MAX = 3; // inclusive
    private static final int DIFFICULTY_DEFAULT = 1;

    private static int sDifficultyIndex;
    private static boolean sSinglePlayer;

    private GameSurfaceView mGLView; // The Activity has one View, a GL surface.
    private GameState mGameState; // Live game state.


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize data that depends on Android resources.
        TextResources.Configuration textConfig = TextResources.configure(this);

        mGameState = new GameState();
        configureGameState();

        // Create a GLSurfaceView, and set it as the Activity's "content view". This will also create a GLSurfaceView.Renderer, which starts the Renderer thread.
        mGLView = new GameSurfaceView(this, mGameState, textConfig);

        setContentView(mGLView);

        Log.d("GameActivity", "finished onCreate");
    }


    @Override
    protected void onPause() {
        super.onPause();

        mGLView.onPause();

        updateHighScore(GameState.getFinalScore());
    }


    @Override
    protected void onResume() {
        super.onResume();

        mGLView.onResume();
    }


    /**
     * Configures the GameState object with the configuration options set by PongActivity.
     */
    private void configureGameState() {
        int maxLives, minSpeed, maxSpeed;
        float ballSize, paddlePlayer1Size, paddlePlayer2Size;
        long cpuMaxDelay;

        switch (sDifficultyIndex) {
            case 0: // easy
                ballSize = 2.0f;
                paddlePlayer1Size = 2.0f;
                paddlePlayer2Size = 0.5f;
                maxLives = 4;
                minSpeed = 200;
                maxSpeed = 500;
                cpuMaxDelay = 300;
                break;

            case 1: // normal
                ballSize = 1;
                paddlePlayer1Size = 1.0f;
                paddlePlayer2Size = 0.8f;
                maxLives = 3;
                minSpeed = 300;
                maxSpeed = 800;
                cpuMaxDelay = 100;
                break;

            case 2: // hard
                ballSize = 1.0f;
                paddlePlayer1Size = 0.8f;
                paddlePlayer2Size = 1.0f;
                maxLives = 3;
                minSpeed = 600;
                maxSpeed = 1200;
                cpuMaxDelay = 50;
                break;

            case 3: // absurd
                ballSize = 1.0f;
                paddlePlayer1Size = 0.5f;
                paddlePlayer2Size = 2.0f;
                maxLives = 1;
                minSpeed = 1000;
                maxSpeed = 100000;
                cpuMaxDelay = 0;
                break;

            default:
                throw new RuntimeException("bad difficulty index " + sDifficultyIndex);
        }

        mGameState.setBallSizeMultiplier(ballSize);
        mGameState.setPaddleSizePlayer1Multiplier(paddlePlayer1Size);
        mGameState.setPaddleSizePlayer2Multiplier(paddlePlayer2Size);
        mGameState.setMaxLives(maxLives);
        mGameState.setBallInitialSpeed(minSpeed);
        mGameState.setBallMaximumSpeed(maxSpeed);
        mGameState.setCpuDelay(cpuMaxDelay);

        mGameState.setSinglePlayerMode(sSinglePlayer);

        Log.d(TAG, "finished configureGameState");
    }


    /**
     * Configures various tunable parameters based on the difficulty index.
     * <p>
     * Changing the value will cause a game in progress to reset.
     */
    public static void setDifficultyIndex(int difficultyIndex) {
        // This could be coming from preferences set by a different version of the game. We want to be tolerant of values we don't recognize.
        if (difficultyIndex < DIFFICULTY_MIN || difficultyIndex > DIFFICULTY_MAX) {
            Log.w(TAG, "Invalid difficulty index " + difficultyIndex + ", using default");
            difficultyIndex = DIFFICULTY_DEFAULT;
        }

        if (sDifficultyIndex != difficultyIndex) {
            sDifficultyIndex = difficultyIndex;
            invalidateSavedGame();
        }
    }


    public static void setSinglePlayer(boolean singlePlayer) {
        if (sSinglePlayer != singlePlayer) {
            sSinglePlayer = singlePlayer;
            invalidateSavedGame();
        }
    }


    public static int getDifficultyIndex() {
        return sDifficultyIndex;
    }


    public static int getDefaultDifficultyIndex() {
        return DIFFICULTY_DEFAULT;
    }


    public static boolean getSinglePlayer() {
        return sSinglePlayer;
    }


    /**
     * Invalidates the current saved game.
     */
    public static void invalidateSavedGame() {
        GameState.invalidateSavedGame();
    }


    /**
     * Determines whether our saved game is for a game in progress.
     */
    public static boolean canResumeFromSave() {
        return GameState.canResumeFromSave();
    }


    /**
     * Updates high score. If the new score is higher than the previous score, the entry is updated.
     *
     * @param lastScore Score from the last completed game.
     */
    private void updateHighScore(int lastScore) {
        SharedPreferences prefs = getSharedPreferences(PongActivity.PREFS_NAME, MODE_PRIVATE);
        int highScore = prefs.getInt(PongActivity.HIGH_SCORE_KEY, 0);

        Log.d(TAG, "final score was " + lastScore);
        if (lastScore > highScore) {
            Log.d(TAG, "new high score!  (" + highScore + " vs. " + lastScore + ")");

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(PongActivity.HIGH_SCORE_KEY, lastScore);
            editor.commit();
        }
    }
}
