package io.pragmatic.shell.safety;

/**
 * 命令安全分级。
 */
public enum CommandRisk {
    READ,        // ls、cat、grep、ps、curl GET
    WRITE,       // cp、mv、mkdir
    DESTRUCTIVE, // rm、kill、systemctl restart
    CRITICAL     // rm -rf /、dd、mkfs、sudo
}
