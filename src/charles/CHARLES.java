package charles;

import com.gtranslate.Audio;
import com.gtranslate.Language;
import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javazoom.jl.decoder.JavaLayerException;
import laboratory.VoiceListener;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;
import scripts.ScriptExecutor;

public class CHARLES {

    static Session smsInputSession;
    static Session emailSession;
    static Chat chat;
    static XMPPConnection connection;
    private static final String SETTINGS_FILE = "Settings";
    static String INPUT_EMAIL;
    static String INPUT_USERNAME;
    static String INPUT_PASSWORD;
    static String HOST;
    static String PHONE_NUMBER;
    static String PHONE_EMAIL;
    static String EMAIL;
    static String EMAIL_USERNAME;
    static String EMAIL_PASSWORD;
    static String SCRIPTS_FILE = "Scripts";
    static String LOGS_FILE = "Logs";
    static String GOOGLE_API_KEY;
    static int INPUT_EMAIL_CHECK_INTERVAL = 1000;
    static int AWAITING_INPUT_EMAIL_CHECK_INTERVAL = 200;
    static int EMAIL_CHECK_INTERVAL = 1000;
    static boolean checkEmail = true;
    static Properties props;
    static int inputOldMessageCount = -1;
    static int oldEmailCount = -1;
    static int ticks;
    static int lastReply;
    static String replyPrompt;
    static Scanner scanner;
    static String lastInput;
    static String lastIM;
    static String lastInputEmail;
    public static String lastVoiceInput;
    static String lastOutput;
    public static String eventCommand;
    static Command command;
    static String currentMedium = "CONSOLE";
    static String toSendTo;
    static String toSendText;
    static ScriptExecutor scriptExecutor = new ScriptExecutor();
    static ArrayList<String> createdScript = new ArrayList<>();
    static Event createdEvent;
    static ArrayList<Event> scheduledEvents = new ArrayList<>();
    static String toSay;
    static Voice freeTTSVoice;
    static String currentOS;
    static ScheduledExecutorService scheduler;
    static boolean deactivate = false;
    public static boolean mute = false;
    static String lastEmailBody;
    public static boolean listen;

    public static void main(String[] args) {
        currentOS = System.getProperty("os.name");
        if (currentOS != null) {
            System.out.println("Currently running on " + currentOS);
        }
        initSettings();
        initEmail();
        initIM();
        scriptExecutor.addScripts(read(SCRIPTS_FILE));
        updateEvents();
        scanner = new Scanner(System.in);
        scanner.useDelimiter("\\n");

        Thread update = new Thread(new UpdateThread());
        update.setName("Update Thread");
        Thread email = new Thread(new EmailThread());
        email.setName("Email Thread");
        Thread script = new Thread(new ScriptThread());
        script.setName("Script Thread");
        Thread tts = new Thread(new TTSThread());
        tts.setName("Text to Speech Thread");
        scheduler = Executors.newScheduledThreadPool(5);
        scheduler.scheduleAtFixedRate(update, 0, 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(tts, 0, 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(email, 0, 10, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(script, 0, 10, TimeUnit.MILLISECONDS);
        if (GOOGLE_API_KEY != null) {
            Thread voiceListener = new Thread(new VoiceListener(GOOGLE_API_KEY));
            voiceListener.setName("Voice Listener Thread");
            scheduler.scheduleAtFixedRate(voiceListener, 0, 10, TimeUnit.MILLISECONDS);
        }

        write(LOGS_FILE, "-----COMMUNICATION STARTED AT " + getTimestamp() + "-----", true);
        checkInputEmail();
        checkEmail();
        output("Good " + getTimeOfDay(), true);
        while (true) {
            lastInput = scanner.nextLine();
        }
    }

    /**
     * Reads the settings file and sets the value of the constants to the
     * constants specified
     */
    public static void initSettings() {
        ArrayList<String> settings = read(SETTINGS_FILE);
        String phoneCarrier = null;
        for (String setting : settings) {
            String[] split = setting.split(":");
            switch (split[0]) {
                case "INPUT_EMAIL":
                    INPUT_EMAIL = split[1];
                    break;
                case "INPUT_USERNAME":
                    INPUT_USERNAME = split[1];
                    break;
                case "INPUT_PASSWORD":
                    INPUT_PASSWORD = split[1];
                    break;
                case "HOST":
                    HOST = split[1];
                    break;
                case "PHONE_NUMBER":
                    PHONE_NUMBER = split[1];
                    break;
                case "PHONE_CARRIER":
                    phoneCarrier = split[1];
                    break;
                case "EMAIL":
                    EMAIL = split[1];
                    break;
                case "EMAIL_USERNAME":
                    EMAIL_USERNAME = split[1];
                    break;
                case "EMAIL_PASSWORD":
                    EMAIL_PASSWORD = split[1];
                    break;
                case "GOOGLE_API_KEY":
                    GOOGLE_API_KEY = split[1];
                    break;
                default:
                    System.err.println("Parsing of line in Settings file failed: " + setting);
            }
        }
        PHONE_EMAIL = parseNumber(PHONE_NUMBER, phoneCarrier);
    }

    /**
     * Initializes the variables related to FreeTTS
     */
    public static void initFreeTTS() {
        VoiceManager voiceManager = VoiceManager.getInstance();
        freeTTSVoice = voiceManager.getVoice("kevin");
        freeTTSVoice.setStyle("breathy");
        freeTTSVoice.setPitchRange(20);
        freeTTSVoice.allocate();
    }

    /**
     * Initialize the variables related to Java Mail
     */
    public static void initEmail() {
        props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", HOST);
        props.put("mail.smtp.port", "587");
        props.setProperty("mail.store.protocol", "imaps");
        smsInputSession = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(INPUT_USERNAME, INPUT_PASSWORD);
                    }
                });
        emailSession = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_USERNAME, EMAIL_PASSWORD);
                    }
                });

    }

    /**
     * Initialize the variables related to Google Talk
     */
    public static void initIM() {
        try {
            ConnectionConfiguration connConfig = new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
            connConfig.setSASLAuthenticationEnabled(false);
            connection = new XMPPConnection(connConfig);
            connection.connect();
            connection.login(INPUT_USERNAME, INPUT_PASSWORD);
            connection.sendPacket(new Presence(Presence.Type.available));
            ChatManager chatmanager = connection.getChatManager();
            chat = chatmanager.createChat(EMAIL, (Chat ch, org.jivesoftware.smack.packet.Message msg) -> {
                if (msg.getBody() != null) {
                    lastIM = msg.getBody();
                    lastReply = ticks;
                }
            });
        } catch (XMPPException ex) {
            System.err.println(ex);
        }
    }

    /**
     * Update the scheduledEvents with the data from the Events file
     */
    public static void updateEvents() {
        ArrayList<String> events = read("Events");
        ArrayList<Event> newEvents = new ArrayList<>();
        events.stream().map((line) -> {
            String[] split = line.split(":");
            Event event = new Event(split[0], line.substring(split[0].length() + 1));
            return event;
        }).forEach((event) -> {
            boolean duplicate = false;
            for (Event evt : scheduledEvents) {
                if (event.toString().equals(evt.toString())) {
                    duplicate = true;
                    newEvents.add(evt);
                    break;
                }
            }
            if (!duplicate) {
                newEvents.add(event);
            }
        });
        scheduledEvents = newEvents;
    }

    private static class UpdateThread
            implements Runnable {

        @Override
        public void run() {
            if (!deactivate) {
                ticks++;
                //Update scheduled events
                String timestamp = getTimestamp();
                scheduledEvents.stream().forEach((evt) -> {
                    evt.update(timestamp);
                });
                //Check for commands
                getLastCommand();
                if (command != null) {
                    System.out.println("Command Recieved: " + command.toString());
                    lastReply = ticks;
                    if (replyPrompt == null) {
                        //Parse new command
                        boolean executed = false;
                        while (!executed && command.words.length > 0) {
                            executed = true;
                            if (command.is("HELLO")) {
                                output("Hello", true);
                            } else if (command.is("GET")) {
                                if (command.text != null) {
                                    String variable = command.words[1].toUpperCase();
                                    Object var = getVariable(variable);
                                    if (var != null) {
                                        if (var instanceof String) {
                                            output("The variable " + variable + " equals " + ((String) var), true);
                                        } else if (var instanceof Integer) {
                                            output("The variable " + variable + " equals " + ((int) var), true);
                                        } else if (var instanceof Boolean) {
                                            output("The variable " + variable + " equals " + ((boolean) var), true);
                                        }
                                    } else {
                                        output("Unfortunately, no variable was found with that name", true);
                                    }
                                } else {
                                    output("-a life", true);
                                }
                            } else if (command.is("READ")) {
                                if (read(command.text) != null) {
                                    String text = "";
                                    text = read(command.text).stream().map((line) -> line + "\n").reduce(text, String::concat);
                                    output(text, false);
                                } else {
                                    output("Unfortunately, no file could be found with that name", true);
                                }
                            } else if (command.is("SAY")) {
                                if (command.text != null) {
                                    output(command.text, true);
                                }
                            } else if (command.is("BROADCAST")) {
                                if (command.text != null) {
                                    String trueMedium = currentMedium + "";
                                    currentMedium = "ALL";
                                    output(command.text, true);
                                    currentMedium = trueMedium;
                                }
                            } else if (command.is("REMIND")) {
                                if (command.timePart != null && command.subjectPart != null) {
                                    write("Events", "Say " + command.subjectPart + ":" + command.getTime(), true);
                                    updateEvents();
                                    output("I shall remind you at " + command.getTime() + " to " + command.subjectPart, true);
                                }
                            } else if (command.is("SCHEDULE")) {
                                if (command.timePart != null && command.subjectPart != null) {
                                    write("Events", command.subjectPart + ":" + command.getTime(), true);
                                    updateEvents();
                                    output("I have scheduled the event for you", true);
                                }
                            } else if (command.is("DEACTIVATE") || command.is("GOODNIGHT")) {
                                deactivate();
                            } else if (command.is("WRITE SCRIPT")) {
                                if (command.words.length == 3) {
                                    createdScript.clear();
                                    createdScript.add(command.words[2]);
                                    output("What parameters would you like to add", true);
                                    replyPrompt = "PARAMETERS";
                                } else {
                                    output("The expected input for this command is 'WRITE SCRIPT [NAME]'", true);
                                }
                            } else if (command.is("THANKS")) {
                                output("Any time", true);
                            } else if (command.is("SHUT UP") || command.is("MUTE")) {
                                mute = true;
                                output("I shall now keep my thoughts to myself", true);
                            } else if (command.is("TALK NOW") || command.is("UNMUTE")) {
                                mute = false;
                                output("Why hello again", true);
                            } else if (command.is("LISTEN")) {
                                listen = !listen;
                                System.out.println("Setting 'Listen' to " + listen);
                            } else {
                                executed = scriptExecutor.parseCommand(command.originalCommand);
                            }
                            if (!executed) {
                                if (command.words.length > 1) {
                                    command = new Command(command.originalCommand.substring(command.words[0].length() + 1));
                                } else {
                                    executed = true;
                                    output("Sorry sir, but I didn't quite get that!", true);
                                }
                            }
                        }
                    } else {
                        //Parse reply
                        if (command.is("CANCEL")) {
                            replyPrompt = null;
                        } else {
                            switch (replyPrompt) {
                                case "PARAMETERS":
                                    if (command.words.length % 2 == 0) {
                                        String firstLine = "<" + createdScript.get(0);
                                        String lastLine = "</" + createdScript.get(0) + ">";
                                        for (int i = 0; i + 1 < command.words.length; i += 2) {
                                            String parameter = command.words[i] + ":" + command.words[i + 1];
                                            firstLine += " " + parameter;
                                        }
                                        firstLine = firstLine.trim() + ">";
                                        createdScript.set(0, firstLine);
                                        createdScript.add(1, lastLine);
                                        output("Noted, you may now enter your code", true);
                                        replyPrompt = "SCRIPT";
                                    } else {
                                        output("I believe your parameters are messed up", true);
                                    }
                                    break;
                                case "SCRIPT":
                                    String[] lines = command.caps().split("\n");
                                    String lastLine = createdScript.get(1);
                                    createdScript.remove(1);
                                    createdScript.addAll(Arrays.asList(lines));
                                    createdScript.add(lastLine);
                                    String output = "";
                                    output = createdScript.stream().map((line) -> line + "\n").reduce(output, String::concat);
                                    output = output.trim();
                                    output(output, false);
                                    scriptExecutor.addScripts(createdScript);
                                    output("Your script has been saved", true);
                                    replyPrompt = null;
                                    break;
                                case "EMAIL":
                                    if (command.caps().contains("MORE")) {
                                        if (lastEmailBody != null) {
                                            output("Here is the rest of the email, sir. " + lastEmailBody, false);
                                            lastEmailBody = null;
                                        }
                                        replyPrompt = null;
                                    }
                                    break;
                            }
                        }
                    }
                    command = null;
                } else {
                    //Check for reply timeout
                    if (replyPrompt != null && ticks - lastReply > 6000) {
                        replyPrompt = null;
                        System.out.println("A reply was not given, timed out");
                    }
                }
            }
        }
    }

    private static class ScriptThread
            implements Runnable {

        @Override
        public void run() {
            if (!deactivate) {
                scriptExecutor.update();
            }
        }
    }

    private static class EmailThread
            implements Runnable {

        @Override
        public void run() {
            if (!deactivate) {
                if (ticks % EMAIL_CHECK_INTERVAL == 0) {
                    checkEmail();
                }
                String email = null;
                if (!(currentMedium.contains("TEXT") || currentMedium.contains("ALL")) || ticks - lastReply > 6000) {
                    if (ticks % AWAITING_INPUT_EMAIL_CHECK_INTERVAL == 0) {
                        email = checkInputEmail();
                    }
                } else {
                    if (ticks % INPUT_EMAIL_CHECK_INTERVAL == 0) {
                        email = checkInputEmail();
                    }
                }
                if (email != null) {
                    lastInputEmail = email;
                }
                if (toSendTo != null && toSendText != null) {
                    if (toSendTo.equals(PHONE_EMAIL)) {
                        if (toSendText.length() <= 150) {
                            sendEmail(toSendText, toSendTo);
                        } else {
                            ArrayList<String> split = wordWrap(toSendText, 150);
                            split.stream().forEach((part) -> {
                                sendEmail(part, toSendTo);
                            });
                        }
                    } else {
                        sendEmail(toSendText, toSendTo);
                    }
                    toSendTo = null;
                    toSendText = null;
                }
            }
        }
    }

    private static class TTSThread
            implements Runnable {

        @Override
        public void run() {
            if (!deactivate) {
                if (toSay != null) {
                    speak(toSay);
                    toSay = null;
                }
            }
        }
    }

    /**
     * Sends a message through the current medium
     *
     * @param text The message to be sent
     * @param format Whether to format the text
     */
    public static void output(String text, boolean format) {
        if (format) {
            if (text.equals(text.toUpperCase())) {
                text = text.toLowerCase();
            }
            text = text.substring(0, 1).toUpperCase() + text.substring(1);
            String caps = text.toUpperCase();
            if (!text.endsWith(".") && !text.endsWith("?") && !text.endsWith("!")) {
                String end = ", sir";
                if (caps.startsWith("WHO") || caps.startsWith("WHAT") || caps.startsWith("WHEN") || caps.startsWith("WHERE") || caps.startsWith("WHY")) {
                    end += "?";
                } else {
                    end += ".";
                }
                text += end;
            }
        }
        switch (currentMedium.replace("FORCE", "")) {
            case "CONSOLE":
                System.out.println("//" + text);
                break;
            case "IM":
                sendIM(text);
                break;
            case "TEXT":
                toSendTo = PHONE_EMAIL;
                toSendText = text;
                break;
            default:
                System.out.println("Output: " + text);
                sendIM(text);
                toSendTo = PHONE_EMAIL;
                toSendText = text;
                break;
        }
        write(LOGS_FILE, "Output: " + text, true);
        if (text.length() < 200) {
            toSay = text;
        }
    }

    /**
     * Updates the command variable to the last command received
     */
    public static void getLastCommand() {
        String commandText = null;
        if (eventCommand != null && replyPrompt == null) {
            commandText = eventCommand;
            eventCommand = null;
        } else if (lastInput != null) {
            commandText = lastInput;
            lastInput = null;
            if (!currentMedium.contains("FORCE")) {
                currentMedium = "CONSOLE";
            }
        } else if (lastVoiceInput != null) {
            commandText = lastVoiceInput;
            lastVoiceInput = null;
        } else if (lastIM != null) {
            commandText = lastIM;
            lastIM = null;
            if (!currentMedium.contains("FORCE")) {
                currentMedium = "IM";
            }
        } else if (lastInputEmail != null) {
            commandText = lastInputEmail;
            lastInputEmail = null;
            if (!currentMedium.contains("FORCE")) {
                currentMedium = "TEXT";
            }
        }
        if (commandText != null) {
            commandText = commandText.trim();
            write(LOGS_FILE, "Input: " + commandText, true);
            command = new Command(commandText);
        }
    }

    /**
     * Converts the given text to speech, which is then played
     *
     * @param text The text to convert to speech
     */
    public static void speak(String text) {
        if (freeTTSVoice == null) {
            initFreeTTS();
        }
        if (!mute && text != null) {
            InputStream sound = null;
            if (text.substring(text.length() - 6).startsWith(",")) {
                text = text.substring(0, text.length() - 6) + text.substring(text.length() - 5);
            }
            try {
                Audio audio = Audio.getInstance();
                if (!currentOS.contains("Windows") && !currentOS.contains("Mac")) {
                    text = "Ahem, " + text;
                }
                for (String chunk : wordWrap(text.replace("\n", ". ").replace("\r", ". "), 90)) {
                    sound = audio.getAudio(chunk, Language.ENGLISH);
                    audio.play(sound);
                }
            } catch (IOException | JavaLayerException ex) {
                System.err.println("Unable to use Google's TTS service, instead using FreeTTS");
                freeTTSVoice.speak(text);
            } finally {
                try {
                    if (sound != null) {
                        sound.close();
                    }
                } catch (IOException ex) {
                }
            }
        }
    }

    /**
     * Checks the email server and returns the last sent command
     *
     * @return The last command sent to the email server
     */
    public static String checkInputEmail() {
        String retreievedComand = null;
        try {
            Store store = smsInputSession.getStore("imaps");
            store.connect("imap.gmail.com", INPUT_EMAIL, INPUT_PASSWORD);
            Folder inbox = store.getFolder("Inbox");
            int messageCount = inbox.getMessageCount();
            if (inputOldMessageCount == -1) {
                inputOldMessageCount = messageCount;
            }
            if (messageCount > 0 && messageCount != inputOldMessageCount) {
                inbox.open(Folder.READ_ONLY);
                Message msg = inbox.getMessage(messageCount);
                if (msg.getFrom()[0].toString().contains(PHONE_NUMBER)) {
                    if (msg.getContent() instanceof String) {
                        retreievedComand = (String) msg.getContent();
                    } else {
                        Multipart mp = (Multipart) msg.getContent();
                        retreievedComand = mp.getBodyPart(0).getContent().toString();
                    }
                }
            }
            inputOldMessageCount = messageCount;
            store.close();
        } catch (NoSuchProviderException e) {
            System.err.println(e.toString());
        } catch (MessagingException e) {
            System.err.println(e.toString());
        } catch (IOException e) {
            System.err.println(e.toString());
        }
        return retreievedComand;
    }

    /**
     * Checks the email server for inbox
     */
    public static void checkEmail() {
        try {
            Store store = emailSession.getStore("imaps");
            store.connect("imap.gmail.com", EMAIL, EMAIL_PASSWORD);
            Folder inbox = store.getFolder("Inbox");
            int messageCount = inbox.getMessageCount();
            if (oldEmailCount == -1) {
                oldEmailCount = messageCount;
            }
            if (messageCount > 0 && messageCount != oldEmailCount) {
                inbox.open(Folder.READ_ONLY);
                Message msg = inbox.getMessage(messageCount);
                if (msg.getContent() instanceof String) {
                    lastEmailBody = (String) msg.getContent();
                } else {
                    Multipart mp = (Multipart) msg.getContent();
                    lastEmailBody = mp.getBodyPart(0).getContent().toString();
                }
                output("Sir, you have recieved an email from " + msg.getFrom()[0].toString().replace("<", " ").replace(">", " ") + " with the subject as follows. " + msg.getSubject() + ".", true);
                replyPrompt = "EMAIL";
            }
            oldEmailCount = messageCount;
            store.close();
        } catch (NoSuchProviderException e) {
            System.err.println(e.toString());
        } catch (MessagingException e) {
            System.err.println(e.toString());
        } catch (IOException ex) {
            Logger.getLogger(CHARLES.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Sends an email to a specified address
     *
     * @param text The body text of the email
     * @param to The email address to send to
     */
    public static void sendEmail(String text, String to) {
        try {
            Message msg = new MimeMessage(smsInputSession);
            msg.setFrom(new InternetAddress(INPUT_EMAIL));
            msg.addRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setText(text);
            Transport.send(msg);
            lastOutput = text;
            System.out.println("Email: " + text + " sent to " + to + " at " + getTimestamp());
        } catch (MessagingException e) {
            System.err.println(e.toString());
        }
    }

    /**
     * Sends an IM using Google talk
     *
     * @param text The message to send
     */
    public static void sendIM(String text) {
        try {
            chat.sendMessage(text);
            lastOutput = text;
            System.out.println("IM: " + text + " sent at " + getTimestamp());
        } catch (XMPPException ex) {
            System.err.println(ex);
        }
    }

    /**
     * Returns the value of the variable with the given name
     *
     * @param name Name of the variable
     * @return The value of the variable
     */
    public static Object getVariable(String name) {
        switch (name.toUpperCase()) {
            case "TICKS":
                return ticks;
            case "CURRENTMEDIUM":
                return currentMedium;
        }
        return null;
    }

    /**
     * Returns the current timestamp
     *
     * @return The timestamp in string form
     */
    public static String getTimestamp() {
        java.util.Date date = new java.util.Date();
        Timestamp time = new Timestamp(date.getTime());
        time.setNanos(0);
        return time.toString().substring(0, time.toString().length() - 2);
    }

    /**
     * Returns the time of day in colloquial terms
     *
     * @return Time of the day
     */
    public static String getTimeOfDay() {
        String timeOfDay = null;
        Calendar c = Calendar.getInstance();
        int time = c.get(Calendar.HOUR_OF_DAY);
        if (time >= 0 && time < 12) {
            timeOfDay = "morning";
        } else if (time >= 12 && time < 18) {
            timeOfDay = "afternoon";
        } else if (time >= 18) {
            timeOfDay = "evening";
        }
        return timeOfDay;
    }

    /**
     * Deactivates CHARLES and exits the program
     */
    public static void deactivate() {
        speak("Until next time sir");
        output("I am now currently deactivating at " + getTimestamp() + "\nUntil next time", true);
        connection.sendPacket(new Presence(Presence.Type.unavailable));
        deactivate = true;
        scheduler.shutdownNow();
        System.exit(0);
    }

    /**
     * Word wrap the text into lines of the number of characters specified
     *
     * @param s String to split
     * @param chunkSize Size of chunks to be split by
     * @return ArrayList with the lines of words
     */
    private static ArrayList<String> wordWrap(String s, int chunkSize) {
        ArrayList<String> returnVal = new ArrayList<>();
        char[] sAr = s.toCharArray();
        int start = 0;
        for (int i = chunkSize; i < sAr.length; i++) {
            if (sAr[i] == ' ') {
                returnVal.add(s.substring(start, i));
                start = i + 1;
                i += chunkSize;
            }
        }
        returnVal.add(s.substring(start));
        return returnVal;
    }

    /**
     * Parses a phone number for gateway email
     *
     * @param number The phone's number
     * @param carrier The phone's carrier
     * @return The email for texts to be sent to
     */
    public static String parseNumber(String number, String carrier) {
        carrier = carrier.toUpperCase();
        carrier = carrier.replace(" ", "");
        carrier = carrier.replace("-", "");
        carrier = carrier.replace("&", "");
        carrier = carrier.replace("+", "");
        number = number.replace("-", "");
        number = number.replace(" ", "");
        String gateway = "";
        switch (carrier) {
            case "VERIZON":
                gateway = "@vtext.com";
                break;
            case "ATT":
                gateway = "@txt.att.net";
                break;
            case "SPRINT":
                gateway = "@messaging.sprintpcs.com";
                break;
            case "TMOBILE":
                gateway = "@tmomail.net";
                break;
        }
        return number + gateway;
    }

    /**
     * Reads through a text file and generates an ArrayList of the lines
     *
     * @param name The name of the file
     * @return An ArrayList containing each line of the file
     */
    public static ArrayList<String> read(String name) {
        ArrayList<String> data = new ArrayList<>();
        Scanner textScanner;
        if (!name.contains(".")) {
            name += ".txt";

        }
        try {
            String urlString = CHARLES.class
                    .getProtectionDomain().getCodeSource().getLocation().toString().replace("build/classes/", "src/data/" + name).replace("dist/CHARLES.jar", "src/data/" + name);
            URL url = new URL(urlString);
            File file = new File(url.toURI());
            textScanner = new Scanner(file);

            while (textScanner.hasNext()) {
                data.add(textScanner.nextLine());
            }

            textScanner.close();
        } catch (FileNotFoundException e) {
            System.err.println("Thank you mario, but our princess is in another castle!\nFile attempted to be found: " + name);

        } catch (URISyntaxException ex) {
        } catch (MalformedURLException ex) {
            Logger.getLogger(CHARLES.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Read: " + name);
        return data;
    }

    /**
     * Writes to a text file
     *
     * @param name The name of the file
     * @param text The Text to write
     * @param append Whether to append to the file or clear it
     */
    public static void write(String name, String text, boolean append) {
        try {
            if (!name.contains(".")) {
                name += ".txt";

            }
            String urlString = CHARLES.class
                    .getProtectionDomain().getCodeSource().getLocation().toString().replace("build/classes/", "src/data/" + name).replace("dist/CHARLES.jar", "src/data/" + name);
            URL url = new URL(urlString);
            File file = new File(url.toURI());
            try (BufferedWriter out = new BufferedWriter(new FileWriter(file, append))) {
                if (append) {
                    text = text + "\n";
                }
                out.append(text);
            }
            //System.out.println(url.toString());
        } catch (URISyntaxException | IOException ex) {
            System.err.println("Unable to write to file");
        }
    }
}
