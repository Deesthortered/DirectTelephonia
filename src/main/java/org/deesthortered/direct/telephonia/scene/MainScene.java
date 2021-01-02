package org.deesthortered.direct.telephonia.scene;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.deesthortered.direct.telephonia.scene.exception.CustomException;
import org.deesthortered.direct.telephonia.scene.exception.ExceptionService;
import org.deesthortered.direct.telephonia.service.MessageService;
import org.deesthortered.direct.telephonia.service.UtilityService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class MainScene extends AbstractScene {
    public static String beanName = "mainScene";

    private final ExceptionService exceptionService;
    private final MessageService messageService;
    private final UtilityService utilityService;

    private final int windowWidth  = 1200;
    private final int windowHeight = 800;

    private final String mediaImagePath = "src/main/resources/default_image.jpg";
    private final int mediaFrameWidth = 500;
    private final int mediaFrameHeight = 300;
    private Image defaultMediaImage;

    private RadioButton radioRoleServer;
    private RadioButton radioRoleClient;
    private TextField fieldHost;
    private TextField fieldPort;
    private Button buttonStart;
    private ObservableList<String> listMessages;
    private ListView<String> listviewMessages;
    private TextField fieldMessage;
    private Button buttonSendMessage;
    private Label labelStateLinePrefix;
    private Label labelStateLine;

    private boolean isStarted = false;

    public MainScene(ExceptionService exceptionService,
                     MessageService messageService,
                     UtilityService utilityService) {
        this.exceptionService = exceptionService;
        this.messageService = messageService;
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

    @Override
    public Pane createMainPane() throws Exception {
        BorderPane mainPane = new BorderPane();
        mainPane.setLeft(createPropertyPanel());
        mainPane.setRight(createMediaPanel());
        mainPane.setBottom(createMessagePanel());
        initializeServices();
        return mainPane;
    }

    private Pane createPropertyPanel() {
        // Hello
        Label labelHello = new Label("Welcome to the Direct Telephonia!");

        // Client/Server role
        Label labelChooseRole = new Label("Choose the role: ");
        ToggleGroup groupChooseRole = new ToggleGroup();
        radioRoleServer = new RadioButton("Server");
        radioRoleClient = new RadioButton("Client");
        radioRoleServer.setToggleGroup(groupChooseRole);
        radioRoleClient.setToggleGroup(groupChooseRole);
        radioRoleServer.setSelected(false);
        radioRoleClient.setSelected(true);
        radioRoleServer.setOnAction(this::handle);
        radioRoleClient.setOnAction(this::handle);
        HBox paneChooseRole = new HBox();
        paneChooseRole.getChildren().addAll(labelChooseRole, radioRoleServer, radioRoleClient);

        // Server info
        Label labelServerInfo = new Label("Set server info");
        Label labelHost = new Label("Host: ");
        Label labelPort = new Label("Port: ");
        fieldHost = new TextField();
        fieldHost.setText(this.messageService.getServerHost());
        fieldPort = new TextField();
        fieldPort.setText(String.valueOf(this.messageService.getServerPort()));
        HBox paneHost = new HBox();
        paneHost.getChildren().addAll(labelHost, fieldHost);
        HBox panePort = new HBox();
        panePort.getChildren().addAll(labelPort, fieldPort);
        VBox paneServerInfo = new VBox();
        paneServerInfo.getChildren().addAll(labelServerInfo, paneHost, panePort);

        buttonStart = new Button("Connect...");
        buttonStart.setOnAction(this::handle);

        VBox panePropertyPanel = new VBox();
        panePropertyPanel.getChildren().add(labelHello);
        panePropertyPanel.getChildren().add(paneChooseRole);
        panePropertyPanel.getChildren().add(paneServerInfo);
        panePropertyPanel.getChildren().add(buttonStart);

        return panePropertyPanel;
    }

    private Pane createMediaPanel() throws IOException {
        this.defaultMediaImage = utilityService.getImageFromFile(mediaImagePath, mediaFrameWidth, mediaFrameHeight,false);
        ImageView imageView = new ImageView();
        imageView.setImage(this.defaultMediaImage);

        ToggleButton toggleMicrophone = new ToggleButton("Microphone");
        ToggleButton toggleWebcam = new ToggleButton("Webcam");
        HBox paneToggleButtons = new HBox();
        paneToggleButtons.getChildren().addAll(toggleMicrophone, toggleWebcam);

        VBox paneMediaPanel = new VBox();
        paneMediaPanel.getChildren().addAll(imageView, paneToggleButtons);
        return paneMediaPanel;
    }

    private Pane createMessagePanel() {
        listMessages = FXCollections.observableList(new ArrayList<>());
        listviewMessages = new ListView<>(listMessages);
        listviewMessages.setPrefHeight(250);

        fieldMessage = new TextField();
        buttonSendMessage = new Button("Send");
        buttonSendMessage.setOnAction(this::handle);
        HBox paneInputMessage = new HBox();
        paneInputMessage.getChildren().addAll(fieldMessage, buttonSendMessage);

        labelStateLinePrefix = new Label("State line: ");
        labelStateLine = new Label("Application has been started!");
        HBox paneStateLine = new HBox();
        paneStateLine.getChildren().addAll(labelStateLinePrefix, labelStateLine);

        VBox paneMessagePanel = new VBox();
        paneMessagePanel.getChildren().addAll(listviewMessages, paneInputMessage, paneStateLine);
        return paneMessagePanel;
    }


    private void initializeServices() {
        this.messageService.setListeningCallbackSuccess((message ->
                Platform.runLater(() -> callbackListeningSuccess())));
        this.messageService.setListeningCallbackFailure((message ->
                Platform.runLater(() -> callbackListeningFailure(message))));
        this.messageService.setConnectionCallbackSuccess((message ->
                Platform.runLater(() -> callbackConnectionSuccess())));
        this.messageService.setConnectionCallbackFailure((message ->
                Platform.runLater(() -> callbackConnectionFailure(message))));
        this.messageService.setMessageSenderCallbackSuccess((message ->
                Platform.runLater(() -> callbackMessageSenderSuccess())));
        this.messageService.setMessageSenderCallbackFailure((message ->
                Platform.runLater(() -> callbackMessageSenderFailure(message))));
        this.messageService.setMessageReceiverCallbackSuccess((message ->
                Platform.runLater(() -> callbackMessageReceiverSuccess())));
        this.messageService.setMessageReceiverCallbackFailure(message ->
                Platform.runLater(() -> callbackMessageReceiverFailure(message)));
        this.messageService.setMessageReceiverCallback(message ->
                Platform.runLater(() -> callbackMessageReceiveMessage(message)));
    }

    private void callbackListeningSuccess() {
        this.fieldHost.setText(this.messageService.getServerHost());
        this.fieldPort.setText(String.valueOf(this.messageService.getServerPort()));
        showOnStateLabel("Server is launched and waiting client.");
        this.buttonStart.setText("Stop listening");
        this.buttonStart.setDisable(false);
    }

    private void callbackListeningFailure(String message) {
        showOnStateLabel("Server has been closed.");
        this.isStarted = false;
        this.buttonStart.setText("Start server...");
        this.buttonStart.setDisable(false);
        fieldHost.setText("automatically...");
        fieldPort.setText("automatically...");
    }

    private void callbackConnectionSuccess() {

    }

    private void callbackConnectionFailure(String message) {

    }

    private void callbackMessageSenderSuccess() {

    }

    private void callbackMessageSenderFailure(String message) {

    }

    private void callbackMessageReceiverSuccess() {

    }

    private void callbackMessageReceiverFailure(String message) {

    }

    private void callbackMessageReceiveMessage(String message) {
        listMessages.add(message);
    }

    @Override
    public void handle(Event event) {
        Object source = event.getSource();
        try {
            if (source == radioRoleServer) {
                handleRadioRoleServer();
            } else if (source == radioRoleClient) {
                handleRadioRoleClient();
            } else if (source == buttonStart) {
                if (isStarted) {
                    handleButtonStopService();
                } else {
                    if (radioRoleServer.isSelected()) {
                        handleButtonStartServer();
                    } else {
                        handleButtonConnectToServer();
                    }
                }
            } else if (source == buttonSendMessage) {
                handleButtonSendMessage();
            }
        } catch (CustomException e) {
            exceptionService.createPopupAlert(e);
        } catch (Exception e) {
            e.printStackTrace();
            exceptionService.createPopupCriticalError(e);
        }
    }

    private void handleRadioRoleServer() {
        buttonStart.setText("Start server...");
        fieldHost.setText("automatically...");
        fieldHost.setDisable(true);
        fieldPort.setText("automatically...");
        fieldPort.setDisable(true);
    }

    private void handleRadioRoleClient() {
        buttonStart.setText("Connect...");
        fieldHost.setText(this.messageService.getServerHost());
        fieldHost.setDisable(false);
        fieldPort.setText(String.valueOf(this.messageService.getServerPort()));
        fieldPort.setDisable(false);
    }

    private void handleButtonStartServer() throws CustomException {
        isStarted = true;
        this.buttonStart.setDisable(true);
        showOnStateLabel("The listening server is launching...");
        this.messageService.createServer();
    }

    private void handleButtonConnectToServer() throws CustomException {
        this.messageService.setServerHost(messageService.getServerHost());
        this.messageService.setServerPort(Integer.parseInt(fieldPort.getText()));
        this.messageService.connectToServer();
    }

    private void handleButtonStopService() throws IOException, CustomException {
        this.messageService.stopService();
    }

    private void handleButtonSendMessage() {
        String message = fieldMessage.getText();
        if (!"".equals(message)) {
            // messageService.sendMessage(message);
            fieldMessage.setText("");
            listMessages.add(message);
        }
    }


    private void showOnStateLabel(String message) {
        this.labelStateLine.setText(message == null ? "" : message);
    }
}
