package org.deesthortered.direct.telephonia.scene;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.deesthortered.direct.telephonia.scene.exception.CustomException;
import org.deesthortered.direct.telephonia.scene.exception.ExceptionService;
import org.deesthortered.direct.telephonia.service.AudioStreamingService;
import org.deesthortered.direct.telephonia.service.MessageService;
import org.deesthortered.direct.telephonia.service.UtilityService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

@Component
public class MainScene extends AbstractScene {
    public static String beanName = "mainScene";
    private final int windowWidth = 1200;
    private final int windowHeight = 800;

    private final ExceptionService exceptionService;
    private final MessageService messageService;
    private final AudioStreamingService audioStreamingService;
    private final UtilityService utilityService;

    // Role Chooser Panel
    private RadioButton radioRoleServer;
    private RadioButton radioRoleClient;

    // Message Service Panel
    private RadioButton radioMessagingAutomatic;
    private RadioButton radioMessagingManual;
    private TextField fieldMessagingHost;
    private TextField fieldMessagingPort;
    private Button buttonMessagingStartStop;

    // Messaging Panel
    private ObservableList<String> listMessages;
    private ListView<String> listviewMessages;
    private TextField fieldMessage;
    private Button buttonSendMessage;
    private Label labelStateLinePrefix;
    private Label labelStateLine;

    // Audio panel
    private RadioButton radioAudioAutomatic;
    private RadioButton radioAudioManual;
    private TextField fieldServerAudioHost;
    private TextField fieldServerAudioPort;
    private TextField fieldClientAudioHost;
    private TextField fieldClientAudioPort;
    private Button buttonAudioStartStopServer;
    private Button buttonAudioStartStopClient;

    // Video panel
    private final String mediaImagePath = "src/main/resources/default_image.jpg";
    private final int mediaFrameWidth = 400;
    private final int mediaFrameHeight = 250;
    private Image defaultMediaImage;

    public MainScene(ExceptionService exceptionService,
                     MessageService messageService,
                     AudioStreamingService audioStreamingService,
                     UtilityService utilityService) {
        this.exceptionService = exceptionService;
        this.messageService = messageService;
        this.audioStreamingService = audioStreamingService;
        this.utilityService = utilityService;
    }

    @Override
    public int setWindowWidth() {
        return this.windowWidth;
    }

    @Override
    public int setWindowHeight() {
        return this.windowHeight;
    }

    @Override
    public boolean setResizable() {
        return false;
    }

    @Override
    public String setStyleFilePath() {
        return "main-scene-style.css";
    }


    // UI construction ////////////////////////
    @Override
    public Pane createMainPane() throws Exception {
        BorderPane mainPane = new BorderPane();

        mainPane.setTop(createRoleChooserPanel());

        HBox mainBox = new HBox();
        mainBox.getChildren().addAll(
                createMessageConfigurationPanel(),
                createAudioPanel(),
                createVideoPanel()
        );
        mainBox.setAlignment(Pos.CENTER);
        mainPane.setCenter(mainBox);

        VBox bottomBox = new VBox();
        bottomBox.getChildren().addAll(
                createNetworkInterfacePanel(),
                createMessagingPanel()
        );
        mainPane.setBottom(bottomBox);

        initializeUI();
        initializeServices();
        return mainPane;
    }

    private Pane createRoleChooserPanel() {
        Label labelHello = new Label("Welcome to the Direct Telephonia!");

        VBox roleChooserPanelPane = new VBox();
        roleChooserPanelPane.getChildren().addAll(labelHello);
        roleChooserPanelPane.setAlignment(Pos.CENTER);
        return roleChooserPanelPane;
    }

    private Pane createMessageConfigurationPanel() {
        Label labelTitle = new Label("Message service info:");

        // Choose role
        Label labelChooseRole = new Label("Choose the role: ");
        ToggleGroup groupChooseRole = new ToggleGroup();

        radioRoleServer = new RadioButton("Server");
        radioRoleServer.setToggleGroup(groupChooseRole);
        radioRoleServer.setOnAction(this::handle);

        radioRoleClient = new RadioButton("Client");
        radioRoleClient.setToggleGroup(groupChooseRole);
        radioRoleClient.setOnAction(this::handle);

        HBox radioBox = new HBox();
        radioBox.getChildren().addAll(radioRoleServer, radioRoleClient);

        VBox paneChooseRole = new VBox();
        paneChooseRole.getChildren().addAll(labelChooseRole, radioBox);

        // Define host/port
        Label labelRadioAutomaticManual = new Label("Define host/port:");
        ToggleGroup groupRadioAutomaticManual = new ToggleGroup();

        this.radioMessagingAutomatic = new RadioButton("Automatic");
        this.radioMessagingAutomatic.setToggleGroup(groupRadioAutomaticManual);
        this.radioMessagingAutomatic.setOnAction(this::handle);

        this.radioMessagingManual = new RadioButton("Manual");
        this.radioMessagingManual.setToggleGroup(groupRadioAutomaticManual);
        this.radioMessagingManual.setOnAction(this::handle);

        HBox boxRadioAutomaticManual = new HBox();
        boxRadioAutomaticManual.getChildren().addAll(
                this.radioMessagingAutomatic,
                this.radioMessagingManual
        );
        VBox boxAutomaticManual = new VBox();
        boxAutomaticManual.getChildren().addAll(
                labelRadioAutomaticManual,
                boxRadioAutomaticManual
        );


        // Server info
        Label labelHost = new Label("Host: ");
        fieldMessagingHost = new TextField();
        HBox paneHost = new HBox();
        paneHost.getChildren().addAll(labelHost, fieldMessagingHost);

        Label labelPort = new Label("Port:  ");
        fieldMessagingPort = new TextField();
        HBox panePort = new HBox();
        panePort.getChildren().addAll(labelPort, fieldMessagingPort);

        VBox paneServerInfo = new VBox();
        paneServerInfo.getChildren().addAll(paneHost, panePort);


        buttonMessagingStartStop = new Button();
        buttonMessagingStartStop.setOnAction(this::handle);


        VBox panePropertyPanel = new VBox();
        panePropertyPanel.getChildren().addAll(
                labelTitle,
                paneChooseRole,
                boxAutomaticManual,
                paneServerInfo,
                buttonMessagingStartStop
        );
        return panePropertyPanel;
    }

    private Pane createAudioPanel() {
        Label labelTitle = new Label("Audio service info:");

        // Define host/port
        Label labelRadioAutomaticManual = new Label("Define host/port:");
        ToggleGroup groupRadioAutomaticManual = new ToggleGroup();

        this.radioAudioAutomatic = new RadioButton("Automatic");
        this.radioAudioAutomatic.setToggleGroup(groupRadioAutomaticManual);
        this.radioAudioAutomatic.setOnAction(this::handle);

        this.radioAudioManual = new RadioButton("Manual");
        this.radioAudioManual.setToggleGroup(groupRadioAutomaticManual);
        this.radioAudioManual.setOnAction(this::handle);

        HBox boxRadioAutomaticManual = new HBox();
        boxRadioAutomaticManual.getChildren().addAll(
                this.radioAudioAutomatic,
                this.radioAudioManual
        );

        VBox boxAutomaticManual = new VBox();
        boxAutomaticManual.getChildren().addAll(
                labelRadioAutomaticManual,
                boxRadioAutomaticManual
        );

        HBox boxAudioServerHost = new HBox();
        Label labelAudioServerHost = new Label("Server host: ");
        this.fieldServerAudioHost = new TextField();
        boxAudioServerHost.getChildren().addAll(labelAudioServerHost, this.fieldServerAudioHost);

        HBox boxAudioServerPort = new HBox();
        Label labelAudioServerPort = new Label("Server port:  ");
        this.fieldServerAudioPort = new TextField();
        boxAudioServerPort.getChildren().addAll(labelAudioServerPort, this.fieldServerAudioPort);

        HBox boxAudioClientHost = new HBox();
        Label labelAudioClientHost = new Label("Client host: ");
        this.fieldClientAudioHost = new TextField();
        boxAudioClientHost.getChildren().addAll(labelAudioClientHost, this.fieldClientAudioHost);

        HBox boxAudioClientPort = new HBox();
        Label labelAudioClientPort = new Label("Client port:  ");
        this.fieldClientAudioPort = new TextField();
        boxAudioClientPort.getChildren().addAll(labelAudioClientPort, this.fieldClientAudioPort);

        this.buttonAudioStartStopServer = new Button();
        this.buttonAudioStartStopServer.setOnAction(this::handle);
        this.buttonAudioStartStopClient = new Button();
        this.buttonAudioStartStopClient.setOnAction(this::handle);

        VBox paneAudioPanel = new VBox();
        paneAudioPanel.getChildren().addAll(
                labelTitle,
                boxAutomaticManual,
                boxAudioServerHost,
                boxAudioServerPort,
                boxAudioClientHost,
                boxAudioClientPort,
                this.buttonAudioStartStopServer,
                this.buttonAudioStartStopClient
        );
        return paneAudioPanel;
    }

    private Pane createVideoPanel() throws IOException {
        Label labelTitle = new Label("Video service info:");

        this.defaultMediaImage = utilityService.getImageFromFile(mediaImagePath, mediaFrameWidth, mediaFrameHeight, false);
        ImageView imageView = new ImageView();
        imageView.setImage(this.defaultMediaImage);

        Label labelInfo = new Label("Video service is not implemented");

        VBox paneVideoPanel = new VBox();
        paneVideoPanel.getChildren().addAll(
                labelTitle,
                imageView,
                labelInfo
        );
        return paneVideoPanel;
    }


    private Pane createNetworkInterfacePanel() throws SocketException {
        StringBuilder hostResult = new StringBuilder();
        boolean needEnter = true;
        for (List<String> networkInterface : this.utilityService.getNetworkInterfaces()) {
            for (String address : networkInterface) {
                if (!address.trim().equals("")) {
                    hostResult.append(address);
                    hostResult.append("\n");
                    needEnter = false;
                }
            }
            if (needEnter) {
                hostResult.append("\n");
                needEnter = false;
            }
        }
        Label labelSummaryHostAddresses = new Label();
        labelSummaryHostAddresses.setText("Your network interfaces:\n" + hostResult.toString());

        StackPane networkInterfacePanelPane = new StackPane();
        networkInterfacePanelPane.getChildren().addAll(labelSummaryHostAddresses);
        networkInterfacePanelPane.setAlignment(Pos.CENTER_LEFT);
        return networkInterfacePanelPane;
    }

    private Pane createMessagingPanel() {
        listMessages = FXCollections.observableList(new ArrayList<>());
        listviewMessages = new ListView<>(listMessages);
        listviewMessages.setPrefHeight(250);

        fieldMessage = new TextField();
        buttonSendMessage = new Button("Send");
        buttonSendMessage.setOnAction(this::handle);
        HBox paneInputMessage = new HBox();
        paneInputMessage.getChildren().addAll(fieldMessage, buttonSendMessage);

        labelStateLinePrefix = new Label("State line: ");
        labelStateLine = new Label();
        HBox paneStateLine = new HBox();
        paneStateLine.getChildren().addAll(labelStateLinePrefix, labelStateLine);

        VBox paneMessagePanel = new VBox();
        paneMessagePanel.getChildren().addAll(listviewMessages, paneInputMessage, paneStateLine);
        return paneMessagePanel;
    }

    private void initializeUI() {
        radioRoleServer.setSelected(true);
        radioRoleClient.setSelected(false);

        radioMessagingAutomatic.setSelected(false);
        radioMessagingManual.setSelected(true);
        fieldMessagingHost.setText(this.messageService.getServerHost());
        fieldMessagingPort.setText(String.valueOf(this.messageService.getServerPort()));

        buttonMessagingStartStop.setText("Start server");

        radioAudioAutomatic.setSelected(false);
        radioAudioManual.setSelected(true);
        fieldServerAudioHost.setText(this.audioStreamingService.getServerHost());
        fieldServerAudioPort.setText(String.valueOf(this.audioStreamingService.getServerPort()));
        fieldClientAudioHost.setText(this.audioStreamingService.getClientHost());
        fieldClientAudioPort.setText(String.valueOf(this.audioStreamingService.getClientPort()));
        buttonAudioStartStopServer.setText("Start server");
        buttonAudioStartStopClient.setText("Connect");
        buttonAudioStartStopClient.setDisable(true);

        fieldMessage.setDisable(true);
        buttonSendMessage.setDisable(true);
        labelStateLine.setText("Application has been started!");
    }
    //////////////////////////


    // Message callbacks ////////////////////////
    private void initializeServices() {
        this.messageService.setListeningCallbackSuccess((message ->
                Platform.runLater(() -> callbackListeningSuccess())));
        this.messageService.setListeningCallbackFailure((message ->
                Platform.runLater(() -> callbackListeningFailure(message))));
        this.messageService.setListeningCallbackFinish((message ->
                Platform.runLater(() -> callbackListeningFinish())));
        this.messageService.setConnectionCallbackSuccess((message ->
                Platform.runLater(() -> callbackConnectionSuccess())));
        this.messageService.setConnectionCallbackFailure((message ->
                Platform.runLater(() -> callbackConnectionFailure(message))));
        this.messageService.setConnectionCallbackFinish((message ->
                Platform.runLater(() -> callbackConnectionFinish())));
        this.messageService.setMessageSuccessfullyFinishedConnectionCallback(message ->
                Platform.runLater(() -> callbackMessageSuccessfullyFinishedConnection()));
        this.messageService.setMessageFailFinishedConnectionCallback(message ->
                Platform.runLater(() -> callbackMessageFailedFinishedConnection(message)));
        this.messageService.setMessageReceiverCallback(message ->
                Platform.runLater(() -> callbackMessageReceiveMessage(message)));

        this.audioStreamingService.setAudioServiceCallbackRecordingFailed(message ->
                Platform.runLater(() -> callbackAudioServiceRecordingFailed(message)));
        this.audioStreamingService.setAudioServiceCallbackPlayingFailed(message ->
                Platform.runLater(() -> callbackAudioServicePlayingFailed(message)));
        this.audioStreamingService.setAudioServiceCallbackPlayingStopped(message ->
                Platform.runLater(() -> callbackAudioServicePlayingStopped()));
        this.audioStreamingService.setAudioServiceCallbackPlayingFinished(message ->
                Platform.runLater(() -> callbackAudioServicePlayingFinished()));
        this.audioStreamingService.setAudioStreamingCallbackServiceListeningSuccessful(message ->
                Platform.runLater(() -> callbackAudioStreamingCallbackServiceListeningSuccessful()));
        this.audioStreamingService.setAudioStreamingCallbackServiceSendingSuccessful(message ->
                Platform.runLater(() -> callbackAudioStreamingCallbackServiceSendingSuccessful()));
        this.audioStreamingService.setAudioStreamingCallbackServiceSendingFailed(message ->
                Platform.runLater(() -> callbackAudioStreamingServiceSendingFailed(message)));
        this.audioStreamingService.setAudioStreamingCallbackServiceReceivingFailed(message ->
                Platform.runLater(() -> callbackAudioStreamingServiceReceivingFailed(message)));
        this.audioStreamingService.setAudioStreamingCallbackServiceServerFinishedSuccessfully(message ->
                Platform.runLater(() -> callbackAudioStreamingCallbackServiceServerFinishedSuccessfully()));
        this.audioStreamingService.setAudioStreamingCallbackServiceClientFinishedSuccessfully(message ->
                Platform.runLater(() -> callbackAudioStreamingCallbackServiceClientFinishedSuccessfully()));
        this.audioStreamingService.setAudioStreamingCallbackServiceFullyFinishedSuccessfully(message ->
                Platform.runLater(() -> callbackAudioStreamingCallbackServiceFullyFinishedSuccessfully()));
    }

    private void callbackListeningSuccess() {
        this.fieldMessagingHost.setText(this.messageService.getServerHost());
        this.fieldMessagingPort.setText(String.valueOf(this.messageService.getServerPort()));
        this.buttonMessagingStartStop.setText("Stop listening");
        this.buttonMessagingStartStop.setDisable(false);
        showOnStateLabel("Server is launched and waiting client.");
    }

    private void callbackListeningFailure(String message) {
        this.radioRoleServer.setDisable(false);
        this.radioRoleClient.setDisable(false);
        if (this.radioMessagingManual.isSelected()) {
            this.fieldMessagingHost.setDisable(false);
            this.fieldMessagingPort.setDisable(false);
        } else {
            this.fieldMessagingHost.setText("automatically...");
            this.fieldMessagingPort.setText("automatically...");
        }

        this.buttonMessagingStartStop.setDisable(false);
        this.buttonMessagingStartStop.setText("Start server...");

        if (!"Socket closed".equals(message)) {
            this.exceptionService.createPopupAlert(new CustomException(message));
            showOnStateLabel("Message server has been forced shutdown.");
        } else {
            showOnStateLabel("Message server listening is stopped. Now you can start again.");
        }
    }

    private void callbackListeningFinish() {
        this.buttonMessagingStartStop.setText("Disconnect");
        this.fieldMessage.setDisable(false);
        this.buttonSendMessage.setDisable(false);
        showOnStateLabel("Client has been connected! Now you can to chat.");
    }

    private void callbackConnectionSuccess() {
        this.buttonMessagingStartStop.setDisable(false);
        this.buttonMessagingStartStop.setText("Disconnect");

        showOnStateLabel("Connection socket is created successfully!");
    }

    private void callbackConnectionFailure(String message) {
        this.radioRoleServer.setDisable(false);
        this.radioRoleClient.setDisable(false);
        this.fieldMessagingHost.setDisable(false);
        this.fieldMessagingPort.setDisable(false);
        this.buttonMessagingStartStop.setDisable(false);
        this.buttonMessagingStartStop.setText("Connect");

        String log = "Connection failed: " + message;
        this.exceptionService.createPopupAlert(new CustomException(log));
        showOnStateLabel(log);
    }

    private void callbackConnectionFinish() {
        this.buttonMessagingStartStop.setText("Disconnect");
        this.fieldMessage.setDisable(false);
        this.buttonSendMessage.setDisable(false);
        showOnStateLabel("You is connected to the server! Now you can to chat.");
    }

    private void callbackMessageSuccessfullyFinishedConnection() {
        this.radioRoleServer.setDisable(false);
        this.radioRoleClient.setDisable(false);

        this.fieldMessage.setDisable(true);
        this.buttonSendMessage.setDisable(true);
        if (this.radioRoleServer.isSelected()) {
            this.buttonMessagingStartStop.setDisable(false);
            this.buttonMessagingStartStop.setText("Start server...");

            if (this.radioMessagingAutomatic.isSelected()) {
                this.fieldMessagingHost.setText("automatically...");
                this.fieldMessagingPort.setText("automatically...");
            } else {
                this.fieldMessagingHost.setDisable(false);
                this.fieldMessagingPort.setDisable(false);
            }
        } else {
            this.fieldMessagingHost.setDisable(false);
            this.fieldMessagingPort.setDisable(false);
            this.buttonMessagingStartStop.setDisable(false);
            this.buttonMessagingStartStop.setText("Connect");
        }

        showOnStateLabel("Connection is closed. Now you can connect again!");
    }

    private void callbackMessageFailedFinishedConnection(String message) {
        this.radioRoleServer.setDisable(false);
        this.radioRoleClient.setDisable(false);

        if (this.radioRoleServer.isSelected()) {
            this.buttonMessagingStartStop.setDisable(false);
            this.buttonMessagingStartStop.setText("Start server...");
            if (this.radioMessagingAutomatic.isSelected()) {
                this.fieldMessagingHost.setText("automatically...");
                this.fieldMessagingPort.setText("automatically...");
            } else {
                this.fieldMessagingHost.setDisable(false);
                this.fieldMessagingPort.setDisable(false);
            }
        } else {
            this.fieldMessagingHost.setDisable(false);
            this.fieldMessagingPort.setDisable(false);
            this.buttonMessagingStartStop.setDisable(false);
            this.buttonMessagingStartStop.setText("Connect");
        }

        showOnStateLabel("Connection was aborted with exception: " + message);
        this.exceptionService.createPopupAlert(new CustomException(
                "Connection error: " + message
        ));
    }

    private void callbackMessageReceiveMessage(String message) {
        listMessages.add(message);
    }
    //////////////////////////


    // Audio callbacks ////////////////////////
    private void callbackAudioServiceRecordingFailed(String message) {
        this.exceptionService.createPopupAlert(new CustomException(message));
        showOnStateLabel("AudioServiceRecordingFailed: " + message);
    }

    private void callbackAudioServicePlayingFailed(String message) {
        this.exceptionService.createPopupAlert(new CustomException(message));
        showOnStateLabel("AudioServicePlayingFailed: " + message);
    }

    private void callbackAudioServicePlayingStopped() {

    }

    private void callbackAudioServicePlayingFinished() {

    }

    private void callbackAudioStreamingCallbackServiceListeningSuccessful() {
        this.buttonAudioStartStopClient.setDisable(false);
        this.fieldServerAudioHost.setText(this.audioStreamingService.getServerHost());
        this.fieldServerAudioPort.setText(String.valueOf(this.audioStreamingService.getServerPort()));

        showOnStateLabel("Audio server is listening...");
    }

    private void callbackAudioStreamingCallbackServiceSendingSuccessful() {
        showOnStateLabel("Audio client connection is created (first packet is sent)...");
    }

    private void callbackAudioStreamingServiceSendingFailed(String message) {
        this.fieldClientAudioHost.setDisable(false);
        this.fieldClientAudioPort.setDisable(false);

        this.buttonAudioStartStopServer.setDisable(false);
        this.buttonAudioStartStopClient.setText("Connect");

        this.exceptionService.createPopupAlert(new CustomException(message));
        showOnStateLabel("Audio client has been forced shutdown: " + message);
    }

    private void callbackAudioStreamingServiceReceivingFailed(String message) {
        if (!"Socket closed".equals(message)) {
            this.exceptionService.createPopupAlert(new CustomException(message));
            showOnStateLabel("Audio server has been forced shutdown.");
        } else {
            showOnStateLabel("Audio server listening is stopped. Now you can start again.");
        }
    }

    private void callbackAudioStreamingCallbackServiceServerFinishedSuccessfully() {
    }

    private void callbackAudioStreamingCallbackServiceClientFinishedSuccessfully() {
    }

    private void callbackAudioStreamingCallbackServiceFullyFinishedSuccessfully() {
        if (this.radioAudioManual.isSelected()) {
            this.fieldServerAudioHost.setDisable(false);
            this.fieldServerAudioPort.setDisable(false);
        } else {
            this.fieldServerAudioHost.setText("automatically");
            this.fieldServerAudioPort.setText("automatically");
        }
        this.fieldClientAudioHost.setDisable(false);
        this.fieldClientAudioPort.setDisable(false);

        this.buttonAudioStartStopServer.setText("Start server");
        this.buttonAudioStartStopServer.setDisable(false);

        this.buttonAudioStartStopClient.setText("Connect");
        this.buttonAudioStartStopClient.setDisable(true);

        showOnStateLabel("Connection is closed. Now you can connect again!");
    }
    //////////////////////////

    @Override
    public void handle(Event event) {
        Object source = event.getSource();
        try {
            if (source == radioRoleServer) {
                handleRadioRoleServer();
            } else if (source == radioRoleClient) {
                handleRadioRoleClient();
            } else if (source == radioMessagingAutomatic) {
                handleMessagingRadioMessagingAutomatic();
            } else if (source == radioMessagingManual) {
                handleMessagingRadioMessagingManual();
            } else if (source == buttonMessagingStartStop) {
                if (this.messageService.isLaunched()) {
                    if (radioRoleServer.isSelected()) {
                        handleMessagingButtonStopServer();
                    } else {
                        handleMessagingButtonStopClient();
                    }
                } else {
                    if (radioRoleServer.isSelected()) {
                        handleMessagingButtonStartServer();
                    } else {
                        handleMessagingButtonConnectToServer();
                    }
                }
            } else if (source == buttonSendMessage) {
                handleMessagingButtonSendMessage();
            } else if (source == radioAudioAutomatic) {
                handleAudioRadioAutomatic();
            } else if (source == radioAudioManual) {
                handleAudioRadioManual();
            } else if (source == buttonAudioStartStopServer) {
                if (this.audioStreamingService.isServerLaunched()) {
                    handleAudioButtonStopServer();
                } else {
                    handleAudioButtonStartServer();
                }
            } else if (source == buttonAudioStartStopClient) {
                if (this.audioStreamingService.isClientLaunched()) {
                    handleAudioButtonStopClient();
                } else {
                    handleAudioButtonConnectToServer();
                }
            }
        } catch (CustomException e) {
            exceptionService.createPopupAlert(e);
        } catch (Exception e) {
            e.printStackTrace();
            exceptionService.createPopupCriticalError(e);
        }
    }

    public void handleCloseApplication() {
        try {
            this.messageService.stopService();
            this.audioStreamingService.stopService();
        } catch (CustomException | IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRadioRoleServer() {
        buttonMessagingStartStop.setText("Start server");

        radioMessagingAutomatic.setDisable(false);
        radioMessagingManual.setDisable(false);
    }

    private void handleRadioRoleClient() {
        buttonMessagingStartStop.setText("Connect");
        radioMessagingAutomatic.setDisable(true);
        radioMessagingManual.setDisable(true);

        radioMessagingAutomatic.setSelected(false);
        radioMessagingManual.setSelected(true);
        fieldMessagingHost.setDisable(false);
        fieldMessagingPort.setDisable(false);

        fieldMessagingHost.setText(this.messageService.getServerHost());
        fieldMessagingPort.setText(String.valueOf(this.messageService.getServerPort()));
    }

    private void handleMessagingRadioMessagingAutomatic() {
        fieldMessagingHost.setText("automatically...");
        fieldMessagingPort.setText("automatically...");
        fieldMessagingHost.setDisable(true);
        fieldMessagingPort.setDisable(true);
    }

    private void handleMessagingRadioMessagingManual() {
        fieldMessagingHost.setText(this.messageService.getServerHost());
        fieldMessagingPort.setText(String.valueOf(this.messageService.getServerPort()));
        fieldMessagingHost.setDisable(false);
        fieldMessagingPort.setDisable(false);
    }

    private void handleMessagingButtonStartServer() throws CustomException, SocketException {
        this.radioRoleServer.setDisable(true);
        this.radioRoleClient.setDisable(true);
        this.buttonMessagingStartStop.setDisable(true);
        this.fieldMessagingHost.setDisable(true);
        this.fieldMessagingPort.setDisable(true);

        if (radioMessagingManual.isSelected()) {
            this.messageService.setServerHost(this.fieldMessagingHost.getText());
            this.messageService.setServerPort(
                    Integer.parseInt(this.fieldMessagingPort.getText()));
        }

        showOnStateLabel("The listening server is launching...");
        this.messageService.createServer(radioMessagingAutomatic.isSelected());
    }

    private void handleMessagingButtonConnectToServer() throws CustomException {
        if (!this.utilityService.isNumeric(fieldMessagingPort.getText())) {
            this.exceptionService.createPopupAlert(new CustomException("Port must be numeric value!"));
            return;
        }

        this.radioRoleServer.setDisable(true);
        this.radioRoleClient.setDisable(true);
        this.fieldMessagingHost.setDisable(true);
        this.fieldMessagingPort.setDisable(true);
        this.buttonMessagingStartStop.setDisable(true);
        this.buttonMessagingStartStop.setText("Connecting...");

        showOnStateLabel("Connecting to the server " + fieldMessagingHost.getText() + ":" + fieldMessagingPort.getText());
        this.messageService.setServerHost(fieldMessagingHost.getText());
        this.messageService.setServerPort(Integer.parseInt(fieldMessagingPort.getText()));
        this.messageService.connectToServer();
    }

    private void handleMessagingButtonStopServer() throws IOException, CustomException {
        this.messageService.stopService();
    }

    private void handleMessagingButtonStopClient() throws IOException, CustomException {
        this.messageService.stopService();
    }

    private void handleMessagingButtonSendMessage() throws CustomException {
        String message = fieldMessage.getText();
        if (message != null && !"".equals(message)) {
            messageService.sendMessage(message);
            fieldMessage.setText("");
            listMessages.add("You: " + message);
        }
    }

    private void handleAudioRadioAutomatic() {
        fieldServerAudioHost.setText("automatically...");
        fieldServerAudioPort.setText("automatically...");
        fieldServerAudioHost.setDisable(true);
        fieldServerAudioPort.setDisable(true);
    }

    private void handleAudioRadioManual() {
        fieldServerAudioHost.setText(this.audioStreamingService.getServerHost());
        fieldServerAudioPort.setText(String.valueOf(
                        radioRoleServer.isSelected() ?
                        this.audioStreamingService.getServerPort() :
                        this.audioStreamingService.getClientPort()));
        fieldServerAudioHost.setDisable(false);
        fieldServerAudioPort.setDisable(false);
    }

    private void handleAudioButtonStartServer() throws CustomException {
        this.fieldServerAudioHost.setDisable(true);
        this.fieldServerAudioPort.setDisable(true);
        this.buttonAudioStartStopServer.setText("Stop server");

        if (this.radioAudioManual.isSelected()) {
            this.audioStreamingService.setServerHost(this.fieldServerAudioHost.getText());
            this.audioStreamingService.setServerPort(Integer.valueOf(this.fieldServerAudioPort.getText()));
        }
        this.audioStreamingService.startServer(this.radioAudioAutomatic.isSelected());
    }

    private void handleAudioButtonConnectToServer() throws CustomException {
        this.fieldClientAudioHost.setDisable(true);
        this.fieldClientAudioPort.setDisable(true);

        this.buttonAudioStartStopServer.setDisable(true);
        this.buttonAudioStartStopClient.setText("Stop connection");

        this.audioStreamingService.setClientHost(this.fieldClientAudioHost.getText());
        this.audioStreamingService.setClientPort(Integer.valueOf(this.fieldClientAudioPort.getText()));
        this.audioStreamingService.connectToServer();
    }

    private void handleAudioButtonStopServer() throws CustomException {
        this.audioStreamingService.stopService();
    }

    private void handleAudioButtonStopClient() throws CustomException {
        this.audioStreamingService.stopService();
    }

    private synchronized void showOnStateLabel(String message) {
        this.labelStateLine.setText(message == null ? "" : message);
    }
}
