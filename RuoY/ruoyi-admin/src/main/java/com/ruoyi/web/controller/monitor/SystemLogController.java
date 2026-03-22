package com.ruoyi.web.controller.monitor;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.file.FileUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/monitor/system/log")
public class SystemLogController extends BaseController {
    /**
     * 系统日志列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:systemLog:list')")
    @GetMapping("/list")
    public TableDataInfo list() {
        List<String> list = new ArrayList<>();
        try{
            Stream<Path> stream = Files.list(Paths.get("/home/hyy1208/xidianProject/logs"));
            stream.forEach(path -> list.add(path.getFileName().toString()));
            stream.close();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
        return getDataTable(list);
    }
    @PreAuthorize("@ss.hasPermi('monitor:systemLog:preview')")
    @PostMapping("/preview/{fileName}")
    public AjaxResult preview(@PathVariable String fileName) {
        Map<String, Object> previewLogs = FileUtils.previewTxt(new File("/home/hyy1208/xidianProject/logs/" + fileName));
        return AjaxResult.success(previewLogs);
    }
}
