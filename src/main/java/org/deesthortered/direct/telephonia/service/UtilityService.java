package org.deesthortered.direct.telephonia.service;

import javafx.scene.image.Image;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Component
@PropertySource("classpath:application.properties")
public class UtilityService {

    public Image getImageFromFile(String path,
                                  int mediaFrameWidth,
                                  int mediaFrameHeight,
                                  boolean saveRatio) throws IOException {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("Image file is not found by path " + path);
        }
        return new Image(
                file.toURI().toString(),
                mediaFrameWidth,
                mediaFrameHeight,
                saveRatio, true, false
        );
    }

    public boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public List<List<String>> getNetworkInterfaces() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        List<List<String>> resultInterfaces = new ArrayList<>();
        while(interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            List<String> resultAddresses = new ArrayList<>();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                resultAddresses.add(address.getHostAddress());
            }
            resultInterfaces.add(resultAddresses);
        }
        return resultInterfaces;
    }
}
