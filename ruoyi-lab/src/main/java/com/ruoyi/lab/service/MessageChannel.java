package com.ruoyi.lab.service;
import com.ruoyi.lab.dto.NotificationCommand;
public interface MessageChannel { String code(); long send(NotificationCommand command); }
