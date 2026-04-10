package com.automatizacion.component;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class SftpConfig {

    @Value("${uat1.sftp.host}")
    private String hostUat1;

    @Value("${uat2.sftp.host}")
    private String hostUat2;

    @Value("${uat3.sftp.host}")
    private String hostUat3;

    @Value("${uat4.sftp.host}")
    private String hostUat4;

    @Value("${sftp.port}")
    private int port;

    @Value("${sftp.user}")
    private String user;

    @Value("${sftp.password}")
    private String password;

    @Value("${sftp.remote.path}")
    private String remotePath;

    @Value("${sftp.script.order}")
    private String scriptOrder;

    @Value("${sftp.script.request}")
    private String scriptRequest;

}
