/*
 * Copyright 2015 Idrees Hassan, All Rights Reserved
 */
package scripts;

import charles.CHARLES;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ScriptExecutor {

    public String currentScript;
    int currentLineIndex;
    final Map<String, Script> scripts = new HashMap<>();
    final boolean garbageCollect = true;
    final Map<String, ObjectWrapper> variables = new HashMap<>();
    boolean updateLoop;
    String currentLine;
    int statements;
    boolean pass;
    boolean hardPass;
    private final Map<String, ObjectWrapper> tempObjects = new HashMap<>();
    public boolean started;
    public int pauseTicks;

    /**
     * Updates the script
     */
    public void update() {
        if (updateLoop) {
            if (pauseTicks > 0) {
                pauseTicks -= 1;
                if (pauseTicks == 0) {
                    nextLine();
                }
            }
            runScript();
        }
    }

    /**
     * Parses a command received from the user to see if it matches any script
     *
     * @param command The command received
     * @return Whether the command works or not
     */
    public boolean parseCommand(String command) {
        String[] split = softSplit(command.toUpperCase());
        boolean works = true;
        if (scripts.get(split[0]) != null) {
            String conditions = scripts.get(split[0]).conditions;
            String[] eachCondition = conditions.split(" ");
            if (eachCondition.length == split.length - 1) {
                for (int i = 1; i < split.length; i++) {
                    String part = split[i];
                    String conditionType = eachCondition[i - 1].split(":")[0];
                    Type type = parse(part).type;
                    if (type == Boolean.class) {
                        if (!conditionType.equals("BOOLEAN")) {
                            works = false;
                        }
                    } else if (type == Float.class) {
                        if (!conditionType.equals("NUMBER")) {
                            works = false;
                        }
                    } else if (type == String.class) {
                        if (!conditionType.equals("STRING")) {
                            works = false;
                        }
                    }
                }
            } else {
                works = split.length == 1 && eachCondition[0].equals("");
            }
            if (works) {
                if (garbageCollect) {
                    variables.clear();
                }
                for (int i = 1; i < split.length; i++) {
                    String conditionName = conditions.split(" ")[i - 1].split(":")[1];
                    ObjectWrapper obj = parse(split[i]);
                    variables.put(conditionName, obj);
                }
                startScript(split[0]);
            }
        } else {
            works = false;
        }
        return works;
    }

    /**
     * Returns the variable with the name given
     *
     * @param name The name of the variable
     * @return An ObjectWrapper containing the variable
     */
    public ObjectWrapper getVariable(String name) {
        ObjectWrapper result;
        name = name.replace("[", "").replace("]", "").toUpperCase();
        if (CHARLES.getVariable(name) != null) {
            result = new ObjectWrapper();
            result.set(CHARLES.getVariable(name));
        } else {
            result = variables.get(name);
        }
        if (result == null && Character.isLetter(name.charAt(0))) {
            error("Null Reference", "No variable stored with a name of " + name);
        }
        return result;
    }

    /**
     * Parses data and computes all nested functions
     *
     * @param data The data to parse
     * @return The value of the data
     */
    public ObjectWrapper parse(String data) {
        while (data.contains(")")) {
            int i = 0;
            int lastP = 0;
            for (char ch : data.toCharArray()) {
                if (ch == '(') {
                    lastP = i;
                } else if (ch == ')') {
                    String key = "{" + lastP + ":" + i + "}";
                    tempObjects.put(key, compute(data.substring(lastP + 1, i)));
                    data = data.replace(data.substring(lastP, i + 1), key);
                    break;
                }
                i++;
            }
        }
        ObjectWrapper result = compute(data);
        tempObjects.clear();
        return result;
    }

    /**
     * Returns the value of the data given
     *
     * @param stuff The data to compute
     * @return A variable containing the result of computing the data
     */
    public ObjectWrapper compute(String stuff) {
        ObjectWrapper variable = new ObjectWrapper();
        String[] split = stuff.split(" ");
        boolean computingString = false;
        boolean toSetComputingString = false;
        for (int i = 0; i < split.length; i++) {
            ObjectWrapper part = new ObjectWrapper();
            String data = split[i];
            if (computingString) {
                part.object = data.replace("\"", "");
                part.type = String.class;
                if (data.endsWith("\"")) {
                    toSetComputingString = false;
                }
            } else if (data.startsWith("{")) {
                part = tempObjects.get(data);
            } else if (data.startsWith("[")) {
                part = getVariable(data);
            } else if (data.equalsIgnoreCase("True")) {
                part.object = true;
                part.type = Boolean.class;
            } else if (data.equalsIgnoreCase("False")) {
                part.object = false;
                part.type = Boolean.class;
            } else if (data.startsWith("\"")) {
                part.object = data.replace("\"", "");
                part.type = String.class;
                toSetComputingString = true;
            } else if (isNumeric(data)) {
                part.object = Float.parseFloat(data);
                part.type = Float.class;
            } else if (getVariable(data) != null) {
                part = getVariable(data);
            }
            if (computingString) {
                variable.object = (String) variable.object + " " + (String) part.object;
            } else if (part != null && i > 0 && variable.type == part.type) {
                switch (split[i - 1]) {
                    case "+":
                        if (variable.type == String.class) {
                            variable.object = (String) variable.object + (String) part.object;
                        } else if (variable.type == Float.class) {
                            variable.object = (float) variable.object + (float) part.object;
                        }
                        break;
                    case "-":
                        variable.object = (float) variable.object - (float) part.object;
                        break;
                    case "*":
                        variable.object = (float) variable.object * (float) part.object;
                        break;
                    case "/":
                        variable.object = (float) variable.object / (float) part.object;
                        break;
                }
            } else if (part != null && part.object != null) {
                variable = part;
            }
            computingString = toSetComputingString;
        }
        return variable;
    }

    /**
     * Splits a string, but ignores nested items
     *
     * @param string The text to split
     * @return An array containing the split text
     */
    public String[] softSplit(String string) {
        StringBuilder buffer = new StringBuilder();
        int bracketCounter = 0;
        boolean quotePass = false;
        for (char c : string.toCharArray()) {
            if (c == '(') {
                bracketCounter++;
            }
            if (c == ')') {
                bracketCounter--;
            }
            if (c == '"') {
                quotePass = !quotePass;
            }
            if (c == ' ' && bracketCounter == 0 && !quotePass) {
                buffer.append("@");
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString().split("@");
    }

    /**
     * Parses a conditional line
     *
     * @param comparison The conditional statement to parse
     * @return The boolean result of the conditional
     */
    public Boolean conditional(String comparison) {
        //comparison = comparison.substring(comparison.indexOf(" ") + 1, comparison.lastIndexOf(" "));
        comparison = comparison.replace("IF", "").replace("THEN", "").replace("ELSE", "").trim();
        String[] split = softSplit(comparison);
        Boolean result = compareObjects(parse(split[0]), parse(split[2]), split[1]);
        if (comparison.contains("NOT")) {
            result = !result;
        }
        return result;
    }

    /**
     * Compares objects to see whether the comparison is true or false
     *
     * @param one The first value to compare
     * @param two The second value to compare
     * @param comparison The way the values shall be compared
     * @return The boolean result of the comparison
     */
    public Boolean compareObjects(ObjectWrapper one, ObjectWrapper two, String comparison) {
        Boolean result = false;
        if (one.type == two.type) {
            switch (comparison) {
                case "=":
                case "==":
                    if (one.object != null && one.object.equals(two.object)) {
                        result = true;
                    }
                    break;
                case "<":
                    if ((float) one.object < (float) two.object) {
                        result = true;
                    }
                    break;
                case ">":
                    if ((float) one.object > (float) two.object) {
                        result = true;
                    }
                    break;
                case "<=":
                case "=<":
                    if ((float) one.object <= (float) two.object) {
                        result = true;
                    }
                    break;
                case ">=":
                case "=>":
                    if ((float) one.object >= (float) two.object) {
                        result = true;
                    }
                    break;
            }
        }
        return result;
    }

    /**
     * Runs through the current script line by line
     */
    public void runScript() {
        if (currentScript != null && currentLine != null) {
            String[] split = softSplit(currentLine);
            if (!pass) {
                switch (split[0]) {
                    case "IF":
                        if (!conditional(currentLine)) {
                            statements = 1;
                            pass = true;
                        }
                        break;
                    case "ELSE":
                        statements = 1;
                        pass = true;
                        hardPass = true;
                        break;
                    case "GOTO":
                        if (split.length == 2) {
                            String label = split[1];
                            int index = scripts.get(currentScript).getLabelIndex(label);
                            if (index != -1) {
                                gotoLine(index);
                            } else {
                                error("Null Reference", "No label found with the name " + label);
                            }
                        } else {
                            error("Syntax", "GOTO used incorrectly");
                        }
                        break;
                    case "PAUSE":
                        if (pauseTicks == 0) {
                            if (split.length == 2) {
                                pauseTicks = Integer.parseInt(split[1]);
                            } else {
                                error("Syntax", "PAUSE used incorrectly");
                            }
                        }
                        break;
                    case "STORE":
                        if (split.length == 4) {
                            storeVariable(split[1], split[3]);
                        } else {
                            error("Syntax", "Improper variable storage");
                        }
                        break;
                    case "OUTPUT":
                        boolean format = false;
                        if (split.length == 3) {
                            format = Boolean.parseBoolean(split[2]);
                        }
                        CHARLES.output(parse(split[1]).toString(), format);
                        break;
                }
                if (pauseTicks == 0) {
                    nextLine();
                }
            } else {
                if (currentLine != null) {
                    if (currentLine.contains("END")) {
                        statements--;
                    } else if (currentLine.contains("ELSE")) {
                        if (!hardPass) {
                            if (statements == 1) {
                                statements = 0;
                            }
                        }
                    } else if (currentLine.contains("THEN")) {
                        statements++;
                    }
                    if (statements == 0) {
                        pass = false;
                        if (currentLine.contains("ELSE IF")) {
                            if (!conditional(currentLine)) {
                                statements = 1;
                                pass = true;
                            }
                        } else if (currentLine.contains("END")) {
                            hardPass = false;
                        }
                    }
                }
                nextLine();
            }
        }
    }

    /**
     * Goes to a specified line in the script
     *
     * @param index The index of the line to goto
     */
    public void gotoLine(int index) {
        currentLineIndex = index - 1;
        nextLine();
    }

    /**
     * Stores a variable
     *
     * @param var The name of the variable
     * @param data The value of the variable in string form
     */
    public void storeVariable(String var, String data) {
        var = var.replace("[", "").replace("]", "").toUpperCase();
        variables.put(var, parse(data));
    }

    /**
     * Determines whether the given text is numeric
     *
     * @param str Text to analyze
     * @return A boolean stating whether it is numeric or not
     */
    public static boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * Counts the occurrences of a character in a string
     *
     * @param str The string to be parsed
     * @param ch The character that is being counted
     * @return The number of occurrences
     */
    public int countOccurrence(String str, char ch) {
        int counter = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                counter++;
            }
        }
        return counter;
    }

    /**
     * Goes to the next line, being mindful not to exceed the amount of lines
     * available
     */
    public void nextLine() {
        currentLineIndex++;
        if (currentLineIndex >= scripts.get(currentScript).procedure.size()) {
            currentScript = null;
            currentLineIndex = 0;
            currentLine = null;
            pass = false;
            hardPass = false;
            pauseTicks = 0;
        } else {
            currentLine = getLine(currentLineIndex);
        }
    }

    /**
     * Gets the line of the script at the index given
     *
     * @param index The index of the line requested
     * @return The line
     */
    public String getLine(int index) {
        String result = scripts.get(currentScript).procedure.get(index);
        return result;
    }

    /**
     * Begins to execute a script
     *
     * @param scriptName The name of the script to be executed
     */
    public void startScript(String scriptName) {
        if (currentScript == null) {
            currentScript = scriptName;
            currentLineIndex = 0;
            currentLine = scripts.get(currentScript).get(currentLineIndex);
            pauseTicks = 0;
        }
    }

    /**
     * Adds one or more scripts
     *
     * @param scriptFile An ArrayList containing the scripts to add
     */
    public void addScripts(ArrayList<String> scriptFile) {
        Script script = null;
        ArrayList<String> procedure = new ArrayList<>();
        updateLoop = false;
        for (String part : scriptFile) {
            String line = part.trim().toUpperCase();
            if (line.startsWith("//")) {
            } else if (line.startsWith("<") && !line.startsWith("</")) {
                String[] split = line.replace("<", "").replace(">", "").split(" ");
                script = new Script();
                script.name = split[0];
                String conditions = "";
                for (int i = 1; i < split.length; i++) {
                    conditions += " " + split[i];
                }
                conditions = conditions.trim();
                script.conditions = conditions;
            } else if (script != null && line.startsWith("</" + script.name)) {
                script.procedure = procedure;
                script.scanForLabels();
                scripts.put(script.name, script);
                procedure = new ArrayList<>();
                script = null;
            } else {
                procedure.add(line);
            }
        }
        updateLoop = true;
    }

    /**
     * Displays an error message
     *
     * @param type The type of error
     * @param error The body of the error
     */
    public void error(String type, String error) {
        System.err.println(type + " Error: " + error + "\nCurrent Event: " + currentScript + "\nCurrent Line: " + currentLine);
        CHARLES.output(type + " Error: " + error + "\nCurrent Event: " + currentScript + "\nCurrent Line: " + currentLine, false);
    }
}
