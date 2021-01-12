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
import java.util.concurrent.locks.ReentrantLock;

@Component
@PropertySource("classpath:application.properties")
public class MessageService {

    @Value("${message.host}")
    private volatile String serverHost;
    @Value("${message.port}")
    private volatile int serverPort;
    @Value("${message.finish-key-word}")
    private volatile String serverFinishKeyWord;

    private volatile Boolean isAutoDefineNetworkData;
    private volatile Boolean isServer;
    private volatile Boolean isLaunched = false;
    private volatile Boolean isConnected = false;
    private volatile Boolean isClosing = false;
    private volatile Boolean isFirstGone = false;
    private volatile Boolean isNeedToDoFinalCallback = false;
    private volatile Boolean isExceptionWasThrown = false;
    private volatile ReentrantLock closingLock = new ReentrantLock();
    private volatile ReentrantLock finalCallbackLock = new ReentrantLock();
    private volatile ReentrantLock exceptionThrowingLock = new ReentrantLock();

    private volatile Thread listeningThread;
    private volatile ServerSocket serverSocket;
    private MessageServiceCallback listeningCallbackSuccess;
    private MessageServiceCallback listeningCallbackFailure;
    private MessageServiceCallback listeningCallbackFinish;

    private volatile Thread connectionThread;
    private volatile Socket clientSocket;
    private MessageServiceCallback connectionCallbackSuccess;
    private MessageServiceCallback connectionCallbackFailure;
    private MessageServiceCallback connectionCallbackFinish;

    private volatile Thread messageSenderThread;
    private volatile Thread messageReceiverThread;
    private MessageServiceCallback messageSuccessfullyFinishedConnectionCallback;
    private MessageServiceCallback messageFailFinishedConnectionCallback;

    private MessageServiceCallback messageReceiveCallback;
    private volatile ConcurrentLinkedQueue<String> messagesForSending = new ConcurrentLinkedQueue<>();
    private volatile PrintWriter streamSender;
    private volatile BufferedReader streamReceiver;


    public MessageService() {
    }

    public void createServer(boolean autoDefineNetworkData) throws CustomException {
        if (this.isLaunched) {
            throw new CustomException("The " + (this.isServer ? "server" : "client") + " is already launched, needs to stop.");
        }

        this.isAutoDefineNetworkData = autoDefineNetworkData;
        this.isLaunched = true;
        this.isConnected = false;
        this.isServer = true;
        this.isClosing = false;
        this.isFirstGone = false;
        this.isNeedToDoFinalCallback = false;
        this.isExceptionWasThrown = false;

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
        this.isClosing = false;
        this.isFirstGone = false;
        this.isNeedToDoFinalCallback = false;
        this.isExceptionWasThrown = false;

        this.connectionThread = new Thread(new FutureTask<>(getClientConnectionThread()));
        this.connectionThread.setDaemon(true);
        this.connectionThread.start();
    }

    public void stopService() throws CustomException, IOException {
        if (!this.isLaunched) {
            throw new CustomException("The " + (this.isServer ? "server" : "client") + " is already stopped.");
        }

        if (isConnected) {
            this.messageSenderThread.interrupt();
        } else {
            if (isServer) {
                this.serverSocket.close();
            } else {
                this.clientSocket.close();
            }
        }
    }

    public void sendMessage(String message) throws CustomException {
        if (!this.isLaunched || !this.isConnected) {
            throw new CustomException("The " + (this.isServer ? "server" : "client") + " is not running for messaging.");
        }
        this.messagesForSending.add(message);
    }


    private Callable<Void> getServerListeningThread() {
        return () -> {
            try {
                if (isAutoDefineNetworkData) {
                    this.serverSocket = new ServerSocket(0);
                    this.serverHost = this.serverSocket.getInetAddress().getHostAddress();
                    this.serverPort = this.serverSocket.getLocalPort();
                } else {
                    this.serverSocket = new ServerSocket(this.serverPort);
                }

                this.listeningCallbackSuccess.handleMessage("");
                this.clientSocket = serverSocket.accept();
                this.serverSocket.close();
                this.isConnected = true;

                this.messageSenderThread = new Thread(new FutureTask<>(getMessageSenderThread()));
                this.messageReceiverThread = new Thread(new FutureTask<>(getMessageReceiverThread()));
                this.messageSenderThread.setDaemon(true);
                this.messageReceiverThread.setDaemon(true);
                this.messageSenderThread.start();
                this.messageReceiverThread.start();

                this.listeningCallbackFinish.handleMessage("");
            } catch (Exception e) {
                this.isLaunched = false;
                this.isConnected = false;
                this.listeningCallbackFailure.handleMessage(e.getMessage());
            }
            return null;
        };
    }

    private Callable<Void> getClientConnectionThread() {
        return () -> {
            try {
                InetAddress inetAddress = InetAddress.getByName(this.serverHost);
                SocketAddress socketAddress = new InetSocketAddress(inetAddress, this.serverPort);
                this.clientSocket = new Socket();

                this.connectionCallbackSuccess.handleMessage("");
                this.clientSocket.connect(socketAddress);
                this.isConnected = true;

                this.messageSenderThread = new Thread(new FutureTask<>(getMessageSenderThread()));
                this.messageReceiverThread = new Thread(new FutureTask<>(getMessageReceiverThread()));
                this.messageSenderThread.setDaemon(true);
                this.messageReceiverThread.setDaemon(true);
                this.messageSenderThread.start();
                this.messageReceiverThread.start();

                this.connectionCallbackFinish.handleMessage("");
            } catch (Exception e) {
                this.isLaunched = false;
                this.isConnected = false;
                this.connectionCallbackFailure.handleMessage(e.getMessage());
            }
            return null;
        };
    }

    private Callable<Void> getMessageSenderThread() {
        return () -> {
            try {
                streamSender = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);

                while (!this.messageSenderThread.isInterrupted()) {
                    if (this.isExceptionWasThrown) {
                        throw new CustomException("Exception was detected at Receiver side");
                    }
                    if (!this.messagesForSending.isEmpty()) {
                        streamSender.println(this.messagesForSending.poll());
                    }
                }
                this.messagesForSending.clear();

                this.closingLock.lock();
                isClosing = true;
                streamSender.println(this.serverFinishKeyWord);
                streamSender.flush();
                if (this.isFirstGone) {
                    streamSender.close();
                    streamReceiver.close();
                    this.clientSocket.close();
                } else {
                    this.isFirstGone = true;
                }
                this.closingLock.unlock();

                // Final callback
                this.finalCallbackLock.lock();
                if (this.isNeedToDoFinalCallback) {
                    this.messageSuccessfullyFinishedConnectionCallback.handleMessage("");
                } else {
                    this.isNeedToDoFinalCallback = true;
                }
                this.finalCallbackLock.unlock();

            } catch (Exception e) {
                if (!(e instanceof CustomException)) {
                    e.printStackTrace();
                }

                if (this.closingLock.isLocked()) {
                    this.closingLock.unlock();
                }
                if (this.finalCallbackLock.isLocked()) {
                    this.finalCallbackLock.unlock();
                }

                this.exceptionThrowingLock.lock();
                if (!this.isExceptionWasThrown) {
                    this.messageFailFinishedConnectionCallback.handleMessage(e.getMessage());
                    this.isExceptionWasThrown = true;
                }
                this.exceptionThrowingLock.unlock();

            } finally {
                this.isConnected = false;
                this.isLaunched = false;
            }
            return null;
        };
    }

    private Callable<Void> getMessageReceiverThread() {
        return () -> {
            try {
                streamReceiver = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String temp;

                while (!this.messageSenderThread.isInterrupted()) {
                    if (this.isExceptionWasThrown) {
                        System.out.println("Exception is detected");
                        throw new CustomException("Exception was detected at Sender side");
                    }
                    temp = streamReceiver.readLine();
                    if (temp == null) {
                        throw new CustomException("Given string from reader stream is null. Maybe connection is aborted urgently.");
                    }
                    if (this.serverFinishKeyWord.equals(temp)) {
                        break;
                    }
                    messageReceiveCallback.handleMessage(temp);
                }

                this.closingLock.lock();
                if (!isClosing) {
                    this.messageSenderThread.interrupt();
                }
                if (this.isFirstGone) {
                    streamSender.close();
                    streamReceiver.close();
                    this.clientSocket.close();
                } else {
                    this.isFirstGone = true;
                }
                this.closingLock.unlock();

                // Final callback
                this.finalCallbackLock.lock();
                if (this.isNeedToDoFinalCallback) {
                    this.messageSuccessfullyFinishedConnectionCallback.handleMessage("");
                } else {
                    this.isNeedToDoFinalCallback = true;
                }
                this.finalCallbackLock.unlock();

            } catch (Exception e) {
                if (!(e instanceof CustomException)) {
                    e.printStackTrace();
                }

                if (this.closingLock.isLocked()) {
                    this.closingLock.unlock();
                }
                if (this.finalCallbackLock.isLocked()) {
                    this.finalCallbackLock.unlock();
                }

                this.exceptionThrowingLock.lock();
                if (!this.isExceptionWasThrown) {
                    this.messageFailFinishedConnectionCallback.handleMessage(e.getMessage());
                    this.isExceptionWasThrown = true;
                }
                this.exceptionThrowingLock.unlock();

            } finally {
                this.isConnected = false;
                this.isLaunched = false;
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

    public boolean isLaunched() {
        return isLaunched;
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

    public void setListeningCallbackFinish(MessageServiceCallback listeningCallbackFinish) {
        this.listeningCallbackFinish = listeningCallbackFinish;
    }

    public void setConnectionCallbackSuccess(MessageServiceCallback connectionCallbackSuccessFinish) {
        this.connectionCallbackSuccess = connectionCallbackSuccessFinish;
    }

    public void setConnectionCallbackFailure(MessageServiceCallback connectionCallbackFailure) {
        this.connectionCallbackFailure = connectionCallbackFailure;
    }

    public void setConnectionCallbackFinish(MessageServiceCallback connectionCallbackFinish) {
        this.connectionCallbackFinish = connectionCallbackFinish;
    }

    public void setMessageSuccessfullyFinishedConnectionCallback(MessageServiceCallback messageSuccessfullyFinishedConnectionCallback) {
        this.messageSuccessfullyFinishedConnectionCallback = messageSuccessfullyFinishedConnectionCallback;
    }

    public void setMessageFailFinishedConnectionCallback(MessageServiceCallback messageFailFinishedConnectionCallback) {
        this.messageFailFinishedConnectionCallback = messageFailFinishedConnectionCallback;
    }

    public void setMessageReceiverCallback(MessageServiceCallback messageReceiveCallback) {
        this.messageReceiveCallback = messageReceiveCallback;
    }
}
