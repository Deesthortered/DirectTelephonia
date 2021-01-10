package org.deesthortered.direct.telephonia.service.audioservice.impl;

public interface AudioServiceStreamReader {
    int read(byte[] whereToWrite, int bytesCount);
}
