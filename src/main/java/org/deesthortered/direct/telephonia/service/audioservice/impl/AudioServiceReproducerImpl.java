package org.deesthortered.direct.telephonia.service.audioservice.impl;

import org.deesthortered.direct.telephonia.service.audioservice.AudioServiceReproducer;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

@Component
public class AudioServiceReproducerImpl implements AudioServiceReproducer {

    private AudioServiceCallback audioServiceCallbackPlayingFailed;
    private AudioServiceCallback audioServiceCallbackPlayingStopped;
    private AudioServiceCallback audioServiceCallbackPlayingFinished;

    private List<AudioServiceStreamFilter> filters;
    private AudioServiceStreamReader streamReader;

    private volatile boolean isPlaying;
    private volatile boolean wasPlaying;
    private volatile boolean wasPlayingStarted;
    private volatile boolean isPlayingStopped;
    private Thread threadPlay;

    private volatile AudioFormat format;
    private volatile SourceDataLine speakers;

    public AudioServiceReproducerImpl() {
    }

    @Override
    public void initializeService(AudioServiceCallback audioServiceCallbackPlayingFailed, AudioServiceCallback audioServiceCallbackPlayingStopped, AudioServiceCallback audioServiceCallbackPlayingFinished) {
        this.audioServiceCallbackPlayingFailed = audioServiceCallbackPlayingFailed;
        this.audioServiceCallbackPlayingStopped = audioServiceCallbackPlayingStopped;
        this.audioServiceCallbackPlayingFinished = audioServiceCallbackPlayingFinished;
        this.filters = new ArrayList<>();

        this.isPlaying = false;
        this.wasPlaying = false;
        this.wasPlayingStarted = false;
        this.isPlayingStopped = false;

        this.format = new AudioFormat(16000.0f, 16, 2, true, false);

        this.threadPlay = new Thread(new FutureTask<>(getPlayingThread()));
        this.threadPlay.setDaemon(true);
        this.threadPlay.start();
    }

    @Override
    public void close() {
        this.threadPlay.interrupt();
    }

    @Override
    public void setStreamReader(AudioServiceStreamReader streamReader) {
        this.streamReader = streamReader;
    }

    @Override
    public void addFilter(AudioServiceStreamFilter filter) {
        this.filters.add(filter);
    }

    @Override
    public void startPlayingRecord() {
        this.isPlaying = true;
        this.wasPlayingStarted = true;
        this.isPlayingStopped = false;
    }

    @Override
    public void stopPlayingRecord() {
        this.isPlaying = false;
        this.isPlayingStopped = true;
    }


    private Callable<Void> getPlayingThread() {
        return () -> {
            try {
                speakers = AudioSystem.getSourceDataLine(format);
                speakers.open(format);
                byte[] temp = new byte[1];

                int index = 0;
                while (!this.threadPlay.isInterrupted()) {
                    if (isPlaying) {
                        if (wasPlayingStarted) {
                            wasPlayingStarted = false;
                            wasPlaying = true;

                            speakers.start();
                        }

                        if (streamReader.read(temp, 1) > 0) {
                            byte chunkSize = temp[0];
                            byte[] currentRawTrack = new byte[chunkSize];
                            int readResult = streamReader.read(currentRawTrack, chunkSize);
                            if (readResult < chunkSize) {
                                throw new IOException("Stream is unexpectedly finished.");
                            }

                            for (AudioServiceStreamFilter filter : this.filters) {
                                currentRawTrack = filter.decode(currentRawTrack);
                            }
                            speakers.write(currentRawTrack, 0, currentRawTrack.length);
                            index++;
                        } else {
                            isPlaying = false;
                            index = 0;
                        }

                    } else if (wasPlaying) {
                        wasPlaying = false;
                        if (isPlayingStopped) {
                            if (this.audioServiceCallbackPlayingStopped != null) {
                                this.audioServiceCallbackPlayingStopped.callback("");
                            }
                        } else {
                            if (this.audioServiceCallbackPlayingFinished != null) {
                                this.audioServiceCallbackPlayingFinished.callback("");
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (this.audioServiceCallbackPlayingFailed != null) {
                    this.audioServiceCallbackPlayingFailed.callback(e.getMessage());
                }
            } finally {
                speakers.stop();
                speakers.close();
            }
            return null;
        };
    }
}
