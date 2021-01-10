package org.deesthortered.direct.telephonia.service.audioservice;

import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceCallback;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceStreamFilter;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceStreamReader;

public interface AudioServiceReproducer extends AutoCloseable {

    void initializeService(AudioServiceCallback audioServiceCallbackPlayingFailed,
                           AudioServiceCallback audioServiceCallbackPlayingStopped,
                           AudioServiceCallback audioServiceCallbackPlayingFinished);

    void close();

    void setStreamReader(AudioServiceStreamReader streamReader);

    void addFilter(AudioServiceStreamFilter filter);

    void startPlayingRecord();

    void stopPlayingRecord();
}
