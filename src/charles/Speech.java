/*
 * Copyright 2015 Idrees Hassan, All Rights Reserved
 */
package charles;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

public class Speech {

    private static Speech audio;

    public synchronized static Speech getInstance() {

        if (audio == null) {
            audio = new Speech();
        }
        return audio;
    }

    public InputStream getAudio(String text, String languageOutput)
            throws Exception {
        URL url = new URL("http://translate.google.com/translate_tts?tl=en&" + "q=" + text.replace(" ", "%20") + "&tl=" + languageOutput + "&ie=UTF-8&total=1&idx=0&client=t");
        URLConnection urlConn = url.openConnection();
        urlConn.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
        InputStream audioSrc = urlConn.getInputStream();
        return new BufferedInputStream(audioSrc);
    }

    public void play(InputStream sound) throws JavaLayerException {
        new Player(sound).play();
    }

}
