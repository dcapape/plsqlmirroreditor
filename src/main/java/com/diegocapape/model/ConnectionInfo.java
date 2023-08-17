package com.diegocapape.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ConnectionInfo {
    private String user;
    private String password;
    private String host;
    private String port;
    private String serviceName;
    private String dialect;
    private String driver;
}