package ai.cellbots.common.voice;

/**
 * Listener that is used to send {@link VoiceCommands.VoiceAction} messages to
 * the Activity that is using the {@link VoiceCommands} class.
 */
public interface OnVoiceDetectedListener {
    void onVoiceActionDetected(VoiceCommands.VoiceAction actionDetected);
}
