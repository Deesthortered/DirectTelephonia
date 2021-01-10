package org.deesthortered.direct.telephonia.service;

import org.apache.commons.lang3.ArrayUtils;
import org.deesthortered.direct.telephonia.service.audioservice.AudioServiceReceiver;
import org.deesthortered.direct.telephonia.service.audioservice.AudioServiceReproducer;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceCallback;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceStreamFilter;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceStreamReader;
import org.deesthortered.direct.telephonia.service.audioservice.impl.AudioServiceStreamWriter;
import org.deesthortered.direct.telephonia.service.cypher.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;

@Component
public class AudioStreamingService {

    private static final Integer maxPacketSize = 508; // 65507 - 1432 - 508

    @Value("${audio.host}")
    private volatile String host;
    @Value("${audio.port}")
    private volatile Integer serverPort;
    @Value("${audio.port}")
    private volatile Integer clientPort;

    private volatile InetAddress address;

    private volatile Thread serverThread;
    private volatile Thread clientThread;
    private volatile DatagramSocket serverSocket;
    private volatile DatagramSocket clientSocket;
    private final ConcurrentLinkedQueue<List<Byte>> serverReceivedPackets;
    private final ConcurrentLinkedQueue<List<Byte>> clientSendingPackets;

    private volatile boolean isReceiverInitialized;
    private volatile boolean isReproducerInitialized;
    private final AudioServiceReceiver audioServiceReceiver;
    private final AudioServiceReproducer audioServiceReproducer;
    private AudioServiceCallback audioServiceCallbackRecordingFailed;
    private AudioServiceCallback audioServiceCallbackPlayingFailed;
    private AudioServiceCallback audioServiceCallbackPlayingStopped;
    private AudioServiceCallback audioServiceCallbackPlayingFinished;
    private AudioStreamingServiceCallback audioStreamingServiceReceivingFailed;
    private AudioStreamingServiceCallback audioStreamingServiceSendingFailed;

    public AudioStreamingService(AudioServiceReceiver audioServiceReceiver,
                                 AudioServiceReproducer audioServiceReproducer) {
        this.serverReceivedPackets = new ConcurrentLinkedQueue<>();
        this.clientSendingPackets = new ConcurrentLinkedQueue<>();

        this.isReceiverInitialized = false;
        this.isReproducerInitialized = false;
        this.audioServiceReceiver = audioServiceReceiver;
        this.audioServiceReproducer = audioServiceReproducer;
        this.audioServiceReceiver.setStreamWriter(getAudioServiceStreamWriter());
        this.audioServiceReproducer.setStreamReader(getAudioServiceStreamReader());

        // Cyphering Filter
        this.audioServiceReceiver.addFilter(getCypheringFilter());
        this.audioServiceReproducer.addFilter(getCypheringFilter());
    }

    public void createServer() {
        if (!this.isReceiverInitialized) {
            audioServiceReceiver.initializeService(
                    audioServiceCallbackRecordingFailed);

            this.isReceiverInitialized = true;
        }

        this.serverThread = new Thread(new FutureTask<>(getInputAudioStreamingThread()));
        this.serverThread.setDaemon(true);
        this.serverThread.start();
    }

    public void connectToServer() {
        if (!this.isReproducerInitialized) {
            audioServiceReproducer.initializeService(
                    audioServiceCallbackPlayingFailed,
                    audioServiceCallbackPlayingStopped,
                    audioServiceCallbackPlayingFinished);

            this.isReproducerInitialized = true;
        }

        this.clientThread = new Thread(new FutureTask<>(getOutputAudioStreamingThread()));
        this.clientThread.setDaemon(true);
        this.clientThread.start();
    }

    public void stopService() {
        this.audioServiceReproducer.close();
        this.audioServiceReceiver.close();
        this.serverThread.interrupt();
        this.clientThread.interrupt();
        this.serverSocket.close();
        this.clientSocket.close();
    }


    private Callable<Void> getInputAudioStreamingThread() {
        return () -> {
            try {
                address = InetAddress.getByName(this.host);
                this.serverSocket = new DatagramSocket(this.serverPort);
                //this.serverPort = this.serverSocket.getPort();

                while (!this.serverThread.isInterrupted()) {
                    byte[] buffer = new byte[maxPacketSize];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    serverSocket.receive(response);
                    serverReceivedPackets.add(convertArrayToList(buffer));
                }
            } catch (Exception e) {
                e.printStackTrace();
                audioStreamingServiceReceivingFailed.callback(e.getMessage());
            }
            return null;
        };
    }

    private Callable<Void> getOutputAudioStreamingThread() {
        return () -> {
            try {
                address = InetAddress.getByName(this.host);
                this.clientSocket = new DatagramSocket(this.clientPort);

                while (!this.clientThread.isInterrupted()) {
                    while (!this.clientSendingPackets.isEmpty()) {
                        byte[] buffer = convertListToArray(this.clientSendingPackets.poll());
                        DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, clientPort);
                        clientSocket.send(request);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                audioStreamingServiceSendingFailed.callback(e.getMessage());
            }
            return null;
        };
    }

    private AudioServiceStreamReader getAudioServiceStreamReader() {
        return new AudioServiceStreamReader() {

            private byte[] buffer = new byte[0];
            private int currentByte = 0;

            @Override
            public int read(byte[] whereToWrite, int bytesCount) {
                int i = 0;
                while (i < bytesCount) {
                    for (; (i < bytesCount) && (currentByte + i < buffer.length); i++) {
                        whereToWrite[i] = buffer[currentByte + i];
                    }
                    currentByte += i;
                    if (currentByte == buffer.length) {
                        currentByte = 0;
                        if (serverReceivedPackets.isEmpty()) {
                            Arrays.fill(buffer, (byte) 0);
                            System.out.println("No packets to reproduce!!!");
                        } else {
                            buffer = convertListToArray(serverReceivedPackets.poll());
                        }
                    }
                }
                return i;
            }
        };
    }

    private AudioServiceStreamWriter getAudioServiceStreamWriter() {
        return new AudioServiceStreamWriter() {

            private final byte[] buffer = new byte[AudioStreamingService.maxPacketSize];
            private int currentByte = 0;

            @Override
            public void write(byte[] data) {
                int i = 0;
                while (i < data.length) {
                    for (; (i < data.length) && (currentByte + i < buffer.length); i++) {
                        buffer[currentByte + i] = data[i];
                    }
                    currentByte += i;
                    if (currentByte == buffer.length) {
                        currentByte = 0;
                        clientSendingPackets.add(convertArrayToList(buffer));
                    }
                }
            }
        };
    }

    private AudioServiceStreamFilter getCypheringFilter() {
        return new AudioServiceStreamFilter() {
            private final String key = "ThisIsASecretKey";

            @Override
            public byte[] encode(byte[] data) throws GeneralSecurityException {
                return AES.encrypt(key, data);
            }

            @Override
            public byte[] decode(byte[] data) throws GeneralSecurityException {
                return AES.decrypt(key, data);
            }
        };
    }


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public Integer getClientPort() {
        return clientPort;
    }

    public void setClientPort(Integer clientPort) {
        this.clientPort = clientPort;
    }


    public interface AudioStreamingServiceCallback {
        void callback(String message);
    }

    public void setAudioServiceCallbackRecordingFailed(
            AudioServiceCallback audioServiceCallbackRecordingFailed) {
        this.audioServiceCallbackRecordingFailed = audioServiceCallbackRecordingFailed;
    }

    public void setAudioServiceCallbackPlayingFailed(
            AudioServiceCallback audioServiceCallbackPlayingFailed) {
        this.audioServiceCallbackPlayingFailed = audioServiceCallbackPlayingFailed;
    }

    public void setAudioServiceCallbackPlayingStopped(
            AudioServiceCallback audioServiceCallbackPlayingStopped) {
        this.audioServiceCallbackPlayingStopped = audioServiceCallbackPlayingStopped;
    }

    public void setAudioServiceCallbackPlayingFinished(
            AudioServiceCallback audioServiceCallbackPlayingFinished) {
        this.audioServiceCallbackPlayingFinished = audioServiceCallbackPlayingFinished;
    }

    public void setAudioStreamingServiceReceivingFailed(AudioStreamingServiceCallback audioStreamingServiceReceivingFailed) {
        this.audioStreamingServiceReceivingFailed = audioStreamingServiceReceivingFailed;
    }

    public void setAudioStreamingServiceSendingFailed(AudioStreamingServiceCallback audioStreamingServiceSendingFailed) {
        this.audioStreamingServiceSendingFailed = audioStreamingServiceSendingFailed;
    }


    public static byte[] convertListToArray(List<Byte> list) {
        byte[] buffer = new byte[list.size()];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = list.get(i);
        }
        return buffer;
    }

    public static List<Byte> convertArrayToList(byte[] buffer) {
        return Arrays.asList(ArrayUtils.toObject(buffer));
    }
}
