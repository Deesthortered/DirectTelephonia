package org.deesthortered.direct.telephonia;

import javafx.application.Application;
import javafx.stage.Stage;
import org.deesthortered.direct.telephonia.configuration.ApplicationConfiguration;
import org.deesthortered.direct.telephonia.scene.AbstractScene;
import org.deesthortered.direct.telephonia.scene.MainScene;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class DirectTelephonia extends Application {

    public static ApplicationContext applicationContext;

    public void initializeContexts() {
        applicationContext =
                new AnnotationConfigApplicationContext(ApplicationConfiguration.class);
    }

    @Override
    public void start(Stage stage) {
        stage.hide();
        initializeContexts();

        AbstractScene mainScene = applicationContext.getBean(MainScene.beanName, AbstractScene.class);
        mainScene.getStage().show();
    }
}
