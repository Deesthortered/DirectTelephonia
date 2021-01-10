package org.deesthortered.direct.telephonia.service.audioservice.impl;

public interface AudioServiceStreamFilter {
    byte[] encode(byte[] data) throws Exception;
    byte[] decode(byte[] data) throws Exception;
}
