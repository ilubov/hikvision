package com.ilubov.hikvision.config;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 海康车牌摄像头相关参数
 *
 * @author ilubov
 * @date 2022/4/21
 */
@Data
@Configuration
@ConfigurationProperties("hk")
public class HkProperty {

    @ApiModelProperty("已登录设备的IP地址")
    private String deviceIp;

    @ApiModelProperty("设备用户名")
    private String username;

    @ApiModelProperty("设备密码")
    private String password;

    @ApiModelProperty("端口号")
    private Short port;

    @ApiModelProperty("加载文件路径")
    private String path;
}
