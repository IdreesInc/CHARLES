/*
 * Copyright 2015 Idrees Hassan, All Rights Reserved
 */
package laboratory;

import charles.CHARLES;
import com.darkprograms.speech.microphone.Microphone;
import com.darkprograms.speech.recognizer.GSpeechDuplex;
import com.darkprograms.speech.recognizer.GoogleResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import javaFlacEncoder.FLACFileWriter;
import javax.sound.sampled.LineUnavailableException;

public class VoiceListener
        implements Runnable {

    private final String API_KEY;
    private GSpeechDuplex dup;
    private Microphone mic;

    public VoiceListener(String API_KEY) {
        this.API_KEY = API_KEY;
    }

    @Override
    public void run() {
        if (dup == null) {
            dup = new GSpeechDuplex(API_KEY);
            dup.addResponseListener((GoogleResponse gr) -> {
                System.out.println("Google thinks you said: " + gr.getResponse());
                CHARLES.lastVoiceInput = gr.getResponse();
            }
            );
        }
        if (mic == null) {
            mic = new Microphone(FLACFileWriter.FLAC);
        }
        if (CHARLES.listen) {
            try {
                File file = new File("VoiceRecording.flac");
                boolean oldMute = CHARLES.mute;
                CHARLES.mute = true;
                CHARLES.output("Currently listening...", false);
                System.out.println("Currently listening...");
                mic.captureAudioToFile(file);
                long start = System.currentTimeMillis();
                boolean tick = true;
                while (CHARLES.listen && System.currentTimeMillis() < start + 9500) {
                    if (System.currentTimeMillis() % 1000 == 0) {
                        if (tick) {
                            System.out.print("");
                        }
                        tick = false;
                    } else {
                        tick = true;
                    }
                }
                mic.close();
                CHARLES.listen = false;
                CHARLES.mute = oldMute;
                CHARLES.output("Sending data to Google...", false);
                System.out.println("Sending data to Google...");
                byte[] data = Files.readAllBytes(mic.getAudioFile().toPath());
                dup.recognize(data, (int) mic.getAudioFormat().getSampleRate());
                mic.getAudioFile().delete();
            } catch (LineUnavailableException | IOException ex) {
                Logger.getLogger(VoiceListener.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
