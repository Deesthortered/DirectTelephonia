package org.deesthortered.direct.telephonia.scene.exception;

import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ExceptionService {
    public static String beanName = "exceptionService";

    public ExceptionService() {
    }

    public void createPopupInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info!");
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    public void createPopupAlert(CustomException e) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning!");
        alert.setHeaderText(e.getMessage());
        alert.showAndWait();
    }

    public void createPopupCriticalError(Throwable e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error!");
        alert.setHeaderText(e.getMessage());
        alert.setContentText(Arrays.toString(e.getStackTrace()));

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        alert.setX(bounds.getMaxX() / 2 - 100);
        alert.setY(bounds.getMaxY() / 2 - 50);

        alert.showAndWait();
    }
}
