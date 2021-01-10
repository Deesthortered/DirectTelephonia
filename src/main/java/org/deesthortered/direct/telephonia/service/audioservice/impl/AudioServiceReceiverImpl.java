package org.deesthortered.direct.telephonia.service.audioservice.impl;

import org.deesthortered.direct.telephonia.service.audioservice.AudioServiceReceiver;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

@Component
public class AudioServiceReceiverImpl implements AudioServiceReceiver {

    private AudioServiceCallback audioServiceCallbackRecordingFailed;
    private List<AudioServiceStreamFilter> filters;
    private AudioServiceStreamWriter streamWriter;

    private volatile boolean isRecording;
    private volatile boolean wasRecording;
    private volatile boolean wasRecordingStarted;
    private Thread threadRecord;

    private volatile AudioFormat format;
    private volatile TargetDataLine microphone;
    private int inputChunkSize;


    public AudioServiceReceiverImpl() {
    }

    @Override
    public void initializeService(AudioServiceCallback audioServiceCallbackRecordingFailed) {
        this.audioServiceCallbackRecordingFailed = audioServiceCallbackRecordingFailed;
        this.filters = new ArrayList<>();

        this.isRecording = false;
        this.wasRecording = false;
        this.wasRecordingStarted = false;

        this.format = new AudioFormat(16000.0f, 16, 2, true, false);
        this.inputChunkSize = format.getFrameSize();

        this.threadRecord = new Thread(new FutureTask<>(getRecordThread()));
        this.threadRecord.setDaemon(true);
        this.threadRecord.start();
    }

    @Override
    public void close() {
        this.threadRecord.interrupt();
    }

    @Override
    public void setStreamWriter(AudioServiceStreamWriter streamWriter) {
        this.streamWriter = streamWriter;
    }

    @Override
    public void addFilter(AudioServiceStreamFilter filter) {
        this.filters.add(filter);
    }

    @Override
    public void startRecording() {
        this.isRecording = true;
        this.wasRecordingStarted = true;
    }

    @Override
    public void stopRecording() {
        this.isRecording = false;
    }

    private Callable<Void> getRecordThread() {
        return () -> {
            try {
                microphone = AudioSystem.getTargetDataLine(format);
                byte[] rawData = new byte[inputChunkSize];
                byte[] filteredDataSize = new byte[1];
                byte[] filteredData;
                int readBytesCount;

                while (!this.threadRecord.isInterrupted()) {
                    if (isRecording) {
                        if (this.wasRecordingStarted) {
                            this.wasRecording = true;
                            this.wasRecordingStarted = false;

                            microphone.open(format);
                            microphone.start();
                        }

                        readBytesCount = microphone.read(rawData, 0, inputChunkSize);

                        filteredData = Arrays.copyOf(rawData, rawData.length);
                        for (AudioServiceStreamFilter filter : this.filters) {
                            filteredData = filter.encode(filteredData);
                        }
                        filteredDataSize[0] = ((byte) filteredData.length);
                        streamWriter.write(filteredDataSize);
                        streamWriter.write(filteredData);
                    } else if (wasRecording) {
                        this.wasRecording = false;

                        microphone.stop();
                        microphone.drain();
                        do {
                            readBytesCount = microphone.read(rawData, 0, inputChunkSize);

                            filteredData = Arrays.copyOf(rawData, rawData.length);
                            for (AudioServiceStreamFilter filter : this.filters) {
                                filteredData = filter.encode(filteredData);
                            }
                            filteredDataSize[0] = ((byte) filteredData.length);
                            streamWriter.write(filteredDataSize);
                            streamWriter.write(filteredData);
                        } while (readBytesCount > 0);
                        microphone.close();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (this.audioServiceCallbackRecordingFailed != null) {
                    this.audioServiceCallbackRecordingFailed.callback(e.getMessage());
                }
            } finally {
                microphone.stop();
                microphone.drain();
                microphone.close();
            }
            return null;
        };
    }
}
