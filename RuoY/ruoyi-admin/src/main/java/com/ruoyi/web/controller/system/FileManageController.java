package com.ruoyi.web.controller.system;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletResponse;

import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.common.core.redis.RedisCache;
import nl.basjes.parse.useragent.utils.springframework.util.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.*; // 务必确保引入了 poi 和 poi-ooxml 依赖
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;

/**
 * 文件管理控制器
 *
 * 修复：重命名类名以避免 Bean 冲突
 */
@RestController
@RequestMapping("/api/file")
public class FileManageController extends BaseController
{
    // 【配置点】生产环境修改此路径
    private static final String BASE_DIR = "/home/hyy1208/data";

    private static final boolean ENABLE_PATH_RESTRICTION = true;

    @Autowired
    private RedisCache redisCache;

    private static final String KEY_PROGRESS = "download:progress:";
    private static final String KEY_PATH = "download:path:";
    private static final String KEY_ERROR = "download:error:";

    // 缓存过期时间 (30分钟)
    private static final Integer EXPIRE_TIME = 30;

    private boolean isPathSafe(String filePath) {
        if (!ENABLE_PATH_RESTRICTION) return true;
        try {
            Path requestPath = Paths.get(filePath).toAbsolutePath().normalize();
            Path basePath = Paths.get(BASE_DIR).toAbsolutePath().normalize();
            return requestPath.startsWith(basePath);
        } catch (Exception e) {
            return false;
        }
    }

    private String getSafePath(String path) {
        if (path == null || path.trim().isEmpty()) return BASE_DIR;
        try {
            String decodedPath = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            if (new File(decodedPath).isAbsolute()) return decodedPath;
            return Paths.get(BASE_DIR, decodedPath).toString();
        } catch (UnsupportedEncodingException e) {
            return BASE_DIR;
        }
    }

    private String detectFileType(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".log")) return "text";
        else if (name.endsWith(".json")) return "json";
        else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")) return "image";
        else if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".flv")) return "video";
        else if (name.endsWith(".pdf")) return "pdf";
        else if (name.endsWith(".xls") || name.endsWith(".xlsx")) return "excel";
        else return "binary";
    }

    @PostMapping("/inspect")
    public AjaxResult inspectFile(@RequestBody Map<String, String> params) {
        String path = params.get("path");
        String safePath = getSafePath(path);

        if (!isPathSafe(safePath)) return error("访问被拒绝: 路径超出允许范围");

        File file = new File(safePath);
        if (!file.exists()) return error("文件不存在");

        Map<String, Object> result = new HashMap<>();
        result.put("name", file.getName());
        result.put("path", safePath);
        result.put("isDir", file.isDirectory());
        result.put("size", file.length());
        result.put("modified", file.lastModified());
        result.put("fileType", detectFileType(file.getName()));
        try {
            result.put("contentUrl", "/api/file/content?path=" + java.net.URLEncoder.encode(safePath, StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            result.put("contentUrl", "");
        }

        return success(result);
    }

    @GetMapping("/list")
    public AjaxResult listFiles(@RequestParam(required = false) String path) {
        String safePath = getSafePath(path);
        if (!isPathSafe(safePath)) return error("访问被拒绝: 路径超出允许范围");

        File dir = new File(safePath);
        if (!dir.exists() || !dir.isDirectory()) return error("目录不存在");

        List<Map<String, Object>> fileList = new ArrayList<>();
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", file.getName());
                fileInfo.put("path", file.getAbsolutePath());
                fileInfo.put("isDir", file.isDirectory());
                fileInfo.put("size", file.length());
                fileInfo.put("modified", file.lastModified());
                if (!file.isDirectory()) {
                    fileInfo.put("fileType", detectFileType(file.getName()));
                }
                fileList.add(fileInfo);
            }
        }
        return success(fileList);
    }

    @GetMapping("/content")
    public void getFileContent(@RequestParam String path, HttpServletResponse response) {
        String safePath = getSafePath(path);
        if (!isPathSafe(safePath)) {
            response.setStatus(403);
            return;
        }

        File file = new File(safePath);
        if (!file.exists() || file.isDirectory()) {
            response.setStatus(404);
            return;
        }

        try {
            String fileType = detectFileType(file.getName());
            String contentType = getContentType(fileType);
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline; filename=" +
                java.net.URLEncoder.encode(file.getName(), StandardCharsets.UTF_8.name()));

            // 支持视频 Range 请求 (简单实现)
            if (fileType.equals("video")) {
                response.setHeader("Accept-Ranges", "bytes");
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                IOUtils.copy(fis, response.getOutputStream());
            }
        } catch (IOException e) {
            // ignore client abort
        }
    }

    @PostMapping("/rename")
    public AjaxResult renameFile(@RequestBody Map<String, String> params) {
        String oldPath = params.get("oldPath");
        String newName = params.get("newName");
        String safePath = getSafePath(oldPath);

        if (!isPathSafe(safePath)) return error("访问被拒绝");
        File oldFile = new File(safePath);
        if (!oldFile.exists()) return error("文件不存在");
        if (newName.contains("\\") || newName.contains("/")) return error("非法文件名");

        File newFile = new File(oldFile.getParent(), newName);
        return oldFile.renameTo(newFile) ? success("重命名成功") : error("重命名失败");
    }

    @PostMapping("/delete")
    @Log(title = "删除文件", businessType = BusinessType.DELETE)
    public AjaxResult deleteFile(@RequestBody Map<String, String> params) {
        String path = params.get("path");
        String safePath = getSafePath(path);
        if (!isPathSafe(safePath)) return error("访问被拒绝");

        File file = new File(safePath);
        if (!file.exists()) return error("文件不存在");

        try {
            if (file.isDirectory()) FileUtils.deleteDirectory(file);
            else FileUtils.forceDelete(file);
            return success("删除成功");
        } catch (IOException e) {
            return error("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/mkdir")
    @Log(title = "创建文件夹", businessType = BusinessType.INSERT)
    public AjaxResult createDirectory(@RequestBody Map<String, String> params) {
        String parentPath = params.get("path");
        String dirName = params.get("dirName");
        if (dirName.contains("\\") || dirName.contains("/")) return error("非法文件夹名");

        String safePath = getSafePath(parentPath);
        if (!isPathSafe(safePath)) return error("访问被拒绝");

        File newDir = new File(safePath, dirName);
        if (newDir.exists()) return error("文件夹已存在");
        return newDir.mkdirs() ? success("创建成功") : error("创建失败");
    }

    @PostMapping("/upload")
    @Log(title = "上传文件", businessType = BusinessType.INSERT)
    public AjaxResult uploadFile(@RequestParam("file") MultipartFile file, @RequestParam(required = false) String path) {
        String safePath = getSafePath(path);
        if (!isPathSafe(safePath)) return error("访问被拒绝");
        if (file.isEmpty()) return error("文件为空");

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.contains("\\") || fileName.contains("/")) return error("非法文件名");

        try {
            File dir = new File(safePath);
            if (!dir.exists()) dir.mkdirs();
            File targetFile = new File(safePath, fileName);
            file.transferTo(targetFile);
            return success("上传成功");
        } catch (IOException e) {
            return error("上传失败: " + e.getMessage());
        }
    }
    /**
     * 1. 提交任务
     */
    @PostMapping("/submit")
    public AjaxResult submitDownloadTask(@RequestBody List<String> paths) {
        if (CollectionUtils.isEmpty(paths)) return AjaxResult.error("文件路径不能为空");

        String taskKey = UUID.randomUUID().toString();

        // 初始化进度为 0，并设置过期时间
        redisCache.setCacheObject(KEY_PROGRESS + taskKey, 0, EXPIRE_TIME, TimeUnit.MINUTES);

        // 异步执行打包
        CompletableFuture.runAsync(() -> {
            performZipping(taskKey, paths);
        });

        return AjaxResult.success("任务已提交", taskKey);
    }
    /**
     * 2. 查询进度
     */
    @GetMapping("/progress/{taskKey}")
    public AjaxResult getProgress(@PathVariable String taskKey) {
        // 先检查是否有错误
        String error = redisCache.getCacheObject(KEY_ERROR + taskKey);
        if (error != null) {
            return AjaxResult.error(error);
        }

        // 获取进度
        Integer progress = redisCache.getCacheObject(KEY_PROGRESS + taskKey);

        if (progress == null) {
            return AjaxResult.error("任务不存在或已过期");
        }

        return AjaxResult.success(progress);
    }
    /**
     * 3. 下载文件
     */
    @GetMapping("/download/{taskKey}")
    public void downloadFile(@PathVariable String taskKey, HttpServletResponse response) {
        String zipFilePath = redisCache.getCacheObject(KEY_PATH + taskKey);

        if (zipFilePath == null) {
            // 简单处理，实际可跳转错误页
            return;
        }

        File file = new File(zipFilePath);
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"batch_download.zip\"");
            response.setContentLength((int) file.length());

            byte[] buffer = new byte[1024];
            int i;
            while ((i = bis.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, i);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // === 清理逻辑 ===
            // 1. 删除服务器上的临时文件
            file.delete();
            // 2. 删除 Redis 中的 key
            redisCache.deleteObject(KEY_PROGRESS + taskKey);
            redisCache.deleteObject(KEY_PATH + taskKey);
            redisCache.deleteObject(KEY_ERROR + taskKey);
        }
    }
    // --- 内部核心打包逻辑---
    private void performZipping(String taskKey, List<String> paths) {
        // 临时文件建议放在系统临时目录
        String tempDir = System.getProperty("java.io.tmpdir");
        File zipFile = new File(tempDir + File.separator + taskKey + ".zip");

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            int total = paths.size();
            for (int i = 0; i < total; i++) {
                String path = paths.get(i);
                File sourceFile = new File(path);

                if (sourceFile.exists()) {
                    ZipEntry zipEntry = new ZipEntry(sourceFile.getName());
                    zos.putNextEntry(zipEntry);
                    try (FileInputStream fis = new FileInputStream(sourceFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = fis.read(buffer)) >= 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }

                int percent = (int) (((double) (i + 1) / total) * 100);
                // 每次更新都重置过期时间，防止大文件打包时间过长导致 Key 过期
                redisCache.setCacheObject(KEY_PROGRESS + taskKey, percent, EXPIRE_TIME, TimeUnit.MINUTES);
            }

            // 打包完成：将"临时文件路径"存入 Redis，供下载接口读取
            redisCache.setCacheObject(KEY_PATH + taskKey, zipFile.getAbsolutePath(), EXPIRE_TIME, TimeUnit.MINUTES);

        } catch (Exception e) {
            e.printStackTrace();
            // 发生异常，记录到 Redis 供前端展示
            redisCache.setCacheObject(KEY_ERROR + taskKey, "打包失败: " + e.getMessage(), EXPIRE_TIME, TimeUnit.MINUTES);
        }
    }

    @GetMapping("/excel/preview")
    public AjaxResult previewExcel(@RequestParam String path) {
        String safePath = getSafePath(path);
        if (!isPathSafe(safePath)) return error("访问被拒绝");

        File file = new File(safePath);
        if (!file.exists()) return error("文件不存在");

        // 使用 WorkbookFactory 自动兼容 .xls 和 .xlsx
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<Map<String, Object>> data = new ArrayList<>();

            for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int colIdx = 0; colIdx < row.getLastCellNum(); colIdx++) {
                    Cell cell = row.getCell(colIdx);
                    rowData.put("col_" + colIdx, getCellValue(cell));
                }
                data.add(rowData);
            }
            return success(data);
        } catch (Exception e) {
            return error("预览失败: " + e.getMessage());
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    private String getContentType(String fileType) {
        switch (fileType) {
            case "text": return MediaType.TEXT_PLAIN_VALUE;
            case "json": return MediaType.APPLICATION_JSON_VALUE;
            case "image": return "image/*";
            case "video": return "video/*";
            case "pdf": return MediaType.APPLICATION_PDF_VALUE;
            case "excel": return "application/vnd.ms-excel";
            default: return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
