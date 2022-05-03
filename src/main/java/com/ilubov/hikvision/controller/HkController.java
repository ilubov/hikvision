package com.ilubov.hikvision.controller;

import com.ilubov.hikvision.service.HkCameraService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "海康摄像头测试接口")
@RestController
@RequestMapping("/hk")
@CrossOrigin("*")
public class HkController {

    @Autowired
    private HkCameraService hkCameraService;

    @ApiOperation("车牌摄像头初始化测试")
    @GetMapping("/init")
    public String init() {
        hkCameraService.init();
        return "init成功";
    }

    @ApiOperation("全彩摄像头拍照")
    @GetMapping("/takePhoto")
    public List<String> takePhoto() {
        return hkCameraService.takePhoto();
    }
}
