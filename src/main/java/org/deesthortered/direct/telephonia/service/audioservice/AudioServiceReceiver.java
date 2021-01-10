package org.deesthortered.direct.telephonia.service.audioservice;

import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceCallback;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceStreamFilter;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceStreamWriter;

public interface AudioServiceReceiver extends AutoCloseable {

    void initializeService(AudioServiceCallback audioServiceCallbackRecordingFailed);

    void close();

    void setStreamWriter(AudioServiceStreamWriter streamWriter);

    void addFilter(AudioServiceStreamFilter filter);

    void startRecording();

    void stopRecording();
}
