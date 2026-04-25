package com.ruoyi.Xidian.config;

import com.ruoyi.Xidian.utils.MatlabEngineReuseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatlabEnginePrewarmRunner implements ApplicationRunner {

    @Value("${matlab.prewarm:true}")
    private boolean prewarm;

    @Value("${matlab.mode:auto}")
    private String matlabMode;

    @Value("${matlab.command:matlab}")
    private String matlabCommand;

    @Value("${matlab.timeout:300}")
    private long timeoutSeconds;

    @Override
    public void run(ApplicationArguments args) {
        if (!prewarm) return;

        String mode = matlabMode == null ? "auto" : matlabMode.trim().toLowerCase();
        if ("process".equals(mode)) return;

        if (!MatlabEngineReuseUtil.isEngineAvailable()) {
            log.warn("MATLAB Engine 不可用（未找到 engine jar），跳过预热。");
            return;
        }

        MatlabEngineReuseUtil.ExecutionResult r = MatlabEngineReuseUtil.warmup(matlabCommand, timeoutSeconds);
        if (r.success) {
            log.info("MATLAB Engine 预热成功：{}（{}ms）", r.stdout, r.durationMs);
        } else {
            log.warn("MATLAB Engine 预热失败：{}（{}ms）", r.stderr, r.durationMs);
        }
    }
}

