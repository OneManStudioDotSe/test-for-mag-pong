package se.onemanstudio.pong;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;


/**
 * Initial activity. Presents some menu items.
 */
public class PongActivity extends Activity {
    public static final String TAG = "PongActivity";

    public static final String PREFS_NAME = "PrefsAndScores";

    // Keys for values saved in our preferences file.
    private static final String DIFFICULTY_KEY = "difficulty";
    private static final String SINGLE_PLAYER_MODE = "single-player-mode";
    public static final String HIGH_SCORE_KEY = "high-score";

    private int mHighScore;

    private RadioGroup mPlayermodeRadioGroup;
    private Spinner mDifficultySpinner;
    private TextView mHighScoreText;
    private Button mStartGame;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "onCreate");

        findViews();

        setupDifficultySpinner();

        setActions();
    }


    @Override
    public void onPause() {
        super.onPause();

        savePreferences();
    }


    @Override
    public void onResume() {
        super.onResume();

        restorePreferences();
        restoreControls();
    }


    private void findViews() {
        mDifficultySpinner = (Spinner) findViewById(R.id.spinner_difficultyLevel);
        mPlayermodeRadioGroup = (RadioGroup) findViewById(R.id.rbPlayersMode);
        mHighScoreText = (TextView) findViewById(R.id.text_highScore);
        mStartGame = (Button) findViewById(R.id.button_newGame);
    }


    private void setActions() {
        mStartGame.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getPlayerMode();

                savePreferences();

                Intent intent = new Intent(PongActivity.this, GameActivity.class);
                startActivity(intent);
            }
        });
    }


    private void getPlayerMode() {

        int radioButtonID = mPlayermodeRadioGroup.getCheckedRadioButtonId();
        View radioButton = mPlayermodeRadioGroup.findViewById(radioButtonID);
        int idx = mPlayermodeRadioGroup.indexOfChild(radioButton);

        Log.d(TAG, "idx: " + idx);

        if (idx == 0) {
            GameActivity.setSinglePlayer(true);
        } else {
            GameActivity.setSinglePlayer(false);
        }
    }


    private void setupDifficultySpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.difficulty_level_names, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mDifficultySpinner.setAdapter(adapter);

        mDifficultySpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Spinner spinner = (Spinner) parent;
                int difficulty = spinner.getSelectedItemPosition();

                GameActivity.setDifficultyIndex(difficulty);
            }


            @Override
            public void onNothingSelected(AdapterView<?> arg0) {

            }
        });
    }


    /**
     * Copies settings to the saved preferences.
     */
    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt(DIFFICULTY_KEY, GameActivity.getDifficultyIndex());
        editor.putBoolean(SINGLE_PLAYER_MODE, GameActivity.getSinglePlayer());
        editor.commit();
    }


    /**
     * Retrieves settings from the saved preferences. Also picks up the high score.
     */
    private void restorePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        GameActivity.setDifficultyIndex(prefs.getInt(DIFFICULTY_KEY, GameActivity.getDefaultDifficultyIndex()));
        GameActivity.setSinglePlayer(prefs.getBoolean(SINGLE_PLAYER_MODE, false));

        mHighScore = prefs.getInt(HIGH_SCORE_KEY, 0);
    }


    private void restoreControls() {
        mDifficultySpinner.setSelection(GameActivity.getDifficultyIndex());
        mHighScoreText.setText(String.valueOf(mHighScore));

        if (GameActivity.getSinglePlayer() == true) {
            View radioButton = mPlayermodeRadioGroup.findViewById(R.id.singlePlayer);
            radioButton.setSelected(true);
        } else {
            View radioButton = mPlayermodeRadioGroup.findViewById(R.id.twoPlayers);
            radioButton.setSelected(true);
        }
    }

}
