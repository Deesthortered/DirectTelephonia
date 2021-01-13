package org.deesthortered.direct.telephonia.service;

import org.apache.commons.collections4.QueueUtils;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.ArrayUtils;
import org.deesthortered.direct.telephonia.scene.exception.CustomException;
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
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class AudioStreamingService {

    private static final int maxPacketSize = (16+1)*4; // 65507 - 1432 - 508
    private static final int queueMaxSizeForReceivingPackets = 3;
    private static final int queueMaxSizeForSendingPackets = 3;

    @Value("${audio.server.host}")
    private volatile String serverHost;
    @Value("${audio.server.port}")
    private volatile Integer serverPort;
    @Value("${audio.client.host}")
    private volatile String clientHost;
    @Value("${audio.client.port}")
    private volatile Integer clientPort;

    private volatile InetAddress address;

    private volatile Thread serverThread;
    private volatile Thread clientThread;
    private volatile DatagramSocket serverSocket;
    private volatile DatagramSocket clientSocket;

    private final Queue<List<Byte>> serverReceivedPackets;
    private final Queue<List<Byte>> clientSendingPackets;

    private volatile boolean isAutoDefineNetworkData;
    private volatile boolean isLaunched;
    private volatile boolean isServerLaunched;
    private volatile boolean isClientLaunched;
    private final ReentrantLock lockLaunched;
    private final AudioServiceReceiver audioServiceReceiver;
    private final AudioServiceReproducer audioServiceReproducer;
    private AudioServiceCallback audioServiceCallbackRecordingFailed;
    private AudioServiceCallback audioServiceCallbackPlayingFailed;
    private AudioServiceCallback audioServiceCallbackPlayingStopped;
    private AudioServiceCallback audioServiceCallbackPlayingFinished;
    private AudioStreamingServiceCallback audioStreamingCallbackServiceListeningSuccessful;
    private AudioStreamingServiceCallback audioStreamingCallbackServiceSendingSuccessful;
    private AudioStreamingServiceCallback audioStreamingCallbackServiceReceivingFailed;
    private AudioStreamingServiceCallback audioStreamingCallbackServiceSendingFailed;
    private AudioStreamingServiceCallback audioStreamingCallbackServiceServerFinishedSuccessfully;
    private AudioStreamingServiceCallback audioStreamingCallbackServiceClientFinishedSuccessfully;
    private AudioStreamingServiceCallback audioStreamingCallbackServiceFullyFinishedSuccessfully;

    public AudioStreamingService(AudioServiceReceiver audioServiceReceiver,
                                 AudioServiceReproducer audioServiceReproducer) {
        this.serverReceivedPackets = QueueUtils.synchronizedQueue(new CircularFifoQueue<>(queueMaxSizeForReceivingPackets));
        this.clientSendingPackets = QueueUtils.synchronizedQueue(new CircularFifoQueue<>(queueMaxSizeForSendingPackets));

        this.isLaunched = false;
        this.isServerLaunched = false;
        this.isClientLaunched = false;
        this.lockLaunched = new ReentrantLock();
        this.audioServiceReceiver = audioServiceReceiver;
        this.audioServiceReproducer = audioServiceReproducer;
        this.audioServiceReceiver.setStreamWriter(getAudioServiceStreamWriter());
        this.audioServiceReproducer.setStreamReader(getAudioServiceStreamReader());
    }

    public void startServer(boolean autoDefineNetworkData) throws CustomException {
        if (this.isServerLaunched) {
            throw new CustomException("The audio service server is started!");
        }

        audioServiceReproducer.initializeService(
                audioServiceCallbackPlayingFailed,
                audioServiceCallbackPlayingStopped,
                audioServiceCallbackPlayingFinished);
        audioServiceReceiver.initializeService(audioServiceCallbackRecordingFailed);
        this.audioServiceReceiver.addFilter(AudioStreamingService.getCypheringFilter());
        this.audioServiceReproducer.addFilter(AudioStreamingService.getCypheringFilter());

        this.isAutoDefineNetworkData = autoDefineNetworkData;

        this.serverThread = new Thread(new FutureTask<>(getInputAudioStreamingThread()));
        this.serverThread.setDaemon(true);
        this.serverThread.start();
    }

    public void connectToServer() throws CustomException {
        if (this.isClientLaunched) {
            throw new CustomException("The audio service is started!");
        }
        if (!this.isServerLaunched) {
            throw new CustomException("Audio service server must be started!");
        }

        this.clientThread = new Thread(new FutureTask<>(getOutputAudioStreamingThread()));
        this.clientThread.setDaemon(true);
        this.clientThread.start();
    }

    public void stopService() throws CustomException {
        if (!this.isServerLaunched) {
            throw new CustomException("Service is not launched");
        }
        if (this.isServerLaunched) {
            this.serverThread.interrupt();
            this.serverSocket.close();
        }
        if (this.isClientLaunched) {
            this.clientThread.interrupt();
            this.clientSocket.close();
        }
    }


    private Callable<Void> getInputAudioStreamingThread() {
        return () -> {
            boolean isFailed = false;
            try {
                isServerLaunched = true;
                isLaunched = true;

                address = InetAddress.getByName(this.serverHost);
                if (isAutoDefineNetworkData) {
                    this.serverSocket = new DatagramSocket(0);
                    this.serverHost = this.serverSocket.getLocalAddress().getHostAddress();
                    this.serverPort = this.serverSocket.getLocalPort();
                } else {
                    this.serverSocket = new DatagramSocket(this.serverPort);
                }

                audioStreamingCallbackServiceListeningSuccessful.callback("");

                boolean firstPacketHere = false;
                while (!this.serverThread.isInterrupted()) {
                    byte[] buffer = new byte[maxPacketSize];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    serverSocket.receive(response);

                    serverReceivedPackets.add(convertArrayToList(buffer));
                    if (!firstPacketHere) {
                        firstPacketHere = true;
                        this.audioServiceReproducer.startPlayingRecord();
                    }
                }
            } catch (Exception e) {
                if (!e.getMessage().equalsIgnoreCase("socket closed")) {
                    e.printStackTrace();
                }
                isFailed = true;
                this.audioStreamingCallbackServiceReceivingFailed.callback(e.getMessage());
            } finally {
                this.audioServiceReproducer.stopPlayingRecord();
                this.audioServiceReproducer.close();
                this.serverReceivedPackets.clear();
                if (this.serverSocket != null && !this.serverSocket.isClosed()) {
                    this.serverSocket.close();
                }

                if (!isFailed) {
                    audioStreamingCallbackServiceServerFinishedSuccessfully.callback("");
                }

                this.lockLaunched.lock();
                this.isServerLaunched = false;
                if (!this.isClientLaunched) {
                    this.isLaunched = false;
                    this.audioStreamingCallbackServiceFullyFinishedSuccessfully.callback("");
                }
                this.lockLaunched.unlock();
            }
            return null;
        };
    }

    private Callable<Void> getOutputAudioStreamingThread() {
        return () -> {
            boolean isFailed = false;
            try {
                this.isClientLaunched = true;
                address = InetAddress.getByName(this.clientHost);
                this.clientSocket = new DatagramSocket(this.clientPort);

                boolean firstPacketSent = false;
                this.audioServiceReceiver.startRecording();
                while (!this.clientThread.isInterrupted()) {
                    while (!this.clientSendingPackets.isEmpty()) {
                        byte[] buffer = convertListToArray(this.clientSendingPackets.poll());
                        DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, clientPort);
                        clientSocket.send(request);

                        if (!firstPacketSent) {
                            firstPacketSent = true;
                            audioStreamingCallbackServiceSendingSuccessful.callback("");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                isFailed = true;
                this.audioStreamingCallbackServiceSendingFailed.callback(e.getMessage());
            } finally {
                this.audioServiceReceiver.stopRecording();
                this.audioServiceReceiver.close();
                this.clientSendingPackets.clear();
                if (this.clientSocket != null && !this.clientSocket.isClosed()) {
                    this.clientSocket.close();
                }

                if (!isFailed) {
                    audioStreamingCallbackServiceClientFinishedSuccessfully.callback("");
                }

                this.lockLaunched.lock();
                this.isClientLaunched = false;
                if (!this.isServerLaunched) {
                    this.isLaunched = false;
                    this.audioStreamingCallbackServiceFullyFinishedSuccessfully.callback("");
                }
                this.lockLaunched.unlock();
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

                        // TODO: implement gap resolver (smooth, not hard)
                        if (serverReceivedPackets.isEmpty()) {
                            Arrays.fill(buffer, (byte) 0);
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

    private static AudioServiceStreamFilter getCypheringFilter() {
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


    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public String getClientHost() {
        return clientHost;
    }

    public void setClientHost(String clientHost) {
        this.clientHost = clientHost;
    }

    public Integer getClientPort() {
        return clientPort;
    }

    public void setClientPort(Integer clientPort) {
        this.clientPort = clientPort;
    }

    public Boolean isLaunched() {
        return isLaunched;
    }

    public boolean isServerLaunched() {
        return isServerLaunched;
    }

    public boolean isClientLaunched() {
        return isClientLaunched;
    }

    public interface AudioStreamingServiceCallback {
        void callback(String message);
    }

    public void setAudioServiceCallbackRecordingFailed(
            AudioServiceCallback uiAudioServiceCallbackRecordingFailed) {
        this.audioServiceCallbackRecordingFailed = (message) -> {
            try {
                stopService();
            } catch (CustomException e) {
                e.printStackTrace();
            }
            uiAudioServiceCallbackRecordingFailed.callback(message);
        };;
    }

    public void setAudioServiceCallbackPlayingFailed(
            AudioServiceCallback uiAudioServiceCallbackPlayingFailed) {
        this.audioServiceCallbackPlayingFailed = (message) -> {
            try {
                stopService();
            } catch (CustomException e) {
                e.printStackTrace();
            }
            uiAudioServiceCallbackPlayingFailed.callback(message);
        };;
    }

    public void setAudioServiceCallbackPlayingStopped(
            AudioServiceCallback uiAudioServiceCallbackPlayingStopped) {
        this.audioServiceCallbackPlayingStopped = (message) -> {
            uiAudioServiceCallbackPlayingStopped.callback(message);
        };;
    }

    public void setAudioServiceCallbackPlayingFinished(
            AudioServiceCallback uiAudioServiceCallbackPlayingFinished) {
        this.audioServiceCallbackPlayingFinished = (message) -> {
            uiAudioServiceCallbackPlayingFinished.callback(message);
        };
    }

    public void setAudioStreamingCallbackServiceListeningSuccessful(AudioStreamingServiceCallback audioStreamingCallbackServiceListeningSuccessful) {
        this.audioStreamingCallbackServiceListeningSuccessful = audioStreamingCallbackServiceListeningSuccessful;
    }

    public void setAudioStreamingCallbackServiceSendingSuccessful(AudioStreamingServiceCallback audioStreamingCallbackServiceSendingSuccessful) {
        this.audioStreamingCallbackServiceSendingSuccessful = audioStreamingCallbackServiceSendingSuccessful;
    }

    public void setAudioStreamingCallbackServiceReceivingFailed(AudioStreamingServiceCallback audioStreamingCallbackServiceReceivingFailed) {
        this.audioStreamingCallbackServiceReceivingFailed = audioStreamingCallbackServiceReceivingFailed;
    }

    public void setAudioStreamingCallbackServiceSendingFailed(AudioStreamingServiceCallback audioStreamingCallbackServiceSendingFailed) {
        this.audioStreamingCallbackServiceSendingFailed = audioStreamingCallbackServiceSendingFailed;
    }

    public void setAudioStreamingCallbackServiceServerFinishedSuccessfully(AudioStreamingServiceCallback audioStreamingCallbackServiceServerFinishedSuccessfully) {
        this.audioStreamingCallbackServiceServerFinishedSuccessfully = audioStreamingCallbackServiceServerFinishedSuccessfully;
    }

    public void setAudioStreamingCallbackServiceClientFinishedSuccessfully(AudioStreamingServiceCallback audioStreamingCallbackServiceClientFinishedSuccessfully) {
        this.audioStreamingCallbackServiceClientFinishedSuccessfully = audioStreamingCallbackServiceClientFinishedSuccessfully;
    }

    public void setAudioStreamingCallbackServiceFullyFinishedSuccessfully(AudioStreamingServiceCallback audioStreamingCallbackServiceFullyFinishedSuccessfully) {
        this.audioStreamingCallbackServiceFullyFinishedSuccessfully = audioStreamingCallbackServiceFullyFinishedSuccessfully;
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
