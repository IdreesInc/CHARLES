package scripts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Script {

    public String name;
    public String conditions;
    public ArrayList<String> procedure;
    public Map<String, Integer> labels = new HashMap<>();

    public Script() {
        name = "";
        conditions = "";
        procedure = new ArrayList<>();
    }

    public Script(String n, String c, ArrayList<String> p) {
        name = n;
        conditions = c;
        procedure = p;
    }

    /**
     * Scans the script for labels
     */
    public void scanForLabels() {
        int index = 0;
        labels.clear();
        for (String line : procedure) {
            if (line.startsWith("LABEL")) {
                String[] split = line.split(" ");
                if (split[1] != null) {
                    labels.put(split[1], index);
                }
            }
            index++;
        }
    }

    /**
     * Gets the index of the label name given
     *
     * @param label The name of the label
     * @return The index of the label
     */
    public int getLabelIndex(String label) {
        if (labels.containsKey(label)) {
            return labels.get(label);
        } else {
            return - 1;
        }
    }

    /**
     * Returns the line in the procedures that is at the given index
     *
     * @param index The index of the line
     * @return The line at the given index
     */
    public String get(int index) {
        return procedure.get(index);
    }
}
