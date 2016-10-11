package se.onemanstudio.pong.engine;

import se.onemanstudio.pong.core.BaseRect;
import se.onemanstudio.pong.core.BasicAlignedRect;
import se.onemanstudio.pong.core.OutlineAlignedRect;
import se.onemanstudio.pong.core.Statics;
import se.onemanstudio.pong.core.TexturedAlignedRect;
import se.onemanstudio.pong.entities.Ball;
import se.onemanstudio.pong.entities.Borders;
import se.onemanstudio.pong.managers.TextResources;

import android.graphics.Rect;
import android.util.Log;

/**
 * This is the primary class for the game itself.
 * <p>
 * This class is intended to be isolated from the Android app UI. It does not hold references to framework objects like the Activity or View. This is a useful property architecturally, but more importantly it removes the possibility of calling non-thread-safe Activity or View methods from the wrong
 * thread.
 * <p>
 * The class is closely associated with GameSurfaceRenderer, and code here generally runs on the Renderer thread. The only exceptions to the rule are the methods used to configure the game, which may only be used before the Renderer thread starts, and the saved game manipulation, which is
 * synchronized.
 */
public class GameState {
    private static final String TAG = "GameState";

    // Gameplay configurables. These may not be changed while the game is in progress, and changing a value invalidates the saved game.
    private static boolean mSinglePlayer = false;
    private int mMaxLives = 3;
    private int mBallInitialSpeed = 300;
    private int mBallMaximumSpeed = 800;
    private float mBallSizeMultiplier = 1.0f;
    private float mPaddleSizePlayer1Multiplier = 1.0f;
    private float mPaddleSizePlayer2Multiplier = 1.0f;
    private long mCpuDelay = 0;
    private BasicAlignedRect mBackground;

    /*
     * The border of the arena
     */
    private Borders gameArenaBorders = Borders.getInstance();

    /*
     * The ball that we play with :)
     */
    private Ball mBall;

    /*
     * The paddles for the 2 players
     */
    private BasicAlignedRect mPaddlePlayer1;
    private BasicAlignedRect mPaddlePlayer2;

    // In-memory saved game. The game is saved and restored whenever the Activity is paused
    // and resumed. This should be the only static variable in GameState.
    private static SavedGame sSavedGame = new SavedGame();

    /*
     * Timestamp of previous frame. Used for animation. We cap the maximum inter-frame delta at 0.5 seconds, so that a major hiccup won't cause things to behave too crazily.
     */
    private static final double NANOS_PER_SECOND = 1000000000.0;
    private static final double MAX_FRAME_DELTA_SEC = 0.5;
    private long mPrevFrameWhenNsec;

    /*
     * Pause briefly on certain transitions, e.g. before launching a new ball after one was lost.
     */
    private float mPauseDuration;

    // If FRAME_RATE_SMOOTHING is true, then the rest of these fields matter.
    private static final boolean FRAME_RATE_SMOOTHING = false;
    private static final int RECENT_TIME_DELTA_COUNT = 5;
    double mRecentTimeDelta[] = new double[RECENT_TIME_DELTA_COUNT];
    int mRecentTimeDeltaNext;

    private BaseRect[] mPossibleCollisions = new BaseRect[Borders.getInstance().NUM_BORDERS + NUM_SCORE_DIGITS + 1];
    private float mHitDistanceTraveled;                                                                                        // result from findFirstCollision()
    private float mHitXAdj, mHitYAdj;                                                                                        // result from findFirstCollision()
    private int mHitFace;                                                                                                    // result from findFirstCollision()
    private OutlineAlignedRect mDebugCollisionRect;                                                                                        // visual debugging

    private int mGamePlayState;

    private boolean mIsAnimating;

    private int mLivesPlayer1Remaining;
    private int mLivesPlayer2Remaining;
    private int mScore;

    /*
     * Text message to display in the middle of the screen (e.g. "won" or "game over").
     */
    private static final float STATUS_MESSAGE_WIDTH_PERC = 85 / 100.0f;
    private TexturedAlignedRect mGameStatusMessages;
    private int mGameStatusMessageNum;

    private int mDebugFramedString;

    /*
     * Score display. the constructor. It is fixed, though, so we can be lazy and just hard-code a value here.
     */
    private static final int NUM_SCORE_DIGITS = 5;
    private TexturedAlignedRect[] mScoreDigits = new TexturedAlignedRect[NUM_SCORE_DIGITS];

    /*
     * Text resources, notably including an image texture for our various text strings.
     */
    private TextResources mTextRes;


    public GameState() {
    }


    public void setSinglePlayerMode(boolean singlePlayerMode) {
        mSinglePlayer = singlePlayerMode;
        Log.d(TAG, "Set single player to be " + mSinglePlayer);
    }


    public void setCpuDelay(long cpuError) {
        mCpuDelay = cpuError;
    }


    public void setMaxLives(int maxLives) {
        mMaxLives = maxLives;
    }


    public void setBallInitialSpeed(int speed) {
        mBallInitialSpeed = speed;
    }


    public void setBallMaximumSpeed(int speed) {
        mBallMaximumSpeed = speed;
    }


    public void setBallSizeMultiplier(float mult) {
        mBallSizeMultiplier = mult;
    }


    public void setPaddleSizePlayer1Multiplier(float mult) {
        mPaddleSizePlayer1Multiplier = mult;
    }


    public void setPaddleSizePlayer2Multiplier(float mult) {
        mPaddleSizePlayer2Multiplier = mult;
    }


    /**
     * Resets game state to initial values. Does not reallocate any storage or access saved game state. This is called when we're asked to restore a game, but no saved game exists.
     */
    private void reset() {
        mGamePlayState = Statics.GAME_INITIALIZING;
        mIsAnimating = true;
        mGameStatusMessageNum = TextResources.NO_MESSAGE;
        mPrevFrameWhenNsec = 0;
        mPauseDuration = 0.0f;
        mRecentTimeDeltaNext = -1;
        mLivesPlayer1Remaining = mMaxLives;
        mLivesPlayer2Remaining = mMaxLives;
        mScore = 0;

        resetBall();
    }


    /**
     * Moves the ball to its start position, resetting direction and speed to initial values.
     */
    private void resetBall() {
        mBall.setDirection(-0.3f, -1.0f);
        mBall.setSpeed(mBallInitialSpeed);

        mBall.setPosition(Statics.ARENA_WIDTH / 2.0f + 45, Statics.ARENA_HEIGHT * Statics.BOTTOM_PERC - 100);
    }


    /**
     * Saves game state into static storage. We could just declare everything in GameState "static" and it would work just as well (unless we wanted to preserve state when the app process is killed by the system). It's a useful exercise though, and by avoiding statics we allow the GC to discard all
     * the game state when the GameActivity goes away. We synchronize on the object because multiple threads can access it.
     */
    public void save() {
        synchronized (sSavedGame) {
            SavedGame save = sSavedGame;

            save.mBallXDirection = mBall.getXDirection();
            save.mBallYDirection = mBall.getYDirection();
            save.mBallXPosition = mBall.getXPosition();
            save.mBallYPosition = mBall.getYPosition();
            save.mBallSpeed = mBall.getSpeed();
            save.mPaddlePlayer1Position = mPaddlePlayer1.getXPosition();
            save.mPaddlePlayer2Position = mPaddlePlayer2.getXPosition();

            save.mGamePlayState = mGamePlayState;
            save.mGameStatusMessageNum = mGameStatusMessageNum;
            save.mLivesPlayer1Remaining = mLivesPlayer1Remaining;
            save.mLivesPlayer2Remaining = mLivesPlayer1Remaining;
            save.mScore = mScore;

            save.mIsValid = true;
        }
    }


    /**
     * Restores game state from save area. If no saved game is available, we just reset the values.
     *
     * @return true if we restored from a saved game.
     */
    public boolean restore() {
        synchronized (sSavedGame) {
            SavedGame save = sSavedGame;
            if (!save.mIsValid) {
                Log.d(TAG, "No valid saved game found");
                reset();
                save(); // initialize save area
                return false;
            } else {
                Log.d(TAG, "I found a game to restore !");

                mBall.setDirection(save.mBallXDirection, save.mBallYDirection);
                mBall.setPosition(save.mBallXPosition, save.mBallYPosition);
                mBall.setSpeed(save.mBallSpeed);

                movePaddlePlayer1(save.mPaddlePlayer1Position);
                movePaddlePlayer2(save.mPaddlePlayer2Position);

                mGamePlayState = save.mGamePlayState;
                mGameStatusMessageNum = save.mGameStatusMessageNum;
                mLivesPlayer1Remaining = save.mLivesPlayer1Remaining;
                mLivesPlayer2Remaining = save.mLivesPlayer2Remaining;
                mScore = save.mScore;

                if (mGamePlayState == Statics.GAME_LOST || mGamePlayState == Statics.GAME_WON || mGamePlayState == Statics.GAME_P1_WON || mGamePlayState == Statics.GAME_P2_WON) {
                    reset();
                }
            }

        }

        return true;
    }


    /**
     * Performs some housekeeping after the Renderer surface has changed.
     * <p>
     * This is called after a screen rotation or when returning to the app from the home screen.
     */
    public void surfaceChanged() {
        setPauseTime(1.5f);// Pause briefly. This gives the user time to orient themselves after a screen rotation or switching back from another app.

        mPrevFrameWhenNsec = 0;// Reset this so we don't leap forward.
        mIsAnimating = true; // We need to draw the screen at least once, so set this whether or not we're actually animating.
    }


    /**
     * Sets the TextResources object that the game will use.
     */
    public void setTextResources(TextResources textRes) {
        mTextRes = textRes;
    }


    /**
     * Marks the saved game as invalid.
     * <p>
     * May be called from a non-Renderer thread.
     */
    public static void invalidateSavedGame() {
        synchronized (sSavedGame) {
            sSavedGame.mIsValid = false;
        }
    }


    /**
     * Determines whether we have saved a game that can be resumed. We would need to have a valid saved game and be playing or about to play.
     * <p>
     * May be called from a non-Renderer thread.
     */
    public static boolean canResumeFromSave() {
        synchronized (sSavedGame) {
            return sSavedGame.mIsValid && (sSavedGame.mGamePlayState == Statics.GAME_PLAYING || sSavedGame.mGamePlayState == Statics.GAME_READY);
        }
    }


    /**
     * Gets the score from a completed game.
     * <p>
     * If we returned the score of a game in progress, we could get excessively high results for games where points may be deducted (e.g. never-lose-ball mode).
     * <p>
     * May be called from a non-Renderer thread.
     *
     * @return The score, or -1 if the current save state doesn't hold a completed game.
     */
    public static int getFinalScore() {
        synchronized (sSavedGame) {
            if (mSinglePlayer) {
                if (sSavedGame.mIsValid && (sSavedGame.mGamePlayState == Statics.GAME_WON || sSavedGame.mGamePlayState == Statics.GAME_LOST)) {
                    return sSavedGame.mScore;
                } else {
                    return -1;
                }
            } else {
                return -1;
            }
        }
    }


    /**
     * Returns true if we want the system to call our draw methods.
     */
    public boolean isAnimating() {
        return mIsAnimating;
    }


    /**
     * Allocates the rects that define the borders and background.
     */
    void allocBorders() {
        BasicAlignedRect rect;

        // Need one rect that covers the entire play area (i.e. viewport) in the background color.
        rect = new BasicAlignedRect();
        rect.setPosition(Statics.ARENA_WIDTH / 2, Statics.BORDER_WIDTH / 2);
        rect.setScale(Statics.ARENA_WIDTH - Statics.BORDER_WIDTH * 2, Statics.BORDER_WIDTH);
        rect.setColor(1.0f, 0.0f, 0.0f);
        mBackground = rect;

        // This rect is just off the bottom of the arena. If we collide with it, the ball is lost. This must be BOTTOM_BORDER (zero).
        rect = new BasicAlignedRect();
        rect.setPosition(Statics.ARENA_WIDTH / 2, -Statics.BORDER_WIDTH / 2);
        rect.setScale(Statics.ARENA_WIDTH, Statics.BORDER_WIDTH);
        rect.setColor(1.0f, 0.65f, 0.0f);
        gameArenaBorders.mBorders[Borders.getInstance().BOTTOM_BORDER] = rect;

        // Need one rect each for left / right / top.
        rect = new BasicAlignedRect();
        rect.setPosition(Statics.BORDER_WIDTH / 2, Statics.ARENA_HEIGHT / 2);
        rect.setScale(Statics.BORDER_WIDTH, Statics.ARENA_HEIGHT);
        rect.setColor(1.0f, 1.0f, 1.0f);
        gameArenaBorders.mBorders[1] = rect;

        rect = new BasicAlignedRect();
        rect.setPosition(Statics.ARENA_WIDTH - Statics.BORDER_WIDTH / 2, Statics.ARENA_HEIGHT / 2);
        rect.setScale(Statics.BORDER_WIDTH, Statics.ARENA_HEIGHT);
        rect.setColor(1.0f, 1.0f, 1.0f);
        gameArenaBorders.mBorders[2] = rect;

        rect = new BasicAlignedRect();
        rect.setPosition(Statics.ARENA_WIDTH / 2, Statics.ARENA_HEIGHT - Statics.BORDER_WIDTH / 2);
        rect.setScale(Statics.ARENA_WIDTH - Statics.BORDER_WIDTH * 2, Statics.BORDER_WIDTH);
        rect.setColor(1.0f, 0.0f, 0.0f);
        gameArenaBorders.mBorders[Borders.getInstance().UPPER_BORDER] = rect;
    }


    /**
     * Creates the ball.
     */
    void allocBall() {
        Ball ball = new Ball();

        int diameter = (int) (Statics.DEFAULT_BALL_DIAMETER * mBallSizeMultiplier);
        ball.setScale(diameter, diameter);

        mBall = ball;
    }


    /**
     * Creates the paddle for user 1.
     */
    void allocPaddleForPlayer1() {
        BasicAlignedRect rect = new BasicAlignedRect();

        rect.setScale(Statics.DEFAULT_PADDLE_WIDTH * mPaddleSizePlayer1Multiplier, Statics.ARENA_HEIGHT * Statics.PADDLE_HEIGHT_PERC);
        rect.setColor(1.0f, 1.0f, 1.0f); // note color is cycled during pauses

        float initialPositionX = Statics.ARENA_WIDTH / 2.0f;
        float initialPositionY = Statics.ARENA_HEIGHT * Statics.PADDLE_VERTICAL_PERC;

        rect.setPosition(initialPositionX, initialPositionY);
        Log.d(TAG, "paddle1 y=" + rect.getYPosition() + ", positioned the paddle for player 1 at " + initialPositionX + ", " + initialPositionY);

        mPaddlePlayer1 = rect;
    }


    /**
     * Creates the paddle for user 2.
     */
    void allocPaddleForPlayer2() {
        BasicAlignedRect rect = new BasicAlignedRect();

        rect.setScale(Statics.DEFAULT_PADDLE_WIDTH * mPaddleSizePlayer2Multiplier, Statics.ARENA_HEIGHT * Statics.PADDLE_HEIGHT_PERC);
        rect.setColor(1.0f, 1.0f, 1.0f); // note color is cycled during pauses

        float initialPositionX = Statics.ARENA_WIDTH / 2.0f;
        float initialPositionY = Statics.ARENA_HEIGHT - (Statics.ARENA_HEIGHT * Statics.PADDLE_VERTICAL_PERC);

        rect.setPosition(initialPositionX, initialPositionY);
        Log.d(TAG, "paddle2 y=" + rect.getYPosition() + ", positioned the paddle for player 2 at " + initialPositionX + ", " + initialPositionY);

        mPaddlePlayer2 = rect;
    }


    /**
     * Creates objects required to display a numeric score.
     */
    void allocScore() {
        /*
		 * The score digits occupy a fixed position at the top right of the screen. They're actually part of the arena, and sit "under" the ball. The basic plan is to find the widest glyph, scale it up to match the height we want, and use that as the size of a cell. The digits are drawn scaled up to
		 * that height, with the width increased proportionally
		 */

        int maxWidth = 0;
        Rect widest = null;

        for (int i = 0; i < 10; i++) {
            Rect boundsRect = mTextRes.getTextureRect(TextResources.DIGIT_START + i);
            int rectWidth = boundsRect.width();

            if (maxWidth < rectWidth) {
                maxWidth = rectWidth;
                widest = boundsRect;
            }
        }

        float widthHeightRatio = (float) widest.width() / widest.height();
        float cellHeight = Statics.ARENA_HEIGHT * Statics.SCORE_HEIGHT_PERC;
        float cellWidth = cellHeight * widthHeightRatio * 1.05f; // add 5% spacing between digits

        // Note these are laid out from right to left, i.e. mScoreDigits[0] is the 1s digit.
        for (int i = 0; i < NUM_SCORE_DIGITS; i++) {
            mScoreDigits[i] = new TexturedAlignedRect();
            mScoreDigits[i].setTexture(mTextRes.getTextureHandle(), mTextRes.getTextureWidth(), mTextRes.getTextureHeight());
            mScoreDigits[i].setPosition(Statics.SCORE_RIGHT - (i * cellWidth) - cellWidth / 2, Statics.SCORE_TOP - cellHeight / 2);
        }
    }


    /**
     * Creates storage for a message to display in the middle of the screen.
     */
    void allocMessages() {
		/*
		 * The messages (e.g. "won" and "lost") are stored in the same texture, so the choice of which text to show is determined by the texture coordinates stored in the TexturedAlignedRect. We can update those without causing an allocation, so there's no need to allocate a separate drawable rect
		 * for every possible message.
		 */

        mGameStatusMessages = new TexturedAlignedRect();

        mGameStatusMessages.setTexture(mTextRes.getTextureHandle(), mTextRes.getTextureWidth(), mTextRes.getTextureHeight());
        mGameStatusMessages.setPosition(Statics.ARENA_WIDTH / 2, Statics.ARENA_HEIGHT / 2);
    }


    /**
     * Allocates shapes that we use for "visual debugging".
     */
    void allocDebugStuff() {
        mDebugCollisionRect = new OutlineAlignedRect();
        mDebugCollisionRect.setColor(1.0f, 0.0f, 0.0f);
    }


    /**
     * Draws the border and background rects.
     */
    void drawBorders() {
        mBackground.draw();

        for (int i = 0; i < gameArenaBorders.mBorders.length; i++) {
            gameArenaBorders.mBorders[i].draw();
        }
    }


    /**
     * Draws the paddle for user 1.
     */
    void drawPaddlePlayer1() {
        mPaddlePlayer1.draw();
    }


    /**
     * Draws the paddle for user 2.
     */
    void drawPaddlePlayer2() {
        mPaddlePlayer2.draw();
    }


    /**
     * Draws the "live" ball and the remaining-lives display.
     */
    void drawBall() {
		/*
		 * We use the lone mBall object to draw all instances of the ball. We just move it around for each instance.
		 */
        Ball ball = mBall;

        float savedX = ball.getXPosition();
        float savedY = ball.getYPosition();
        float radius = ball.getRadius();

        boolean ballIsLive = (mGamePlayState != Statics.GAME_INITIALIZING && mGamePlayState != Statics.GAME_READY);

        if (mSinglePlayer) {
            float xpos = Statics.BORDER_WIDTH * 2 + radius;
            float ypos = Statics.BORDER_WIDTH + 2 * radius;
            int lives = mLivesPlayer1Remaining;

            for (int i = 0; i < lives; i++) {
                // Vibrate the "remaining lives" balls when we're about to lose. It's kind of silly, but it's easy to do.
                float jitterX = 0.0f;
                float jitterY = 0.0f;
                if (lives == 1) {
                    jitterX = (float) (3 * (Math.random() - 0.5) * 2);
                    jitterY = (float) (3 * (Math.random() - 0.5) * 2);
                }
                ball.setPosition(xpos + jitterX, ypos + jitterY);
                ball.draw();

                xpos += radius * 3;
            }

            movePaddlePlayer2(savedX);
        } else {
            // for player 1
            float xpos = Statics.BORDER_WIDTH * 2 + radius;
            float ypos = Statics.BORDER_WIDTH + 2 * radius;
            int lives = mLivesPlayer1Remaining;

            // draw the lives for p1
            for (int i = 0; i < lives; i++) {
                float jitterX = 0.0f;
                float jitterY = 0.0f;
                if (lives == 1) {
                    jitterX = (float) (3 * (Math.random() - 0.5) * 2);
                    jitterY = (float) (3 * (Math.random() - 0.5) * 2);
                }
                ball.setPosition(xpos + jitterX, ypos + jitterY);
                ball.draw();

                xpos += radius * 3;
            }

            // for player 2
            xpos = Statics.BORDER_WIDTH * 2 + radius;
            ypos = Statics.ARENA_HEIGHT - Statics.BORDER_WIDTH - 2 * radius;
            lives = mLivesPlayer2Remaining;

            for (int i = 0; i < lives; i++) {
                float jitterX = 0.0f;
                float jitterY = 0.0f;
                if (lives == 1) {
                    jitterX = (float) (3 * (Math.random() - 0.5) * 2);
                    jitterY = (float) (3 * (Math.random() - 0.5) * 2);
                }
                ball.setPosition(xpos + jitterX, ypos + jitterY);
                ball.draw();

                xpos += radius * 3;
            }
        }

        ball.setPosition(savedX, savedY);

        if (ballIsLive) {
            ball.draw();
        }
    }


    /**
     * Draws the current score.
     */
    void drawScore() {
        if (mSinglePlayer) {
            float cellHeight = Statics.ARENA_HEIGHT * Statics.SCORE_HEIGHT_PERC;
            int score = mScore;

            for (int i = 0; i < NUM_SCORE_DIGITS; i++) {
                int val = score % 10;
                Rect boundsRect = mTextRes.getTextureRect(TextResources.DIGIT_START + val);
                float ratio = cellHeight / boundsRect.height();

                TexturedAlignedRect scoreCell = mScoreDigits[i];
                scoreCell.setTextureCoords(boundsRect);
                scoreCell.setScale(boundsRect.width() * ratio, cellHeight);
                scoreCell.draw();

                score /= 10;
            }
        }
    }


    /**
     * If appropriate, draw a message in the middle of the screen.
     */
    void drawMessages() {
        if (mGameStatusMessageNum != TextResources.NO_MESSAGE) {
            TexturedAlignedRect msgBox = mGameStatusMessages;

            Rect boundsRect = mTextRes.getTextureRect(mGameStatusMessageNum);
            msgBox.setTextureCoords(boundsRect);

            float scale = (Statics.ARENA_WIDTH * STATUS_MESSAGE_WIDTH_PERC) / boundsRect.width();
            msgBox.setScale(boundsRect.width() * scale, boundsRect.height() * scale);

            msgBox.draw();
        }
    }


    /**
     * Renders debug features.
     * <p>
     * This function is allowed to violate the "don't allocate objects" rule.
     */
    void drawDebugStuff() {
        if (!Statics.SHOW_DEBUG_STUFF) {
            return;
        }

        // Draw a red outline rectangle around the ball. This shows the area that was examined for collisions during the "coarse" pass.
        mDebugCollisionRect.draw();

        // Draw the entire message texture so we can see what it looks like.
        if (true) {
            int textureWidth = mTextRes.getTextureWidth();
            int textureHeight = mTextRes.getTextureHeight();
            float scale = (Statics.ARENA_WIDTH * STATUS_MESSAGE_WIDTH_PERC) / textureWidth;

            // Draw an orange rect around the texture.
            OutlineAlignedRect outline = new OutlineAlignedRect();

            outline.setPosition(Statics.ARENA_WIDTH / 2, Statics.ARENA_HEIGHT / 2);
            outline.setScale(textureWidth * scale + 2, textureHeight * scale + 2);
            outline.setColor(1.0f, 0.65f, 0.0f);
            outline.draw();

            // Draw the full texture. Note you can set the background to opaque white in TextResources to see what the drop shadow looks like.
            Rect boundsRect = new Rect(0, 0, textureWidth, textureHeight);

            TexturedAlignedRect msgBox = mGameStatusMessages;
            msgBox.setTextureCoords(boundsRect);
            msgBox.setScale(textureWidth * scale, textureHeight * scale);
            msgBox.draw();

            // Draw a rectangle around each individual text item. We draw a different one each time to get a flicker effect, so it doesn't fully obscure the text.
            if (true) {
                outline.setColor(1.0f, 1.0f, 1.0f);
                int stringNum = mDebugFramedString;
                mDebugFramedString = (mDebugFramedString + 1) % TextResources.getNumStrings();
                boundsRect = mTextRes.getTextureRect(stringNum);

                // The bounds rect is in bitmap coordinates, with (0,0) in the top left. Translate it to an offset from the center of the bitmap, and find the center of the rect.
                float boundsCenterX = boundsRect.exactCenterX() - (textureWidth / 2);
                float boundsCenterY = boundsRect.exactCenterY() - (textureHeight / 2);

                // Now scale it to arena coordinates, using the same scale factor we used to draw the texture with all the messages, and translate it to the center of the arena. We need to invert Y to match GL conventions.
                boundsCenterX = Statics.ARENA_WIDTH / 2 + (boundsCenterX * scale);
                boundsCenterY = Statics.ARENA_HEIGHT / 2 - (boundsCenterY * scale);

                // Set the values and draw the rect.
                outline.setPosition(boundsCenterX, boundsCenterY);
                outline.setScale(boundsRect.width() * scale, boundsRect.height() * scale);

                outline.draw();
            }
        }
    }


    /**
     * Moves the paddle to a new location. The requested position is expressed in arena coordinates, but does not need to be clamped to the viewable region.
     * <p>
     * The final position may be slightly different due to collisions with walls or side-contact with the ball.
     */
    void movePaddlePlayer1(float arenaX) {
		/*
		 * If we allow the paddle to be moved inside the ball (e.g. a quick sideways motion at a time when the ball is on the same horizontal line), the collision detection code may react badly. This can happen if we move the paddle without regard for the position of the ball. The problem is easy to
		 * demonstrate with a ball that has a large radius and a slow speed. If the paddle deeply intersects the ball, you either have to ignore the collision and let the ball pass through the paddle (which looks weird), or bounce off. When bouncing off we have to adjust the ball position so it no
		 * longer intersects with the paddle, which means a large jarring jump in position, or ignoring additional collisions, since they could cause the ball to reverse direction repeatedly (essentially just vibrating in place). We can handle this by running the paddle movement through the same
		 * collision detection code that the ball uses, and stopping it when we collide with something (the ball or walls). That would work well if the paddle were smoothly sliding, but our control scheme allows absolute jumps -- the paddle instantly goes wherever you touch on the screen. If the
		 * paddle were on the far right, and you touched the far left, you'd expect it to go to the far left even if the ball was "in the way" in the middle of the screen. The visual artifacts of making the ball leap are minor given the speed of animation and the size of objects on screen, so I'm
		 * currently just ignoring the problem. The moral of the story is that everything that moves needs to tested for collisions with all objects.
		 */

        float paddleWidth = mPaddlePlayer1.getXScale() / 2;
        final float minX = Statics.BORDER_WIDTH + paddleWidth;
        final float maxX = Statics.ARENA_WIDTH - Statics.BORDER_WIDTH - paddleWidth;

        if (arenaX < minX) {
            arenaX = minX;
        } else if (arenaX > maxX) {
            arenaX = maxX;
        }

        mPaddlePlayer1.setXPosition(arenaX);
    }


    /**
     * Moves the paddle to a new location. The requested position is expressed in arena coordinates, but does not need to be clamped to the viewable region.
     * <p>
     * The final position may be slightly different due to collisions with walls or side-contact with the ball.
     */
    void movePaddlePlayer2(float arenaX) {
        float paddleWidth = mPaddlePlayer2.getXScale() / 2;
        final float minX = Statics.BORDER_WIDTH + paddleWidth;
        final float maxX = Statics.ARENA_WIDTH - Statics.BORDER_WIDTH - paddleWidth;

        if (arenaX < minX) {
            arenaX = minX;
        } else if (arenaX > maxX) {
            arenaX = maxX;
        }

        if (mSinglePlayer) {
            final float finalArenaX = arenaX;
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    mPaddlePlayer2.setXPosition(finalArenaX);
                }
            }, mCpuDelay);

        } else {
            mPaddlePlayer2.setXPosition(arenaX);
        }

    }


    /**
     * Sets the pause time. The game will continue to execute and render, but won't advance game state. Used at the start of the game to give the user a chance to orient themselves to the board.
     * <p>
     * May also be handy during debugging to see stuff (like the ball at the instant of a collision) without fully stopping the game.
     */
    void setPauseTime(float durationMsec) {
        mPauseDuration = durationMsec;
    }


    /**
     * Updates all game state for the next frame. This primarily consists of moving the ball and checking for collisions.
     */
    void calculateNextFrame() {
        // First frame has no time delta, so make it a no-op.
        if (mPrevFrameWhenNsec == 0) {
            mPrevFrameWhenNsec = System.nanoTime(); // use monotonic clock
            mRecentTimeDeltaNext = -1; // reset saved values

            return;
        }

		/*
		 * The distance the ball must travel is determined by the time between frames and the current speed (expressed in arena-units per second). What we actually want to know is how much time will elapse between the *display* of the previous frame and the *display* of the current frame. Smoothing
		 * frames by averaging the last few deltas can reduce noticeable jumps, but create the possibility that you won't be animating at exactly the right speed. For our purposes it doesn't seem to matter. It's interesting to note that, because "deltaSec" varies, and our collision handling isn't
		 * perfectly precise, the game is not deterministic. Variations in frame rate lead to minor variations in the ball's path. If you want reproducible behavior for debugging, override deltaSec with a fixed value (e.g. 1/60).
		 */

        long nowNsec = System.nanoTime();
        double curDeltaSec = (nowNsec - mPrevFrameWhenNsec) / NANOS_PER_SECOND;

        if (curDeltaSec > MAX_FRAME_DELTA_SEC) {// We went to sleep for an extended period. Cap it at a reasonable limit.
            Log.d(TAG, "delta time was " + curDeltaSec + ", capping at " + MAX_FRAME_DELTA_SEC);
            curDeltaSec = MAX_FRAME_DELTA_SEC;
        }

        double deltaSec;

        if (FRAME_RATE_SMOOTHING) {
            if (mRecentTimeDeltaNext < 0) {// first time through, fill table with current value
                for (int i = 0; i < RECENT_TIME_DELTA_COUNT; i++) {
                    mRecentTimeDelta[i] = curDeltaSec;
                }

                mRecentTimeDeltaNext = 0;
            }

            mRecentTimeDelta[mRecentTimeDeltaNext] = curDeltaSec;
            mRecentTimeDeltaNext = (mRecentTimeDeltaNext + 1) % RECENT_TIME_DELTA_COUNT;

            deltaSec = 0.0f;

            for (int i = 0; i < RECENT_TIME_DELTA_COUNT; i++) {
                deltaSec += mRecentTimeDelta[i];
            }

            deltaSec /= RECENT_TIME_DELTA_COUNT;
        } else {
            deltaSec = curDeltaSec;
        }

        boolean advanceFrame = true;

        if (mPauseDuration > 0.0f) {// If we're in a pause, animate the color of the paddle, but don't advance any state.
            advanceFrame = false;

            if (mPauseDuration > deltaSec) {
                mPauseDuration -= deltaSec;

                if (mGamePlayState == Statics.GAME_PLAYING) {
                    float[] colors = mPaddlePlayer1.getColor();// rotate through yellow, magenta, cyan

                    if (colors[0] == 0.0f) {
                        mPaddlePlayer1.setColor(1.0f, 0.0f, 1.0f);
                        mPaddlePlayer2.setColor(1.0f, 0.0f, 1.0f);
                    } else if (colors[1] == 0.0f) {
                        mPaddlePlayer1.setColor(1.0f, 1.0f, 0.0f);
                        mPaddlePlayer2.setColor(1.0f, 1.0f, 0.0f);
                    } else {
                        mPaddlePlayer1.setColor(0.0f, 1.0f, 1.0f);
                        mPaddlePlayer2.setColor(0.0f, 1.0f, 1.0f);
                    }
                }
            } else { // leaving pause, restore paddle color to white
                mPauseDuration = 0.0f;
                mPaddlePlayer1.setColor(1.0f, 1.0f, 1.0f);
                mPaddlePlayer2.setColor(1.0f, 1.0f, 1.0f);
            }
        }

        if (mSinglePlayer) {
            switch (mGamePlayState) {// Do something appropriate based on our current state.
                case Statics.GAME_INITIALIZING:
                    mGamePlayState = Statics.GAME_READY;
                    break;

                case Statics.GAME_READY:
                    mGameStatusMessageNum = TextResources.READY;

                    if (advanceFrame) {// "ready" has expired, move ball to starting position
                        mGamePlayState = Statics.GAME_PLAYING;

                        mGameStatusMessageNum = TextResources.NO_MESSAGE;
                        setPauseTime(0.5f);
                        advanceFrame = false;
                    }
                    break;

                case Statics.GAME_PLAYING:
                    break;

                case Statics.GAME_WON:
                    mIsAnimating = false;
                    advanceFrame = false;
                    break;

                case Statics.GAME_LOST:
                    mGameStatusMessageNum = TextResources.GAME_OVER;

                    mIsAnimating = false;
                    advanceFrame = false;
                    break;

                default:
                    Log.e(TAG, "SINGLE PLAYER MODE, GLITCH: bad state " + mGamePlayState);
                    break;
            }
        } else {
            switch (mGamePlayState) {// Do something appropriate based on our current state.
                case Statics.GAME_INITIALIZING:
                    mGamePlayState = Statics.GAME_READY;
                    break;

                case Statics.GAME_READY:
                    mGameStatusMessageNum = TextResources.READY;

                    if (advanceFrame) {// "ready" has expired, move ball to starting position
                        mGamePlayState = Statics.GAME_PLAYING;

                        mGameStatusMessageNum = TextResources.NO_MESSAGE;
                        setPauseTime(0.5f);
                        advanceFrame = false;
                    }
                    break;

                case Statics.GAME_PLAYING:
                    break;

                case Statics.GAME_P1_WON:
                    mGameStatusMessageNum = TextResources.WINNER_P1;

                    mIsAnimating = false;
                    advanceFrame = false;
                    break;

                case Statics.GAME_P2_WON:
                    mGameStatusMessageNum = TextResources.WINNER_P2;

                    mIsAnimating = false;
                    advanceFrame = false;
                    break;

                default:
                    Log.e(TAG, "TWO PLAYER MODE, GLITCH: bad state " + mGamePlayState);
                    break;
            }
        }

        // If we're playing, move the ball around.
        if (advanceFrame) {
            int event = moveBall(deltaSec);

            if (mSinglePlayer) {
                switch (event) {
                    case Statics.EVENT_BALL_LOST_AT_P1_SIDE:
                        if (--mLivesPlayer1Remaining == 0) {
                            // game over, man
                            mGamePlayState = Statics.GAME_LOST;
                        } else {
                            // switch back to "ready" state, reset ball position
                            mGamePlayState = Statics.GAME_READY;
                            mGameStatusMessageNum = TextResources.READY;
                            setPauseTime(1.5f);
                            resetBall();
                        }
                        break;

                    case Statics.EVENT_BALL_LOST_AT_P2_SIDE:
                        Log.d(TAG, "Ball hit the top border. You get points !!!");
                        mScore += 10;
                        // switch back to "ready" state, reset ball position
                        mGamePlayState = Statics.GAME_READY;
                        mGameStatusMessageNum = TextResources.READY;
                        setPauseTime(1.5f);
                        resetBall();
                        break;

                    case Statics.EVENT_NONE:
                        break;

                    default:
                        throw new RuntimeException("bad game event: " + event);
                }
            } else {
                switch (event) {
                    case Statics.EVENT_BALL_LOST_AT_P1_SIDE:
                        if (--mLivesPlayer1Remaining == 0) {
                            mGamePlayState = Statics.GAME_P2_WON;// game over, man
                        } else {
                            // switch back to "ready" state, reset ball position
                            mGamePlayState = Statics.GAME_READY;
                            mGameStatusMessageNum = TextResources.READY;
                            setPauseTime(1.5f);
                            resetBall();
                        }
                        break;

                    case Statics.EVENT_BALL_LOST_AT_P2_SIDE:
                        if (--mLivesPlayer2Remaining == 0) {
                            mGamePlayState = Statics.GAME_P1_WON;// game over, man
                        } else {
                            // switch back to "ready" state, reset ball position
                            mGamePlayState = Statics.GAME_READY;
                            mGameStatusMessageNum = TextResources.READY;
                            setPauseTime(1.5f);
                            resetBall();
                        }
                        break;

                    case Statics.EVENT_NONE:
                        break;

                    default:
                        throw new RuntimeException("bad game event: " + event);
                }
            }
        }

        mPrevFrameWhenNsec = nowNsec;
    }


    /**
     * Moves the ball, checking for and reporting collisions as we go.
     *
     * @return A value indicating special events (won game, lost ball).
     */
    private int moveBall(double deltaSec) {
		/*
		 * Movement and collision detection is done with two checks, "coarse" and "fine". First, we take the current position of the ball, and compute where it will be for the next frame. We compute a box that encloses both the current and next positions (an "axis-aligned bounding box", or AABB).
		 * For every object in the list, including the borders and paddle, we do quick test for a collision. If nothing matches, we just jump the ball forward. If we do get some matches, we need to do a finer-grained test to see if (a) we actually hit something, and (b) how far along the ball's path
		 * we were when we first collided. If we did hit something, we need to update the ball's motion vector based on which edge or corner we hit, and restart the whole process from the point of the collision. The ball is now moving in a different direction, so the "coarse" information we gathered
		 * previously is no longer valid.
		 */

        int event = Statics.EVENT_NONE;

        float radius = mBall.getRadius();
        float distance = (float) (mBall.getSpeed() * deltaSec);

        while (distance > 0.0f) {
            float curX = mBall.getXPosition();
            float curY = mBall.getYPosition();
            float dirX = mBall.getXDirection();
            float dirY = mBall.getYDirection();
            float finalX = curX + dirX * distance;
            float finalY = curY + dirY * distance;
            float left, right, top, bottom;

            // Find the edges of the rectangle described by the ball's start and end position. The (x,y) values identify the center, so factor in the radius too.
            if (curX < finalX) {
                left = curX - radius;
                right = finalX + radius;
            } else {
                left = finalX - radius;
                right = curX + radius;
            }
            if (curY < finalY) {
                bottom = curY - radius;
                top = finalY + radius;
            } else {
                bottom = finalY - radius;
                top = curY + radius;
            }

			/* debug */
            mDebugCollisionRect.setPosition((curX + finalX) / 2, (curY + finalY) / 2);
            mDebugCollisionRect.setScale(right - left, top - bottom);

            int hits = 0;

            // test borders
            for (int i = 0; i < gameArenaBorders.NUM_BORDERS; i++) {
                if (checkCoarseCollision(gameArenaBorders.mBorders[i], left, right, bottom, top)) {
                    mPossibleCollisions[hits++] = gameArenaBorders.mBorders[i];
                }
            }

            // test paddle for player 1
            if (checkCoarseCollision(mPaddlePlayer1, left, right, bottom, top)) {
                mPossibleCollisions[hits++] = mPaddlePlayer1;
            }

            // test paddle for player 2
            if (checkCoarseCollision(mPaddlePlayer2, left, right, bottom, top)) {
                mPossibleCollisions[hits++] = mPaddlePlayer2;
            }

            if (hits != 0) {
                BaseRect hit = findFirstCollision(mPossibleCollisions, hits, curX, curY, dirX, dirY, distance, radius);// may have hit something, look closer

                if (hit == null) {
                    hits = 0;// didn't actually hit, clear counter
                } else {
                    if (GameSurfaceRenderer.EXTRA_CHECK) {
                        if (mHitDistanceTraveled <= 0.0f) {
                            Log.e(TAG, "GLITCH: collision detection didn't move the ball");
                            mHitDistanceTraveled = distance;
                        }
                    }

                    // Update position for the actual distance traveled and the collision adjustment
                    float newPosX = curX + dirX * mHitDistanceTraveled + mHitXAdj;
                    float newPosY = curY + dirY * mHitDistanceTraveled + mHitYAdj;
                    mBall.setPosition(newPosX, newPosY);

                    // Update the direction vector based on the nature of the surface we struck. We will override this for collisions with the paddle.
                    float newDirX = dirX;
                    float newDirY = dirY;

                    switch (mHitFace) {
                        case Statics.HIT_FACE_HORIZONTAL:
                            newDirY = -dirY;
                            break;

                        case Statics.HIT_FACE_VERTICAL:
                            newDirX = -dirX;
                            break;

                        case Statics.HIT_FACE_SHARPCORNER:
                            newDirX = -dirX;
                            newDirY = -dirY;
                            break;

                        case Statics.HIT_FACE_NONE:
                        default:
                            Log.e(TAG, "GLITCH: unexpected hit face" + mHitFace);
                            break;
                    }

                    // Figure out what we hit, and react. A conceptually cleaner way to do this would be to define a "collision" action on every BaseRect object, and call that.
                    if (hit == mPaddlePlayer1) {
                        if (mHitFace == Statics.HIT_FACE_HORIZONTAL) {
                            float paddleWidth = mPaddlePlayer1.getXScale();
                            float paddleLeft = mPaddlePlayer1.getXPosition() - paddleWidth / 2;
                            float hitAdjust = (newPosX - paddleLeft) / paddleWidth;

                            // Adjust the ball's motion based on where it hit the paddle.
                            // The hitPosn ranges from 0.0 to 1.0, with a little bit of overlap because the ball is round (it's based on the ball's *center*, not the actual point of impact on the paddle itself)
                            // The location determines how we alter the X velocity. We want this to be more pronounced at the edges of the paddle, especially if the ball is hitting the "outside edge".
                            // Direction is a vector, normalized by the "set direction" method. We don't need to worry about dirX growing without bound.
                            // This bit of code has a substantial impact on the "feel" of the game. It could probably use more tweaking.
                            if (hitAdjust < 0.0f) {
                                hitAdjust = 0.0f;
                            }

                            if (hitAdjust > 1.0f) {
                                hitAdjust = 1.0f;
                            }

                            hitAdjust -= 0.5f;

                            if (Math.abs(hitAdjust) > 0.25) { // outer 25% on each side
                                if (dirX < 0 && hitAdjust > 0 || dirX > 0 && hitAdjust < 0) {
                                    hitAdjust *= 1.6;
                                } else {
                                    hitAdjust *= 1.2;
                                }
                            }

                            hitAdjust *= 1.25;
                            newDirX += hitAdjust;
                            float maxRatio = 3.0f;
                            if (Math.abs(newDirX) > Math.abs(newDirY) * maxRatio) {
                                // Limit the angle so we don't get too crazily horizontal.
                                if (newDirY < 0) {
                                    maxRatio = -maxRatio;
                                }
                                newDirY = Math.abs(newDirX) / maxRatio;
                            }
                        }

                    } else if (hit == mPaddlePlayer2) {
                        if (mHitFace == Statics.HIT_FACE_HORIZONTAL) {
                            float paddleWidth = mPaddlePlayer2.getXScale();
                            float paddleLeft = mPaddlePlayer2.getXPosition() - paddleWidth / 2;
                            float hitAdjust = (newPosX - paddleLeft) / paddleWidth;

                            // Adjust the ball's motion based on where it hit the paddle.
                            // The hitPosn ranges from 0.0 to 1.0, with a little bit of overlap because the ball is round (it's based on the ball's *center*, not the actual point of impact on the paddle itself)
                            // The location determines how we alter the X velocity. We want this to be more pronounced at the edges of the paddle, especially if the ball is hitting the "outside edge".
                            // Direction is a vector, normalized by the "set direction" method. We don't need to worry about dirX growing without bound.
                            // This bit of code has a substantial impact on the "feel" of the game. It could probably use more tweaking.
                            if (hitAdjust < 0.0f) {
                                hitAdjust = 0.0f;
                            }

                            if (hitAdjust > 1.0f) {
                                hitAdjust = 1.0f;
                            }

                            hitAdjust -= 0.5f;

                            if (Math.abs(hitAdjust) > 0.25) { // outer 25% on each side
                                if (dirX < 0 && hitAdjust > 0 || dirX > 0 && hitAdjust < 0) {
                                    hitAdjust *= 1.6;
                                } else {
                                    hitAdjust *= 1.2;
                                }
                            }
                            hitAdjust *= 1.25;
                            newDirX += hitAdjust;
                            float maxRatio = 3.0f;
                            if (Math.abs(newDirX) > Math.abs(newDirY) * maxRatio) {
                                // Limit the angle so we don't get too crazily horizontal.
                                if (newDirY < 0) {
                                    maxRatio = -maxRatio;
                                }

                                newDirY = Math.abs(newDirX) / maxRatio;
                            }
                        }
                    } else if (hit == gameArenaBorders.mBorders[gameArenaBorders.BOTTOM_BORDER]) {// We hit the bottom border.
                        Log.d(TAG, "ball hit the bottom border");
                        event = Statics.EVENT_BALL_LOST_AT_P1_SIDE;
                        distance = 0.0f;
                    } else if (hit == gameArenaBorders.mBorders[gameArenaBorders.UPPER_BORDER]) {// We hit the upper border.
                        Log.d(TAG, "ball hit the upper border");
                        event = Statics.EVENT_BALL_LOST_AT_P2_SIDE;
                        distance = 0.0f;
                    } else {
                        // hit a border or a score digit
                    }

                    // Increase speed by 3% after each (super-elastic!) collision, capping at the skill-level-dependent maximum speed.
                    int speed = mBall.getSpeed();
                    speed += (mBallMaximumSpeed - mBallInitialSpeed) * 3 / 100;
                    if (speed > mBallMaximumSpeed) {
                        speed = mBallMaximumSpeed;
                    }
                    mBall.setSpeed(speed);

                    mBall.setDirection(newDirX, newDirY);
                    distance -= mHitDistanceTraveled;
                }
            }

            if (hits == 0) {// hit nothing, move ball to final position and bail
                mBall.setPosition(finalX, finalY);
                distance = 0.0f;
            }
        }

        return event;
    }


    /**
     * Determines whether the target object could possibly collide with a ball whose current and future position are enclosed by the l/r/b/t values.
     *
     * @return true if we might collide with this object.
     */
    private boolean checkCoarseCollision(BaseRect target, float left, float right, float bottom, float top) {
		/*
		 * This is a "coarse" detection, so we can play fast and loose. One approach is to essentially draw a circle around each object, and see if the circles intersect. This requires a simple distance test -- if the distance between the center points of the objects is greater than their combined
		 * radii, there's no chance of collision. Mathematically, each test is two multiplications and a compare. check in the "fine" pass to a handful.
		 */

        // Convert position+scale into l/r/b/t.
        float xpos, ypos, xscale, yscale;
        float targLeft, targRight, targBottom, targTop;

        xpos = target.getXPosition();
        ypos = target.getYPosition();
        xscale = target.getXScale();
        yscale = target.getYScale();
        targLeft = xpos - xscale;
        targRight = xpos + xscale;
        targBottom = ypos - yscale;
        targTop = ypos + yscale;

        // If the smallest right is bigger than the biggest left, and the smallest bottom is bigger than the biggest top, we overlap.
        // FWIW, this is essentially an application of the Separating Axis Theorem for two axis-aligned rects.
        float checkLeft = targLeft > left ? targLeft : left;
        float checkRight = targRight < right ? targRight : right;
        float checkTop = targBottom > bottom ? targBottom : bottom;
        float checkBottom = targTop < top ? targTop : top;

        if (checkRight > checkLeft && checkBottom > checkTop) {
            return true;
        }

        return false;
    }


    /**
     * Tests for a collision with the rectangles in mPossibleCollisions as the ball travels from (curX,curY).
     * <p>
     * We can't return multiple values from a method call in Java. We don't want to allocatestorage for the return value on each frame (this being part of the main game loop). We can define a class that holds all of the return values and allocate a single instance of it when GameState is
     * constructed, or just drop the values into dedicated return-value fields. The latter is incrementally easier, so we return the object we hit, and store additional details in these fields:
     * <ul>
     * <li>mHitDistanceLeft - the amount of distance remaining to travel after impact
     * <li>mHitFace - what face orientation we hit
     * <li>mHitXAdj, mHitYAdj - position adjustment so objects won't intersect
     * </ul>
     *
     * @param rects    Array of rects to test against.
     * @param numRects Number of rects in array.
     * @param curX     Current X position.
     * @param curY     Current Y position.
     * @param dirX     X component of normalized direction vector.
     * @param dirY     Y component of normalized direction vector.
     * @param distance Distance to travel.
     * @param radius   Radius of the ball.
     * @return The object we struck, or null if none.
     */
    @SuppressWarnings("unused")
    private BaseRect findFirstCollision(BaseRect[] rects, final int numRects, final float curX, final float curY, final float dirX, final float dirY, final float distance, final float radius) {
        // The "coarse" function has indicated that a collision is possible. We need to get an exact determination of what we're hitting.

        // Maximum distance, in arena coordinates, we advance the ball on each iteration of the loop. If this is too small, we'll do a lot of unnecessary iterations.
        // If it's too large (e.g. more than the ball's radius), the ball can end up inside an object, or pass through one entirely.
        final float MAX_STEP = 2.0f;

        // Minimum distance. After a collision the objects are just barely in contact, so at each step we need to move a little or we'll double-collide.
        // The minimum exists to ensure that we don't get hosed by floating point round-off error.
        final float MIN_STEP = 0.001f;

        float radiusSq = radius * radius;
        int faceHit = Statics.HIT_FACE_NONE;
        int faceToAdjust = Statics.HIT_FACE_NONE;
        float xadj = 0.0f;
        float yadj = 0.0f;
        float traveled = 0.0f;

        while (traveled < distance) {
            // Travel a bit.
            if (distance - traveled > MAX_STEP) {
                traveled += MAX_STEP;
            } else if (distance - traveled < MIN_STEP) {
                break;
            } else {
                traveled = distance;
            }
            float circleXWorld = curX + dirX * traveled;
            float circleYWorld = curY + dirY * traveled;

            for (int i = 0; i < numRects; i++) {
                BaseRect rect = rects[i];
                float rectXWorld = rect.getXPosition();
                float rectYWorld = rect.getYPosition();
                float rectXScaleHalf = rect.getXScale() / 2.0f;
                float rectYScaleHalf = rect.getYScale() / 2.0f;

                // Translate the circle so that it's in the first quadrant, with the center of the rectangle at (0,0).
                float circleX = Math.abs(circleXWorld - rectXWorld);
                float circleY = Math.abs(circleYWorld - rectYWorld);

                if (circleX > rectXScaleHalf + radius || circleY > rectYScaleHalf + radius) {
                    // Circle is too far from rect edge(s) to overlap. No collision.
                    continue;
                }

				/*
				 * Check to see if the center of the circle is inside the rect on one axis. The previous test eliminated anything that was too far on either axis, so if this passes then we must have a collision. We're not moving the ball fast enough (limited by MAX_STEP) to get the center of the
				 * ball completely inside the rect (i.e. we shouldn't see a case where the center is inside the rect on *both* axes), so if we're inside in the X axis we can conclude that we just collided due to vertical motion, and have hit a horizontal surface. If the center isn't inside on either
				 * axis, we've hit the corner case, and need to do a distance test.
				 */
                if (circleX <= rectXScaleHalf) {
                    faceToAdjust = faceHit = Statics.HIT_FACE_HORIZONTAL;
                } else if (circleY <= rectYScaleHalf) {
                    faceToAdjust = faceHit = Statics.HIT_FACE_VERTICAL;
                } else {
                    // Check the distance from rect corner to center of circle.
                    float xdist = circleX - rectXScaleHalf;
                    float ydist = circleY - rectYScaleHalf;
                    if (xdist * xdist + ydist * ydist > radiusSq) {
                        // Not close enough.
                        continue;
                    }

                    float dirXSign = Math.signum(dirX);
                    float dirYSign = Math.signum(dirY);
                    float cornerXSign = Math.signum(rectXWorld - circleXWorld);
                    float cornerYSign = Math.signum(rectYWorld - circleYWorld);

                    String msg;
                    if (dirXSign == cornerXSign && dirYSign == cornerYSign) {
                        faceHit = Statics.HIT_FACE_SHARPCORNER;
                        msg = "sharp";
                    } else if (dirXSign == cornerXSign) {
                        faceHit = Statics.HIT_FACE_VERTICAL;
                        msg = "vert";
                    } else if (dirYSign == cornerYSign) {
                        faceHit = Statics.HIT_FACE_HORIZONTAL;
                        msg = "horiz";
                    } else {
                        Log.w(TAG, "COL: impossible corner hit");
                        faceHit = Statics.HIT_FACE_SHARPCORNER;
                        msg = "???";
                    }

                    // Adjust whichever requires the least movement to guarantee we're no longer colliding.
                    if (xdist < ydist) {
                        faceToAdjust = Statics.HIT_FACE_HORIZONTAL;
                    } else {
                        faceToAdjust = Statics.HIT_FACE_VERTICAL;
                    }
                }

				/*
				 * Collision! Because we're moving in discrete steps rather than continuously, we will usually end up slightly embedded in the object. If, after reversing direction, we subsequently step forward very slightly (assuming a non-destructable object like a wall), we will detect a second
				 * collision with the same object, and reverse direction back *into* the wall. Visually, the ball will "stick" to the wall and vibrate. We need to back the ball out slightly. Ideally we'd back it along the path the ball was traveling by just the right amount, but unless MAX_STEP is
				 * really large the difference between that and a minimum-distance axis-aligned shift is negligible -- and this is easier to compute. There's some risk that our adjustment will leave the ball trapped in a different object. Since the ball is the only object that's moving, and the
				 * direction of adjustment shouldn't be too far from the angle of incidence, we shouldn't have this problem in practice. Note this leaves the ball just *barely* in contact with the object it hit, which means it's technically still colliding. This won't cause us to collide again and
				 * reverse course back into the object because we will move the ball a nonzero distance away from the object before we check for another collision. The use of MIN_STEP ensures that we won't fall victim to floating point round-off error. (If we didn't want to guarantee movement, we
				 * could shift the ball a tiny bit farther so that it simply wasn't in contact.)
				 */
                float hitXAdj, hitYAdj;
                if (faceToAdjust == Statics.HIT_FACE_HORIZONTAL) {
                    hitXAdj = 0.0f;
                    hitYAdj = rectYScaleHalf + radius - circleY;

                    if (circleYWorld < rectYWorld) {
                        hitYAdj = -hitYAdj;// ball is below rect, must be moving up, so adjust it down
                    }
                } else if (faceToAdjust == Statics.HIT_FACE_VERTICAL) {
                    hitXAdj = rectXScaleHalf + radius - circleX;
                    hitYAdj = 0.0f;

                    if (circleXWorld < rectXWorld) {
                        hitXAdj = -hitXAdj;// ball is left of rect, must be moving to right, so adjust it left
                    }
                } else {
                    hitXAdj = hitYAdj = 0.0f;
                }

                mHitFace = faceHit;
                mHitDistanceTraveled = traveled;
                mHitXAdj = hitXAdj;
                mHitYAdj = hitYAdj;

                return rect;
            }
        }

        return null;
    }


    /**
     * Game state storage. Anything interesting gets copied in here. If we wanted to save it to disk we could just serialize the object.
     * <p>
     * This is "organized" as a dumping ground for GameState to use.
     */
    private static class SavedGame {
        public float mBallXDirection, mBallYDirection;
        public float mBallXPosition, mBallYPosition;
        public int mBallSpeed;
        public float mPaddlePlayer1Position;
        public float mPaddlePlayer2Position;
        public int mGamePlayState;
        public int mGameStatusMessageNum;
        public int mLivesPlayer1Remaining;
        public int mLivesPlayer2Remaining;
        public int mScore;

        public boolean mIsValid = false;                // set when state has been written out
    }

}
