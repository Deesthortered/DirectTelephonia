package org.deesthortered.direct.telephonia.scene;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public abstract class AbstractScene implements EventHandler<Event> {
    private Stage stage;

    public final Stage getStage() {
        if (this.stage == null) {
            try {
                Pane pane = createMainPane();
                Scene scene = new Scene(
                        pane == null ? new Pane() : pane,
                        setWindowWidth(),
                        setWindowHeight()
                );

                String stylePath = setStyleFilePath();
                if (stylePath != null) {
                    scene.getStylesheets().add(stylePath);
                }

                this.stage = new Stage();
                this.stage.setResizable(setResizable());
                this.stage.setScene(scene);
            } catch (Exception e) {
                System.out.println("System error! The application will be terminated.");
                e.printStackTrace();
                System.exit(-1);
            }
        }
        return this.stage;
    }

    public abstract int setWindowWidth();

    public abstract int setWindowHeight();

    public abstract boolean setResizable();

    public abstract String setStyleFilePath();

    public abstract Pane createMainPane() throws Exception;
}
