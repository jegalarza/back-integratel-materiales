package com.automatizacion.component;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class SshConfig {

    @Value("${ssh.host}")
    private String host;

    @Value("${ssh.port}")
    private int port;

    @Value("${ssh.user}")
    private String user;

    @Value("${ssh.password}")
    private String password;

    @Value("${ssh.remote.path}")
    private String remotePath;

}
