/*
 * Copyright 2015 Idrees Hassan, All Rights Reserved
 */
package charles;

public class Event {

    public String timestamp;
    public String command;
    public boolean hasRun;
    private boolean pending;

    public Event() {
    }

    public Event(String c, String t) {
        command = c;
        timestamp = t;
    }

    /**
     * Checks to see if the event can run, and if so, sends the specified
     * command
     *
     * @param currentTimestamp The timestamp as of now
     */
    public void update(String currentTimestamp) {
        if (currentTimestamp.contains(timestamp)) {
            if (!hasRun) {
                pending = true;
            }
        } else {
            hasRun = false;
        }
        if (pending) {
            if (CHARLES.eventCommand == null) {
                CHARLES.eventCommand = command;
                hasRun = true;
                pending = false;
                System.out.println("Running event " + command);
            }
        }
    }

    @Override
    public String toString() {
        return command + "@" + timestamp;
    }
}
