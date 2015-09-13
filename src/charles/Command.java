/*
 * Copyright 2015 Idrees Hassan, All Rights Reserved
 */
package charles;

import java.util.HashMap;
import java.util.Map;

public final class Command {

    public final String originalCommand;
    private final Map<String, String> ALL_TOKENS = new HashMap<>();
    public String subjectPart = "";
    public String timePart = "";
    public String text;
    public String[] words;

    public Command(String cmd) {
        initTokens();
        originalCommand = cmd;
        words = caps().split(" ");
        if (words.length > 1) {
            text = originalCommand.substring(words[0].length() + 1);
        }
        updatePartitives();
    }

    /**
     * Initialize the token map to contain all tokens and types possible
     */
    private void initTokens() {
        ALL_TOKENS.put("TO", "SUBJECT");
        ALL_TOKENS.put("THAT", "SUBJECT");
        ALL_TOKENS.put("AT", "TIME");
    }

    /**
     * Stores the parts that are found after each token
     */
    private void updatePartitives() {
        //TODO: Fix this
        String currentType = null;
        String currentPart = "";
        for (String word : words) {
            if (ALL_TOKENS.containsKey(word)) {
                if (currentType == null) {
                    currentType = ALL_TOKENS.get(word);
                    currentPart = "";
                } else {
                    if (!ALL_TOKENS.get(word).equals(currentType)) {
                        currentPart = currentPart.trim();
                        switch (currentType) {
                            case "SUBJECT":
                                subjectPart = currentPart;
                                currentPart = "";
                                currentType = ALL_TOKENS.get(word);
                                break;
                            case "TIME":
                                timePart = currentPart;
                                currentPart = "";
                                currentType = ALL_TOKENS.get(word);
                        }
                    } else {
                        currentPart += word + " ";
                    }

                }
            } else {
                currentPart += word + " ";
            }
        }
        if (currentType != null) {
            currentPart = currentPart.trim();
            switch (currentType) {
                case "SUBJECT":
                    subjectPart = currentPart;
                    break;
                case "TIME":
                    timePart = currentPart;
            }
        }
    }

    /**
     * Returns a capitalized version of the original command
     *
     * @return The command in all upper case
     */
    public String caps() {
        return originalCommand.toUpperCase();
    }

    /**
     * Returns the original command after removing the given number of
     * characters
     *
     * @param index The number of letters to remove
     * @return The substring
     */
    public String substring(int index) {
        return originalCommand.substring(index);
    }

    /**
     * Returns the timePart formatted in Date format (NOT IMPLEMENTED)
     *
     * @return The formatted timePart
     */
    public String getTime() {
        String time = timePart;
        time = time.replace("/", "-");
        return time;
    }

    @Override
    public String toString() {
        return originalCommand;
    }

    /**
     * Determines if the command begins with the given text
     *
     * @param startsWith The text to check if the command starts with
     * @return Whether the command starts with the given text
     */
    public boolean is(String startsWith) {
        return caps().startsWith(startsWith.toUpperCase());
    }
}
