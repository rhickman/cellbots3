package ai.cellbots.arcompanion.dialog;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.zagum.speechrecognitionview.RecognitionProgressView;
import com.github.zagum.speechrecognitionview.adapters.RecognitionListenerAdapter;

import java.util.ArrayList;

import ai.cellbots.arcompanion.R;

/**
 * A DialogFragment class for adding a new PointOfInterest via Text/Voice commands.
 * Instantiates when the user long clicks on the screen while in AR view.
 *
 * Reference: https://guides.codepath.com/android/Using-DialogFragment
 *
 * Created by playerthree on 9/18/17.
 */

public class AddPOIDialogFragment extends DialogFragment implements View.OnClickListener {

    private static final String TAG = AddPOIDialogFragment.class.getSimpleName();

    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 0;
    private static final long RUNNABLE_TIME_DELAY_IN_MILLIS = 50;

    // Listener that passes the user's voice-to-text speech result back to MainActivity for
    // processing into a new POI and sending to the cloud.
    private OnVoiceResultListener listener;

    // EditText view for inputting the name of the new point of interest.
    private EditText mPOINameEditText;

    // Google Assistant-like progress view and animation that plays when recording voice.
    private RecognitionProgressView mVoiceRecognitionProgressView;

    // Floating Action Button for activating the voice feature (record speech).
    private FloatingActionButton mVoiceFAB;

    // Button for confirming the new POI's name, sending the String back to MainActivity, and
    // dismissing the dialog.
    private Button mConfirmButton;

    // A library object that provides access to Android's speech recognition service.
    // Source: https://github.com/zagum/SpeechRecognitionView
    private SpeechRecognizer mSpeechRecognizer;

    public AddPOIDialogFragment() {
        // Empty constructor is required for DialogFragment
        // Make sure not to add arguments to the constructor
        // Use `newInstance` instead as shown below.
    }

    public static AddPOIDialogFragment newInstance() {
        return new AddPOIDialogFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnVoiceResultListener) {
            listener = (OnVoiceResultListener) context;
        } else {
            throw new ClassCastException(context.toString() +
                    " must implement AddPOIDialogFragment.OnVoiceResultListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_fragment_add_poi, container, false);

        mPOINameEditText = view.findViewById(R.id.name_edittext);
        mVoiceRecognitionProgressView = view.findViewById(R.id.voice_recognition_progress_view);
        mVoiceFAB = view.findViewById(R.id.voice_fab);
        mConfirmButton = view.findViewById(R.id.confirm_button);

        mVoiceFAB.setOnClickListener(this);
        mConfirmButton.setOnClickListener(this);

        int[] googleColors = {
                ContextCompat.getColor(getActivity(), R.color.google_blue),
                ContextCompat.getColor(getActivity(), R.color.google_red),
                ContextCompat.getColor(getActivity(), R.color.google_yellow),
                ContextCompat.getColor(getActivity(), R.color.google_green),
                ContextCompat.getColor(getActivity(), R.color.google_blue)
        };
        mVoiceRecognitionProgressView.setColors(googleColors);

        int[] heights = {60, 76, 58, 80, 55};
        mVoiceRecognitionProgressView.setBarMaxHeightsInDp(heights);
        mVoiceRecognitionProgressView.setIdleStateAmplitudeInDp(5);
        mVoiceRecognitionProgressView.setRotationRadiusInDp(30);
        mVoiceRecognitionProgressView.setSpeechRecognizer(mSpeechRecognizer);
        mVoiceRecognitionProgressView.setRecognitionListener(new RecognitionListenerAdapter() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && matches.size() > 0) {
                    String poiName = matches.get(0);
                    mPOINameEditText.setText(poiName);
                } else {
                    Toast.makeText(getActivity(), "The speech recognizer could not understand " +
                            "your command. Please try again.", Toast.LENGTH_SHORT).show();
                }

            }
        });
        mVoiceRecognitionProgressView.play();

        return view;
    }

    @Override
    public void onDestroy() {
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.voice_fab:
                if (ContextCompat.checkSelfPermission(getActivity(),
                        android.Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionToRecordAudio();
                } else {
                    mVoiceRecognitionProgressView.play();
                    recordSpeech();
                    // Hack posted by the library author for correctly initiating the voice recognition
                    // feature every time it's called.
                    mVoiceRecognitionProgressView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            recordSpeech();
                        }
                    }, RUNNABLE_TIME_DELAY_IN_MILLIS);
                }
                break;
            case R.id.confirm_button:
                Editable text = mPOINameEditText.getText();

                // If POI name is null/empty, reject the name and show an error.
                if (text == null || TextUtils.isEmpty(text.toString())) {
                    mPOINameEditText.setError(getString(R.string.error_poi_name_cannot_be_empty));
                    mPOINameEditText.requestFocus();
                    break;
                }

                // Retrieve latest POI name from mPOINameEditText and send back to MainActivity
                String poiName = text.toString();
                Log.i(TAG, "POI Name: " + poiName);
                listener.onPOINameResult(poiName); // Pass POI Name back to MainActivity
                dismiss(); // Close dialog
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getActivity(), R.string.permission_record_audio_success,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), R.string.permission_record_audio_failed,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestPermissionToRecordAudio() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                android.Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(getActivity(), "Requires RECORD_AUDIO permission", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    PERMISSION_REQUEST_RECORD_AUDIO
            );
        }
    }

    // Create an intent that starts the Speech Recognizer activity.
    // This will translate the user's speech into text.

    /**
     * Creates an intent that starts the Speech Recognizer activity for translating user's speech
     * into a usable String text.
     */
    private void recordSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getActivity().getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizer.startListening(intent);
    }

    public interface OnVoiceResultListener {
        void onPOINameResult(String poiName);
    }
}
