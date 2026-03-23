package com.ruoyi.web.controller.monitor;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.web.controller.monitor.support.LogDocumentExportUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/monitor/system/log")
public class SystemLogController extends BaseController
{
    private static final String LOG_PATH = "/home/hyy1208/xidianProject/logs";

    @PreAuthorize("@ss.hasPermi('monitor:systemLog:list')")
    @GetMapping("/list")
    public TableDataInfo list()
    {
        List<String> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Paths.get(LOG_PATH)))
        {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> list.add(path.getFileName().toString()));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('monitor:systemLog:preview')")
    @PostMapping("/preview/{fileName}")
    public AjaxResult preview(@PathVariable String fileName)
    {
        Map<String, Object> previewLogs = FileUtils.previewTxt(resolveLogFile(fileName).toFile());
        return AjaxResult.success(previewLogs);
    }

    @PreAuthorize("@ss.hasPermi('monitor:systemLog:download')")
    @PostMapping("/download/{fileName}")
    @Log(title = "下载系统日志", businessType = BusinessType.EXPORT)
    public void download(@PathVariable String fileName, HttpServletResponse response)
    {
        Path logFile = resolveLogFile(fileName);
        FileUtils.downloadFile(logFile.toString(), logFile.getFileName().toString(), response);
    }

    @PreAuthorize("@ss.hasPermi('monitor:systemLog:download')")
    @PostMapping("/export/word/{fileName}")
    @Log(title = "导出系统日志Word", businessType = BusinessType.EXPORT)
    public void exportWord(@PathVariable String fileName, HttpServletResponse response) throws Exception
    {
        Path logFile = resolveLogFile(fileName);
        LogDocumentExportUtil.exportSystemLogWord(response, logFile.getFileName().toString(), readLogLines(logFile));
    }

    @PreAuthorize("@ss.hasPermi('monitor:systemLog:download')")
    @PostMapping("/export/pdf/{fileName}")
    @Log(title = "导出系统日志PDF", businessType = BusinessType.EXPORT)
    public void exportPdf(@PathVariable String fileName, HttpServletResponse response) throws Exception
    {
        Path logFile = resolveLogFile(fileName);
        LogDocumentExportUtil.exportSystemLogPdf(response, logFile.getFileName().toString(), readLogLines(logFile));
    }

    private Path resolveLogFile(String fileName)
    {
        if (!FileUtils.isValidFilename(fileName))
        {
            throw new ServiceException("非法日志文件名");
        }

        try
        {
            Path basePath = Paths.get(LOG_PATH).toAbsolutePath().normalize();
            Path logFile = basePath.resolve(fileName).normalize();
            if (!logFile.startsWith(basePath) || !Files.exists(logFile) || !Files.isRegularFile(logFile))
            {
                throw new ServiceException("日志文件不存在");
            }
            return logFile;
        }
        catch (InvalidPathException ex)
        {
            throw new ServiceException("日志文件路径不合法");
        }
    }

    private List<String> readLogLines(Path logFile)
    {
        try
        {
            return Files.readAllLines(logFile, StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new ServiceException("读取日志文件失败: " + ex.getMessage());
        }
    }
}
