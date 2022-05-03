package com.ilubov.hikvision.config;

import com.ilubov.hikvision.vo.HkCameraParam;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 海康摄像头相关参数
 *
 * @author ilubov
 * @date 2022/5/3
 */
@Data
@Configuration
@ConfigurationProperties("hk")
public class HkCameraProperty {

    @ApiModelProperty("加载文件路径")
    private String sdkPath;

    @ApiModelProperty("保存图片路径")
    private String imgPath;

    @ApiModelProperty("车牌摄像头")
    private HkCameraParam plateNumber;

    @ApiModelProperty("全彩摄像头")
    private List<HkCameraParam> camera;
}
