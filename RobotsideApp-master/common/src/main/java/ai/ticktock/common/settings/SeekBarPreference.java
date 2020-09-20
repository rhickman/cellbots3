package ai.cellbots.common.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Class that implements a SeekBar (slider) on the Settings Preferences
 */

public class SeekBarPreference extends DialogPreference
        implements SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    final static private String TAG = SeekBarPreference.class.getSimpleName();

    // Private attributes
    private static final String androidNs ="http://schemas.android.com/apk/res/android";

    private SeekBar mSeekBar;
    private TextView mValueText;
    private Context mContext;

    private String mDialogMessage;
    private String mSummaryMessage;
    private String mSuffix;
    private int mDefault;
    private int mMax;
    private int mValue = 50;

    // Constructor
    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        // Get string value for dialogMessage
        int mDialogMessageId = attrs.getAttributeResourceValue(androidNs, "dialogMessage", 0);
        if(mDialogMessageId == 0) mDialogMessage = attrs.getAttributeValue(androidNs,
                "dialogMessage");
        else mDialogMessage = mContext.getString(mDialogMessageId);

        int mSummaryMessageId = attrs.getAttributeResourceValue(androidNs, "summary", 0);
        if (mSummaryMessageId == 0) mSummaryMessage = attrs.getAttributeValue(androidNs,
                "summary");
        else mSummaryMessage = mContext.getString(mSummaryMessageId);

        // Get string value for suffix (text attribute in xml file)
        int mSuffixId = attrs.getAttributeResourceValue(androidNs, "text", 0);
        if(mSuffixId == 0) mSuffix = attrs.getAttributeValue(androidNs, "text");
        else mSuffix = mContext.getString(mSuffixId);

        // Get default and max seekBar values
        mDefault = attrs.getAttributeIntValue(androidNs, "defaultValue", 50);
        mMax = attrs.getAttributeIntValue(androidNs, "max", 100);
    }

    /**
     * Creates the new seekBar dialog
     * @return the view
     */
    @Override
    protected View onCreateDialogView() {
        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6,6,6,6);


        TextView mSplashText = new TextView(mContext);
        mSplashText.setPadding(30, 10, 30, 10);
        if (mDialogMessage != null)
            mSplashText.setText(mDialogMessage);
        layout.addView(mSplashText);

        mValueText = new TextView(mContext);
        mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
        mValueText.setTextSize(32);
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(mValueText, params);

        mSeekBar = new SeekBar(mContext);
        mSeekBar.setOnSeekBarChangeListener(this);
        layout.addView(mSeekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (shouldPersist())
            mValue = getPersistedInt(mDefault);

        mSeekBar.setMax(mMax);
        mSeekBar.setProgress(mValue);

        return layout;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mSeekBar.setMax(mMax);
        mSeekBar.setProgress(mValue);

        String t = String.valueOf(mValue);
        mValueText.setText(mSuffix == null? t : t.concat(" " + mSuffix));
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        super.onSetInitialValue(restorePersistedValue, defaultValue);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        Button positiveButton = ((AlertDialog)getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (shouldPersist()) {
            mValue = mSeekBar.getProgress();
            persistInt(mSeekBar.getProgress());
            callChangeListener(mSeekBar.getProgress());
            setSummary(mSummaryMessage + mSeekBar.getProgress() + mSuffix);
        }
        getDialog().dismiss();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // Change the progress text on the dialog
        String t = String.valueOf(progress);
        mValueText.setText(mSuffix == null? t : t.concat(" " + mSuffix));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}

    // Defined functions

    public void setMax(int max) { mMax = max; }
    public int getMax() { return mMax; }

    public void setProgress(int progress) {
        mValue = progress;
        if (mSeekBar != null)
            mSeekBar.setProgress(progress);
    }
    public int getProgress() { return mValue; }

    /**
     * When the Settings screen is created, this function is called
     * @param value Initial value (default)
     */
    public void setDefaults(int value) {
        if (shouldPersist()) {
            mDefault = value;
            mValue = value;
            persistInt(mValue);

            setSummary(mSummaryMessage + value + mSuffix);
            setProgress(value);

            callChangeListener(mValue);
        }
    }

}
