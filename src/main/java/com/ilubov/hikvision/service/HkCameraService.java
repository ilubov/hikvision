package com.ilubov.hikvision.service;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.ilubov.hikvision.config.HkCameraProperty;
import com.ilubov.hikvision.sdk.HCNetSDK;
import com.ilubov.hikvision.util.OsSelect;
import com.ilubov.hikvision.vo.HkCameraParam;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

/**
 * 海康车牌摄像头初始化
 *
 * @author ilubov
 * @date 2022/5/3
 */
@Slf4j
@Component
public class HkCameraService {

    @Autowired
    private HkCameraProperty property;

    static HCNetSDK hCNetSDK = null;

    // rtsp://${username}:${password}@${ip}:554/h265/ch1/main/av_stream
    private static final String RTSP_URL = "rtsp://%s:%s@%s:554/h265/ch1/main/av_stream";

    /**
     * 海康车牌摄像头初始化
     */
    @PostConstruct
    public void init() {
        HkCameraParam plateNumber = property.getPlateNumber();
        this.plateNumberInit(plateNumber.getDeviceIp(), plateNumber.getUsername(), plateNumber.getPassword(), plateNumber.getPort());
    }

    /**
     * 海康车牌摄像头初始化
     */
    public void plateNumberInit(String deviceIp, String username, String password, Short port) {
        if (hCNetSDK == null) {
            if (!this.createSDKInstance()) {
                log.info("【海康车牌摄像头初始化】加载SDK失败");
                return;
            }
        }
        // 初始化
        if (!hCNetSDK.NET_DVR_Init()) {
            log.info("【海康车牌摄像头初始化】初始化失败");
            return;
        }
        log.info("【海康车牌摄像头初始化】初始化成功");
        // 设置连接时间与重连时间
        this.connect();
        // 设备信息
        int lUserID = this.login(deviceIp, username, password, port);
        if (lUserID < 0) {
            return;
        }
        // 启用布防
        this.setupAlarm(lUserID);
        // 设置报警回调函数
        new Thread(() -> {
            try {
                for (; ; ) {
                    this.setMessageCallBack();
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 动态库加载
     */
    private boolean createSDKInstance() {
        if (hCNetSDK == null) {
            synchronized (HCNetSDK.class) {
                String path;
                if (OsSelect.isWindows()) {
                    path = "\\HCNetSDK.dll";
                } else {
                    path = "/libhcnetsdk.so";
                }
                log.info("【海康初始化】LOAD_PATH: {}", property.getSdkPath() + path);
                hCNetSDK = (HCNetSDK) Native.loadLibrary(property.getSdkPath() + path, HCNetSDK.class);
                // linux系统建议调用以下接口加载组件库
                this.loadLinuxLib();
            }
        }
        return true;
    }

    /**
     * linux系统建议调用以下接口加载组件库
     */
    private void loadLinuxLib() {
        if (OsSelect.isWindows()) {
            return;
        }
        // linux系统建议调用以下接口加载组件库
        log.info("【海康初始化】linux系统加载组件库");
        HCNetSDK.BYTE_ARRAY ptrByteArray1 = new HCNetSDK.BYTE_ARRAY(256);
        HCNetSDK.BYTE_ARRAY ptrByteArray2 = new HCNetSDK.BYTE_ARRAY(256);
        //这里是库的绝对路径，请根据实际情况修改，注意改路径必须有访问权限
        String strPathCom = property.getSdkPath();
        String strPath1 = strPathCom + "/libcrypto.so.1.1";
        String strPath2 = strPathCom + "/libssl.so.1.1";

        System.arraycopy(strPath1.getBytes(), 0, ptrByteArray1.byValue, 0, strPath1.length());
        ptrByteArray1.write();
        hCNetSDK.NET_DVR_SetSDKInitCfg(3, ptrByteArray1.getPointer());

        System.arraycopy(strPath2.getBytes(), 0, ptrByteArray2.byValue, 0, strPath2.length());
        ptrByteArray2.write();
        hCNetSDK.NET_DVR_SetSDKInitCfg(4, ptrByteArray2.getPointer());

        HCNetSDK.NET_DVR_LOCAL_SDK_PATH struComPath = new HCNetSDK.NET_DVR_LOCAL_SDK_PATH();
        System.arraycopy(strPathCom.getBytes(), 0, struComPath.sPath, 0, strPathCom.length());
        struComPath.write();
        hCNetSDK.NET_DVR_SetSDKInitCfg(2, struComPath.getPointer());
    }

    /**
     * 设置链接事件与重连时间
     */
    private void connect() {
        // 设置连接时间与重连时间
        hCNetSDK.NET_DVR_SetConnectTime(2000, 1);
        hCNetSDK.NET_DVR_SetReconnect(10000, true);
    }

    /**
     * 注册设备
     */
    private int login(String deviceIp, String username, String password, Short port) {
        // 设备信息, 输出参数
        HCNetSDK.NET_DVR_DEVICEINFO_V40 m_strDeviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();
        HCNetSDK.NET_DVR_USER_LOGIN_INFO m_strLoginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        // 注册设备-登录参数，包括设备地址、登录用户、密码等
        m_strLoginInfo.sDeviceAddress = new byte[hCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        System.arraycopy(deviceIp.getBytes(), 0, m_strLoginInfo.sDeviceAddress, 0, deviceIp.length());
        m_strLoginInfo.sUserName = new byte[hCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        System.arraycopy(username.getBytes(), 0, m_strLoginInfo.sUserName, 0, username.length());
        m_strLoginInfo.sPassword = new byte[hCNetSDK.NET_DVR_LOGIN_PASSWD_MAX_LEN];
        System.arraycopy(password.getBytes(), 0, m_strLoginInfo.sPassword, 0, password.length());
        m_strLoginInfo.wPort = port;
        // 是否异步登录：0- 否，1- 是
        m_strLoginInfo.bUseAsynLogin = false;
        m_strLoginInfo.write();
        // 设备信息, 输出参数
        int lUserID = hCNetSDK.NET_DVR_Login_V40(m_strLoginInfo, m_strDeviceInfo);
        if (lUserID < 0) {
            log.info("【海康车牌摄像头初始化】注册登录失败: {}", hCNetSDK.NET_DVR_GetErrorMsg(null));
            hCNetSDK.NET_DVR_Cleanup();
            return -1;
        }
        return lUserID;
    }

    /**
     * 设置回调
     */
    private void setMessageCallBack() {
        // 设置报警回调函数
        hCNetSDK.NET_DVR_SetDVRMessageCallBack_V31(this::callback, null);
    }

    /**
     * 启用布防
     */
    private void setupAlarm(int lUserID) {
        // 启用布防
        HCNetSDK.NET_DVR_SETUPALARM_PARAM lpSetupParam = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
        lpSetupParam.dwSize = 0;
        // 布防优先级：0- 一等级（高），1- 二等级（中）
        lpSetupParam.byLevel = 1;
        // 上传报警信息类型: 0- 老报警信息(NET_DVR_PLATE_RESULT), 1- 新报警信息(NET_ITS_PLATE_RESULT)
        lpSetupParam.byAlarmInfoType = 1;
        int lAlarmHandle = hCNetSDK.NET_DVR_SetupAlarmChan_V41(lUserID, lpSetupParam);
        if (lAlarmHandle < 0) {
            log.info("【海康车牌摄像头初始化】启用布防失败: {}", hCNetSDK.NET_DVR_GetLastError());
            hCNetSDK.NET_DVR_Logout(lUserID);
            hCNetSDK.NET_DVR_Cleanup();
            return;
        }
        log.info("【海康车牌摄像头初始化】布防成功,开始监测车辆");
    }

    /**
     * SDK时间解析
     */
    public String parseTime(int time) {
        int year = (time >> 26) + 2000;
        int month = (time >> 22) & 15;
        int day = (time >> 17) & 31;
        int hour = (time >> 12) & 31;
        int min = (time >> 6) & 63;
        int second = (time) & 63;
        return year + "-" + month + "-" + day + "-" + hour + ":" + min + ":" + second;
    }

    /**
     * 回调
     */
    public boolean callback(int lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
        log.info("【海康车牌摄像头回调】进入回调 开始识别车牌 lCommand: 0x{}", Integer.toHexString(lCommand));
        switch (lCommand) {
            case HCNetSDK.COMM_ITS_PLATE_RESULT:
                log.info("【海康车牌摄像头回调】交通抓拍的终端图片上传");
                HCNetSDK.NET_ITS_PLATE_RESULT strItsPlateResult = new HCNetSDK.NET_ITS_PLATE_RESULT();
                strItsPlateResult.write();
                Pointer pItsPlateInfo = strItsPlateResult.getPointer();
                pItsPlateInfo.write(0, pAlarmInfo.getByteArray(0, strItsPlateResult.size()), 0, strItsPlateResult.size());
                strItsPlateResult.read();
                try {
                    String license = new String(strItsPlateResult.struPlateInfo.sLicense, "GBK");
                    // 车型识别：0- 未知，1- 客车(大型)，2- 货车(大型)，3- 轿车(小型)，4- 非机动车
                    String type = strItsPlateResult.byVehicleType + "".trim();
                    String plateNumber = license.substring(1).trim();
                    String byCountry = license.substring(1, 2).trim();
                    String byColor = license.substring(0, 1).trim();
                    log.info("【海康车牌摄像头回调】车辆类型: {}", type);
                    log.info("【海康车牌摄像头回调】车牌号: {}", plateNumber);
                    log.info("【海康车牌摄像头回调】车牌省份: {}", byCountry);
                    log.info("【海康车牌摄像头回调】车牌颜色: {}", byColor);
                    // 报警图片保存，车牌，车辆图片
                    for (int i = 0; i < strItsPlateResult.dwPicNum; i++) {
                        HCNetSDK.NET_ITS_PICTURE_INFO pic = strItsPlateResult.struPicInfo[i];
                        if (pic.dwDataLen > 0) {
                            // 0.车牌照片 1.场景照片
                            byte byType = pic.byType;
                            ByteBuffer buffers = pic.pBuffer.getByteBuffer(0, pic.dwDataLen);
                            byte[] bytes = new byte[pic.dwDataLen];
                            buffers.rewind();
                            buffers.get(bytes);
                            log.info("【海康车牌摄像头回调】图片类型: {}", byType);
                            // 写到本地
                            String imgPath = this.writeFile(bytes, String.valueOf(byType));
                        }
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case HCNetSDK.COMM_VEHICLE_CONTROL_ALARM:
                log.info("【海康车牌摄像头回调】车辆报警上传");
                break;
        }
        return true;
    }

    /**
     * 写到本地
     */
    private String writeFile(byte[] bs, String suffix) {
        String filename = UUID.randomUUID() + "_" + suffix + ".jpg", path = property.getImgPath() + filename;
        File file = new File(path);
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            bos.write(bs);
        } catch (Exception e) {
            log.error("【海康摄像头写入图片失败】", e);
            return null;
        }
        return path;
    }

    /**
     * 海康全彩摄像头拍照
     */
    public List<String> takePhoto() {
        List<String> imgList = Lists.newArrayList();
        List<HkCameraParam> cameraList = property.getCamera();
        for (HkCameraParam camera : cameraList) {
            long start = System.currentTimeMillis();
            String img = this.takePhoto(camera.getDeviceIp(),
                    camera.getUsername(), camera.getPassword(), camera.getPort());
            if (StrUtil.isNotBlank(img)) {
                imgList.add(img);
            }
            log.info("【海康全彩摄像头】ip: {}, 拍照用时: {}ms", camera.getDeviceIp(), (System.currentTimeMillis() - start));
        }
        return imgList;
    }

    /**
     * 海康全彩摄像头拍照
     */
    public String takePhoto(String deviceIp, String username, String password, Short port) {
        // 设备信息
        int lUserID = this.cameraInit(deviceIp, username, password, port);
        if (lUserID < 0) {
            return null;
        }
        // 写到本地
        return this.writeFile(this.takePhoto(lUserID), deviceIp.substring(deviceIp.lastIndexOf(".") + 1));
    }

    /**
     * 海康全彩摄像头初始化
     */
    private int cameraInit(String deviceIp, String username, String password, Short port) {
        if (hCNetSDK == null) {
            if (!this.createSDKInstance()) {
                log.info("【海康全彩摄像头】加载SDK失败");
                return -1;
            }
        }
        // 初始化
        if (!hCNetSDK.NET_DVR_Init()) {
            log.info("【海康全彩摄像头】初始化失败");
            return -1;
        }
        log.info("【海康全彩摄像头】初始化成功");
        // 设备信息
        return this.login(deviceIp, username, password, port);
    }

    /**
     * 海康全彩摄像头拍照
     */
    private byte[] takePhoto(int lUserID) {
        // JPEG图像参数
        HCNetSDK.NET_DVR_JPEGPARA lpJpegPara = new HCNetSDK.NET_DVR_JPEGPARA();
        // 设置图片的分辨率
        lpJpegPara.wPicSize = 2;
        // 设置图片质量
        lpJpegPara.wPicQuality = 0;
        // 通道号、输入缓冲区大小
        int channel = 1, dwPicSize = 1024 * 1024;
        // 保存JPEG数据的缓冲区
        Pointer sJpegPicBuffer = new Memory(dwPicSize);
        // 返回图片数据的大小
        IntByReference reference = new IntByReference();
        // 抓拍
        hCNetSDK.NET_DVR_CaptureJPEGPicture_NEW(lUserID, channel, lpJpegPara, sJpegPicBuffer, dwPicSize, reference);
        // 图片大小
        int value = reference.getValue();
        log.info("【海康全彩摄像头】图片大小: {}", value);
        // 图片byte
        return sJpegPicBuffer.getByteArray(0, value);
    }
}
