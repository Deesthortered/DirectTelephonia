package org.deesthortered.direct.telephonia.service;

import org.deesthortered.direct.telephonia.scene.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;

@Component
@PropertySource("classpath:application.properties")
public class MessageService {

    @Value("${message.server.host}")
    private String serverHost;
    @Value("${message.server.port}")
    private int serverPort;

    private Boolean isLaunched = false;
    private Boolean isConnected = false;
    private Boolean isServer;

    private Thread listeningThread;
    private MessageServiceCallback listeningCallbackSuccess;
    private MessageServiceCallback listeningCallbackFailure;
    private volatile ServerSocket serverSocket;

    private Thread connectionThread;
    private MessageServiceCallback connectionCallbackSuccessStart;
    private MessageServiceCallback connectionCallbackSuccessFinish;
    private MessageServiceCallback connectionCallbackFailure;
    private volatile Socket clientSocket;

    private Thread messageSenderThread;
    private MessageServiceCallback messageSenderCallbackSuccess;
    private MessageServiceCallback messageSenderCallbackFailure;

    private Thread messageReceiverThread;
    private MessageServiceCallback messageReceiverCallbackSuccess;
    private MessageServiceCallback messageReceiverCallbackFailure;

    private MessageServiceCallback messageReceiveCallback;
    private volatile ConcurrentLinkedQueue<String> messagesForSending;

    public MessageService() {
        this.messagesForSending = new ConcurrentLinkedQueue<>();
    }

    public void createServer() throws CustomException {
        if (this.isLaunched) {
            throw new CustomException("The " + (this.isServer ? "server" : "client") + " is already launched, needs to stop.");
        }

        this.isLaunched = true;
        this.isConnected = false;
        this.isServer = true;

        this.listeningThread = new Thread(new FutureTask<>(getServerListeningThread()));
        this.listeningThread.setDaemon(true);
        this.listeningThread.start();
    }

    public void connectToServer() throws CustomException {
        if (this.isLaunched) {
            throw new CustomException("The " + (this.isServer ? "server" : "client") + " is already launched, needs to stop.");
        }

        this.isLaunched = true;
        this.isConnected = false;
        this.isServer = false;

        this.connectionThread = new Thread(new FutureTask<>(getClientConnectionThread()));
        this.connectionThread.setDaemon(true);
        this.connectionThread.start();
    }

    public void stopService() throws CustomException, IOException {
        if (!this.isLaunched) {
            throw new CustomException("The " + (this.isServer ? "server" : "client") + " is already stopped.");
        }

        if (isServer) {
            if (isConnected) {
                this.messageSenderThread.interrupt();
                this.messageReceiverThread.interrupt();
            } else {
                this.serverSocket.close();
            }
        } else {
            if (isConnected) {
                this.messageSenderThread.interrupt();
                this.messageReceiverThread.interrupt();
            } else {
                this.clientSocket.close();
            }
        }
    }

    public void sendMessage(String message) {
        this.messagesForSending.add(message);
    }


    private Callable<Void> getServerListeningThread() {
        return () -> {
            try {
                this.serverSocket = new ServerSocket(0);
                this.serverHost = this.serverSocket.getInetAddress().getHostAddress();
                this.serverPort = this.serverSocket.getLocalPort();

                this.listeningCallbackSuccess.handleMessage("");
                this.clientSocket = serverSocket.accept();
                this.isConnected = true;

                this.messageSenderThread = new Thread(new FutureTask<>(getMessageSenderThread()));
                this.messageReceiverThread = new Thread(new FutureTask<>(getMessageReceiverThread()));
                this.messageSenderThread.start();
                this.messageReceiverThread.start();
            } catch (Exception e) {
                this.listeningCallbackFailure.handleMessage(e.getMessage());
                this.isLaunched = false;
                this.isConnected = false;
            }
            return null;
        };
    }

    private Callable<Void> getClientConnectionThread() {
        return () -> {
            try {
                this.connectionCallbackSuccessStart.handleMessage("");

                InetAddress inetAddress = InetAddress.getByName(this.serverHost);
                SocketAddress socketAddress = new InetSocketAddress(inetAddress, this.serverPort);
                this.clientSocket = new Socket();
                this.clientSocket.bind(socketAddress);
                this.clientSocket.connect(socketAddress);
                this.isConnected = true;

                this.messageSenderThread = new Thread(new FutureTask<>(getMessageSenderThread()));
                this.messageReceiverThread = new Thread(new FutureTask<>(getMessageReceiverThread()));
                this.messageSenderThread.start();
                this.messageReceiverThread.start();

                this.connectionCallbackSuccessFinish.handleMessage("");
            } catch (Exception e) {
                this.connectionCallbackFailure.handleMessage(e.getMessage());
                this.isLaunched = false;
                this.isConnected = false;
            }
            return null;
        };
    }

    private Callable<Void> getMessageSenderThread() {
        return () -> {
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);
                while (!this.messageSenderThread.isInterrupted()) {
                    if (!this.messagesForSending.isEmpty()) {
                        out.println(this.messagesForSending.poll());
                    }
                }

                this.messagesForSending.clear();
                out.close();

                this.messageSenderCallbackSuccess.handleMessage("");
            } catch (Exception e) {
                this.messageSenderCallbackFailure.handleMessage(e.getMessage());
            }
            return null;
        };
    }

    private Callable<Void> getMessageReceiverThread() {
        return () -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String temp;

                while (!this.messageSenderThread.isInterrupted()) {
                    temp = in.readLine();
                    messageReceiveCallback.handleMessage(temp);
                }

                in.close();
                this.clientSocket.close();
                this.clientSocket = null;

                this.messageReceiverCallbackSuccess.handleMessage("");
            } catch (Exception e) {
                this.messageReceiverCallbackFailure.handleMessage(e.getMessage());
            }
            return null;
        };
    }


    public void setServerHost(String host) {
        this.serverHost = host;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerPort(int port) {
        this.serverPort = port;
    }

    public int getServerPort() {
        return serverPort;
    }

    public interface MessageServiceCallback {
        void handleMessage(String message);
    }

    public void setListeningCallbackSuccess(MessageServiceCallback listeningCallbackSuccess) {
        this.listeningCallbackSuccess = listeningCallbackSuccess;
    }

    public void setListeningCallbackFailure(MessageServiceCallback listeningCallbackFailure) {
        this.listeningCallbackFailure = listeningCallbackFailure;
    }

    public void setConnectionCallbackSuccessStart(MessageServiceCallback connectionCallbackSuccessStart) {
        this.connectionCallbackSuccessStart = connectionCallbackSuccessStart;
    }

    public void setConnectionCallbackSuccessFinish(MessageServiceCallback connectionCallbackSuccessFinish) {
        this.connectionCallbackSuccessFinish = connectionCallbackSuccessFinish;
    }

    public void setConnectionCallbackFailure(MessageServiceCallback connectionCallbackFailure) {
        this.connectionCallbackFailure = connectionCallbackFailure;
    }

    public void setMessageSenderCallbackSuccess(MessageServiceCallback messageSenderCallbackSuccess) {
        this.messageSenderCallbackSuccess = messageSenderCallbackSuccess;
    }

    public void setMessageSenderCallbackFailure(MessageServiceCallback messageSenderCallbackFailure) {
        this.messageSenderCallbackFailure = messageSenderCallbackFailure;
    }

    public void setMessageReceiverCallbackSuccess(MessageServiceCallback messageReceiverCallbackSuccess) {
        this.messageReceiverCallbackSuccess = messageReceiverCallbackSuccess;
    }

    public void setMessageReceiverCallbackFailure(MessageServiceCallback messageReceiverCallbackFailure) {
        this.messageReceiverCallbackFailure = messageReceiverCallbackFailure;
    }

    public void setMessageReceiverCallback(MessageServiceCallback messageReceiveCallback) {
        this.messageReceiveCallback = messageReceiveCallback;
    }
}
