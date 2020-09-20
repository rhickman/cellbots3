package ai.cellbots.robotlib;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * An animation for the robot.
 */
public class Animation {
    private static final String TAG = Animation.class.getSimpleName();

    private final String mName;
    private final List<AnimationCommand> mAnimationCommands;

    public static class AnimationCommand {
    }
    public static class SetMotorAnimationCommand extends AnimationCommand {
        private final double mLinear;
        private final double mAngular;
        public double getLinear() {
            return mLinear;
        }
        public double getAngular() {
            return mAngular;
        }
        private SetMotorAnimationCommand(double linear, double angular) {
            mLinear = linear;
            mAngular = angular;
        }
    }
    public static class WaitAnimationCommand extends AnimationCommand {
        private final long mTime;
        public long getTime() {
            return mTime;
        }
        private WaitAnimationCommand(long time) {
            mTime = time;
        }
    }
    public static class PlayAudioAnimationCommand extends AnimationCommand {
        private final String mName;
        public String getName() {
            return mName;
        }
        private PlayAudioAnimationCommand(String name) {
            mName = name;
        }
    }

    public String getName() {
        return mName;
    }

    public List<AnimationCommand> getAnimationCommands() {
        return Collections.unmodifiableList(mAnimationCommands);
    }

    public Animation(String name, List<AnimationCommand> animationCommands) {
        mName = name;
        mAnimationCommands = Collections.unmodifiableList(new ArrayList<>(animationCommands));
    }

    public static Animation readAnimation(String name, String content) {
        // TODO, strings that contain semis are going to be split here
        String[] lines = content.split(";");
        LinkedList<AnimationCommand> commands = new LinkedList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.equals("")) {
                continue;
            }

            if (!line.contains("(")) {
                Log.e(TAG, "Animation " + name + " bad line no '(':" + line);
                return null;
            }
            if (!line.substring(line.length() - 1).equals(")")) {
                Log.e(TAG, "Animation " + name + " bad line does not end with ')':" + line);
                return null;
            }

            String function = line.substring(0, line.indexOf('(')).trim();

            // TODO: this will fail with strings that have ','
            String[] params = line.substring(line.indexOf('(') + 1, line.length() - 1).split(",");
            for (int i = 0; i < params.length; i++) {
                params[i] = params[i].trim();
            }

            if (function.equals("SetMotor")) {
                if (params.length != 2) {
                    Log.e(TAG, "Wrong number of params to SetMotor: " + line + " in " + name);
                    break;
                }
                try {
                    commands.add(new SetMotorAnimationCommand(Double.valueOf(params[0]),
                            Double.valueOf(params[1])));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse double in SetMotor: " + line + " in " + name);
                    return null;
                }
            } else if (function.equals("Wait")) {
                if (params.length != 1) {
                    Log.e(TAG, "Wrong number of params to Wait: " + line + " in " + name);
                    break;
                }
                try {
                    commands.add(new WaitAnimationCommand(Long.valueOf(params[0])));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse long in Wait: " + line + " in " + name);
                    return null;
                }
            } else if (function.equals("PlayAudio")) {
                if (params.length != 1) {
                    Log.e(TAG, "Wrong number of params to PlayAudio: " + line + " in " + name);
                    break;
                }
                commands.add(new PlayAudioAnimationCommand(params[0]));
            } else {
                Log.e(TAG, "Invalid command " + function + " in animation " + name);
                return null;
            }
        }

        return new Animation(name, commands);
    }

    public static List<Animation> readAnimationFile(String file) {
        int ch = 0;
        LinkedList<Animation> animations = new LinkedList<>();
        while (true) {
            if (ch >= file.length()) {
                Log.v(TAG, "Finish file");
                break;
            }

            // Read the function name
            StringBuilder functionName = new StringBuilder();
            while (!file.substring(ch, ch + 1).equals("(")) {
                // Ignore semicolons in function names
                if (!file.substring(ch, ch + 1).equals(";")) {
                    functionName.append(file.substring(ch, ch + 1));
                }
                ch++;
                if (ch >= file.length() && !functionName.toString().trim().equals("")) {
                    Log.e(TAG, "Animation name never ends: " + functionName);
                    return null;
                } else if (ch >= file.length()) {
                    break;
                }
            }
            if (ch >= file.length()) {
                Log.v(TAG, "Finish ignoring: " + functionName);
                break;
            }

            String[] nameComponents = functionName.toString().trim().split(" ");
            if (nameComponents.length > 0) {
                functionName = new StringBuilder(nameComponents[nameComponents.length - 1].trim());
            } else {
                functionName = new StringBuilder(functionName.toString().trim());
            }

            if (functionName.length() == 0) {
                Log.e(TAG, "Nameless animation");
                return null;
            }

            if (!Character.isJavaIdentifierStart(functionName.charAt(0))) {
                Log.e(TAG, "Invalid character: " + functionName.charAt(0) + " in " + functionName);
                break;
            }

            for (int i = 1; i < functionName.length(); i++) {
                if (!Character.isJavaIdentifierPart(functionName.charAt(i))) {
                    Log.e(TAG, "Invalid character: " + functionName.charAt(i) + " in " + functionName);
                    break;
                }
            }

            Log.v(TAG, "Animation name: " + functionName);


            while (!file.substring(ch, ch + 1).equals("{")) {
                ch++;
                if (ch >= file.length()) {
                    Log.e(TAG, "Animation never starts: " + functionName);
                    return null;
                }
            }

            // Skip the end of the file
            ch++;
            if (ch >= file.length()) {
                Log.e(TAG, "Animation body opens at EOF:" + functionName);
                return null;
            }

            StringBuilder functionContent = new StringBuilder();

            // Capture function content
            while (!file.substring(ch, ch + 1).equals("}")) {
                functionContent.append(file.substring(ch, ch + 1));
                ch++;
                if (functionContent.length() >= 2 &&
                        (file.substring(ch - 2, ch).equals("//")
                                || file.substring(ch - 2, ch).equals("\\\\"))) {
                    functionContent = new StringBuilder(functionContent.substring(0, functionContent.length() - 2));
                    while (ch < file.length()
                            && !file.substring(ch, ch + 1).equals("\n")
                            && !file.substring(ch, ch + 1).equals("\r")) {
                        ch++;
                    }
                }
                if (ch >= file.length()) {
                    Log.e(TAG, "Animation never stops: " + functionName);
                    return null;
                }
            }

            ch++;

            Log.v(TAG, "Animation content: " + functionContent);

            Animation a = readAnimation(functionName.toString(), functionContent.toString());
            if (a == null) {
                Log.e(TAG, "Unable to parse animation: " + functionName);
                return null;
            } else {
                animations.add(a);
            }
        }

        return animations;
    }

}
