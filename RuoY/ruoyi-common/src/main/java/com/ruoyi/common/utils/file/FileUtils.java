package com.ruoyi.common.utils.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;

/**
 * 文件处理工具类
 * 
 * @author ruoyi
 */
public class FileUtils
{
    public static String FILENAME_PATTERN = "[a-zA-Z0-9_\\-\\|\\.\\u4e00-\\u9fa5]+";

    /**
     * 输出指定文件的byte数组
     * 
     * @param filePath 文件路径
     * @param os 输出流
     * @return
     */
    public static void writeBytes(String filePath, OutputStream os) throws IOException
    {
        FileInputStream fis = null;
        try
        {
            File file = new File(filePath);
            if (!file.exists())
            {
                throw new FileNotFoundException(filePath);
            }
            fis = new FileInputStream(file);
            byte[] b = new byte[1024];
            int length;
            while ((length = fis.read(b)) > 0)
            {
                os.write(b, 0, length);
            }
        }
        catch (IOException e)
        {
            throw e;
        }
        finally
        {
            IOUtils.close(os);
            IOUtils.close(fis);
        }
    }

    /**
     * 写数据到文件中
     *
     * @param data 数据
     * @return 目标文件
     * @throws IOException IO异常
     */
    public static String writeImportBytes(byte[] data) throws IOException
    {
        return writeBytes(data, RuoYiConfig.getImportPath());
    }

    /**
     * 写数据到文件中
     *
     * @param data 数据
     * @param uploadDir 目标文件
     * @return 目标文件
     * @throws IOException IO异常
     */
    public static String writeBytes(byte[] data, String uploadDir) throws IOException
    {
        FileOutputStream fos = null;
        String pathName = "";
        try
        {
            String extension = getFileExtendName(data);
            pathName = DateUtils.datePath() + "/" + IdUtils.fastUUID() + "." + extension;
            File file = FileUploadUtils.getAbsoluteFile(uploadDir, pathName);
            fos = new FileOutputStream(file);
            fos.write(data);
        }
        finally
        {
            IOUtils.close(fos);
        }
        return FileUploadUtils.getPathFileName(uploadDir, pathName);
    }

    /**
     * 移除路径中的请求前缀片段
     * 
     * @param filePath 文件路径
     * @return 移除后的文件路径
     */
    public static String stripPrefix(String filePath)
    {
        return StringUtils.substringAfter(filePath, Constants.RESOURCE_PREFIX);
    }

    /**
     * 删除文件
     * 
     * @param filePath 文件
     * @return
     */
    public static boolean deleteFile(String filePath)
    {
        boolean flag = false;
        File file = new File(filePath);
        // 路径为文件且不为空则进行删除
        if (file.isFile() && file.exists())
        {
            flag = file.delete();
        }
        return flag;
    }

    /**
     * 文件名称验证
     * 
     * @param filename 文件名称
     * @return true 正常 false 非法
     */
    public static boolean isValidFilename(String filename)
    {
        return filename.matches(FILENAME_PATTERN);
    }

    /**
     * 检查文件是否可下载
     * 
     * @param resource 需要下载的文件
     * @return true 正常 false 非法
     */
    public static boolean checkAllowDownload(String resource)
    {
        // 禁止目录上跳级别
        if (StringUtils.contains(resource, ".."))
        {
            return false;
        }

        // 检查允许下载的文件规则
        if (ArrayUtils.contains(MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, FileTypeUtils.getFileType(resource)))
        {
            return true;
        }

        // 不在允许下载的文件规则
        return false;
    }

    /**
     * 下载文件名重新编码
     * 
     * @param request 请求对象
     * @param fileName 文件名
     * @return 编码后的文件名
     */
    public static String setFileDownloadHeader(HttpServletRequest request, String fileName) throws UnsupportedEncodingException
    {
        final String agent = request.getHeader("USER-AGENT");
        String filename = fileName;
        if (agent.contains("MSIE"))
        {
            // IE浏览器
            filename = URLEncoder.encode(filename, "utf-8");
            filename = filename.replace("+", " ");
        }
        else if (agent.contains("Firefox"))
        {
            // 火狐浏览器
            filename = new String(fileName.getBytes(), "ISO8859-1");
        }
        else if (agent.contains("Chrome"))
        {
            // google浏览器
            filename = URLEncoder.encode(filename, "utf-8");
        }
        else
        {
            // 其它浏览器
            filename = URLEncoder.encode(filename, "utf-8");
        }
        return filename;
    }

    /**
     * 下载文件名重新编码
     *
     * @param response 响应对象
     * @param realFileName 真实文件名
     */
    public static void setAttachmentResponseHeader(HttpServletResponse response, String realFileName) throws UnsupportedEncodingException
    {
        String percentEncodedFileName = percentEncode(realFileName);

        StringBuilder contentDispositionValue = new StringBuilder();
        contentDispositionValue.append("attachment; filename=")
                .append(percentEncodedFileName)
                .append(";")
                .append("filename*=")
                .append("utf-8''")
                .append(percentEncodedFileName);

        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition,download-filename");
        response.setHeader("Content-disposition", contentDispositionValue.toString());
        response.setHeader("download-filename", percentEncodedFileName);
    }

    /**
     * 百分号编码工具方法
     *
     * @param s 需要百分号编码的字符串
     * @return 百分号编码后的字符串
     */
    public static String percentEncode(String s) throws UnsupportedEncodingException
    {
        String encode = URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        return encode.replaceAll("\\+", "%20");
    }

    /**
     * 获取图像后缀
     * 
     * @param photoByte 图像数据
     * @return 后缀名
     */
    public static String getFileExtendName(byte[] photoByte)
    {
        String strFileExtendName = "jpg";
        if ((photoByte[0] == 71) && (photoByte[1] == 73) && (photoByte[2] == 70) && (photoByte[3] == 56)
                && ((photoByte[4] == 55) || (photoByte[4] == 57)) && (photoByte[5] == 97))
        {
            strFileExtendName = "gif";
        }
        else if ((photoByte[6] == 74) && (photoByte[7] == 70) && (photoByte[8] == 73) && (photoByte[9] == 70))
        {
            strFileExtendName = "jpg";
        }
        else if ((photoByte[0] == 66) && (photoByte[1] == 77))
        {
            strFileExtendName = "bmp";
        }
        else if ((photoByte[1] == 80) && (photoByte[2] == 78) && (photoByte[3] == 71))
        {
            strFileExtendName = "png";
        }
        return strFileExtendName;
    }

    /**
     * 获取文件名称 /profile/upload/2022/04/16/ruoyi.png -- ruoyi.png
     * 
     * @param fileName 路径名称
     * @return 没有文件路径的名称
     */
    public static String getName(String fileName)
    {
        if (fileName == null)
        {
            return null;
        }
        int lastUnixPos = fileName.lastIndexOf('/');
        int lastWindowsPos = fileName.lastIndexOf('\\');
        int index = Math.max(lastUnixPos, lastWindowsPos);
        return fileName.substring(index + 1);
    }

    /**
     * 获取不带后缀文件名称 /profile/upload/2022/04/16/ruoyi.png -- ruoyi
     * 
     * @param fileName 路径名称
     * @return 没有文件路径和后缀的名称
     */
    public static String getNameNotSuffix(String fileName)
    {
        if (fileName == null)
        {
            return null;
        }
        String baseName = FilenameUtils.getBaseName(fileName);
        return baseName;
    }

    /**
     * 下载单个文件
     *
     * @param filePath 绝对路径
     * @param downloadName 下载文件名（为空时取原文件名）
     * @param response 响应对象
     */
    public static void downloadFile(String filePath, String downloadName, HttpServletResponse response)
    {
        if (StringUtils.isEmpty(filePath))
        {
            throw new ServiceException("文件路径不能为空");
        }
        if (StringUtils.contains(filePath, ".."))
        {
            throw new ServiceException("非法文件路径");
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile())
        {
            throw new ServiceException("文件不存在");
        }

        String realFileName = StringUtils.isEmpty(downloadName) ? file.getName() : downloadName;
        try
        {
            response.setContentType("application/octet-stream");
            setAttachmentResponseHeader(response, realFileName);
            writeBytes(file.getAbsolutePath(), response.getOutputStream());
        }
        catch (Exception e)
        {
            throw new ServiceException("文件下载失败: " + e.getMessage());
        }
    }

    public static List<Map<String, Object>> previewExcel(String filePath)
    {
        File file = new File(filePath);
        if (!file.exists())
        {
            throw new ServiceException("文件不存在");
        }

        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(".csv"))
        {
            return previewCsv(file);
        }

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis))
        {
            Sheet sheet = workbook.getSheetAt(0);
            List<Map<String, Object>> data = new ArrayList<>();

            for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++)
            {
                Row row = sheet.getRow(rowIdx);
                if (row == null)
                {
                    continue;
                }

                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int colIdx = 0; colIdx < row.getLastCellNum(); colIdx++)
                {
                    Cell cell = row.getCell(colIdx);
                    rowData.put("col_" + colIdx, getCellValue(cell));
                }
                data.add(rowData);
            }
            return data;
        }
        catch (Exception e)
        {
            throw new ServiceException("Preview failed: " + e.getMessage());
        }
    }

    public static Map<String, Object> previewExcelByPage(String filePath, int pageNum, int pageSize)
    {
        File file = new File(filePath);
        if (!file.exists())
        {
            throw new ServiceException("文件不存在");
        }

        int safePageNum = pageNum <= 0 ? 1 : pageNum;
        int safePageSize = pageSize <= 0 ? 200 : pageSize;
        int startIndex = (safePageNum - 1) * safePageSize;
        int endIndex = startIndex + safePageSize;

        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(".csv"))
        {
            return previewCsvByPage(file, safePageNum, safePageSize, startIndex, endIndex);
        }

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis))
        {
            Sheet sheet = workbook.getSheetAt(0);
            List<Map<String, Object>> pageRows = new ArrayList<>();
            int totalRows = 0;

            for (int rowIdx = 0; rowIdx <= sheet.getLastRowNum(); rowIdx++)
            {
                Row row = sheet.getRow(rowIdx);
                if (row == null)
                {
                    continue;
                }

                if (totalRows >= startIndex && totalRows < endIndex)
                {
                    Map<String, Object> rowData = new LinkedHashMap<>();
                    for (int colIdx = 0; colIdx < row.getLastCellNum(); colIdx++)
                    {
                        Cell cell = row.getCell(colIdx);
                        rowData.put("col_" + colIdx, getCellValue(cell));
                    }
                    pageRows.add(rowData);
                }
                totalRows++;
            }
            return buildPreviewPageResult(pageRows, totalRows, safePageNum, safePageSize);
        }
        catch (Exception e)
        {
            throw new ServiceException("Preview failed: " + e.getMessage());
        }
    }

    private static List<Map<String, Object>> previewCsv(File file)
    {
        List<Map<String, Object>> data = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                List<String> cells = parseCsvLine(line);
                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int colIdx = 0; colIdx < cells.size(); colIdx++)
                {
                    rowData.put("col_" + colIdx, cells.get(colIdx));
                }
                data.add(rowData);
            }
            return data;
        }
        catch (Exception e)
        {
            throw new ServiceException("预览失败：" + e.getMessage());
        }
    }

    private static Map<String, Object> previewCsvByPage(File file, int pageNum, int pageSize, int startIndex, int endIndex)
    {
        List<Map<String, Object>> pageRows = new ArrayList<>();
        int totalRows = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
        {
            String line;
            //将第一行作为表头
            line = reader.readLine();
            List<String> headers = parseCsvLine(line);

            while ((line = reader.readLine()) != null)
            {
                if (totalRows >= startIndex && totalRows < endIndex)
                {
                    List<String> cells = parseCsvLine(line);
                    Map<String, Object> rowData = new LinkedHashMap<>();
                    for (int colIdx = 0; colIdx < cells.size(); colIdx++)
                    {
                        rowData.put(headers.get(colIdx), cells.get(colIdx));
                    }
                    pageRows.add(rowData);
                }
                totalRows++;
            }
            return buildPreviewPageResult(pageRows, totalRows, pageNum, pageSize);
        }
        catch (Exception e)
        {
            throw new ServiceException("Preview failed: " + e.getMessage());
        }
    }

    private static Map<String, Object> buildPreviewPageResult(List<Map<String, Object>> rows, int total, int pageNum, int pageSize)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    private static List<String> parseCsvLine(String line)
    {
        List<String> cells = new ArrayList<>();
        if (line == null)
        {
            return cells;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++)
        {
            char ch = line.charAt(i);
            if (ch == '"')
            {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"')
                {
                    current.append('"');
                    i++;
                }
                else
                {
                    inQuotes = !inQuotes;
                }
            }
            else if (ch == ',' && !inQuotes)
            {
                cells.add(current.toString());
                current.setLength(0);
            }
            else
            {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static String getCellValue(Cell cell)
    {
        if (cell == null)
        {
            return "";
        }
        switch (cell.getCellType())
        {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell))
                {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
