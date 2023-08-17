package com.diegocapape;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;
import com.diegocapape.model.ConnectionInfo;
import org.ini4j.Ini;
import java.util.List;

@Slf4j
public class ConnectionManager {
    private static final String INI_FILE_PATH = "connections.ini";

    public static void saveConnection(ConnectionInfo connectionInfo) {
        try {
            Ini ini = new Ini(new File(INI_FILE_PATH));
            Ini.Section section = ini.add(connectionInfo.getUser());
            section.put("password", connectionInfo.getPassword());
            section.put("host", connectionInfo.getHost());
            section.put("port", connectionInfo.getPort());
            section.put("serviceName", connectionInfo.getServiceName());
            section.put("dialect", connectionInfo.getDialect());
            section.put("driver", connectionInfo.getDriver());
            ini.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<ConnectionInfo> getConnections() {
        List<ConnectionInfo> connections = new ArrayList<>();
        File iniFile = new File(INI_FILE_PATH);

        try {
            // Comprobar si el archivo existe, si no, crearlo
            if (!iniFile.exists()) {
                iniFile.createNewFile();
            }

            Ini ini = new Ini(iniFile);
            for (String sectionName : ini.keySet()) {
                Ini.Section section = ini.get(sectionName);
                ConnectionInfo connectionInfo = new ConnectionInfo();
                connectionInfo.setUser(sectionName);
                connectionInfo.setPassword(section.get("password"));
                connectionInfo.setHost(section.get("host"));
                connectionInfo.setPort(section.get("port"));
                connectionInfo.setServiceName(section.get("serviceName"));
                connectionInfo.setDialect(section.get("dialect"));
                connectionInfo.setDriver(section.get("driver"));
                connections.add(connectionInfo);
                log.info("ConnectionInfo: {}", connectionInfo);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return connections;
    }


    public static void deleteConnection(String user) {
        try {
            Ini ini = new Ini(new File(INI_FILE_PATH));
            ini.remove(user);
            ini.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
