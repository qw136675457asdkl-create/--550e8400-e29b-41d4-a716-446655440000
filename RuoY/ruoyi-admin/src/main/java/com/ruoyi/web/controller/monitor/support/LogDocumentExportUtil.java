package com.ruoyi.web.controller.monitor.support;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.domain.SysOperLog;

/**
 * Word/PDF export helper for monitor log module only.
 */
public final class LogDocumentExportUtil
{
    private LogDocumentExportUtil()
    {
    }

    public static void exportOperLogWord(HttpServletResponse response, List<SysOperLog> data) throws IOException
    {
        String[] headers = { "ID", "Module", "BusinessType", "RequestMethod", "Operator", "IP", "Location", "Status", "OperTime", "CostTime(ms)" };
        writeWord(response, "operlog", headers, data, item -> new String[] {
                toString(item.getOperId()),
                safe(item.getTitle()),
                formatBusinessType(item.getBusinessType()),
                safe(item.getRequestMethod()),
                safe(item.getOperName()),
                safe(item.getOperIp()),
                safe(item.getOperLocation()),
                formatOperStatus(item.getStatus()),
                formatDate(item.getOperTime()),
                toString(item.getCostTime()) });
    }

    public static void exportOperLogPdf(HttpServletResponse response, List<SysOperLog> data) throws Exception
    {
        String[] headers = { "ID", "Module", "BusinessType", "RequestMethod", "Operator", "IP", "Location", "Status", "OperTime", "CostTime(ms)" };
        writePdf(response, "operlog", "Operation Log", headers, data, item -> new String[] {
                toString(item.getOperId()),
                safe(item.getTitle()),
                formatBusinessType(item.getBusinessType()),
                safe(item.getRequestMethod()),
                safe(item.getOperName()),
                safe(item.getOperIp()),
                safe(item.getOperLocation()),
                formatOperStatus(item.getStatus()),
                formatDate(item.getOperTime()),
                toString(item.getCostTime()) });
    }

    public static void exportLoginInfoWord(HttpServletResponse response, List<SysLogininfor> data) throws IOException
    {
        String[] headers = { "ID", "Username", "IP", "Location", "Browser", "OS", "Status", "Message", "LoginTime" };
        writeWord(response, "logininfor", headers, data, item -> new String[] {
                toString(item.getInfoId()),
                safe(item.getUserName()),
                safe(item.getIpaddr()),
                safe(item.getLoginLocation()),
                safe(item.getBrowser()),
                safe(item.getOs()),
                formatLoginStatus(item.getStatus()),
                safe(item.getMsg()),
                formatDate(item.getLoginTime()) });
    }

    public static void exportLoginInfoPdf(HttpServletResponse response, List<SysLogininfor> data) throws Exception
    {
        String[] headers = { "ID", "Username", "IP", "Location", "Browser", "OS", "Status", "Message", "LoginTime" };
        writePdf(response, "logininfor", "Login Log", headers, data, item -> new String[] {
                toString(item.getInfoId()),
                safe(item.getUserName()),
                safe(item.getIpaddr()),
                safe(item.getLoginLocation()),
                safe(item.getBrowser()),
                safe(item.getOs()),
                formatLoginStatus(item.getStatus()),
                safe(item.getMsg()),
                formatDate(item.getLoginTime()) });
    }

    private static <T> void writeWord(HttpServletResponse response, String filePrefix, String[] headers, List<T> data,
            RowMapper<T> rowMapper) throws IOException
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setCharacterEncoding("utf-8");
        setAttachmentHeader(response, filePrefix + "_" + DateUtils.dateTimeNow(DateUtils.YYYYMMDDHHMMSS) + ".docx");

        try (XWPFDocument doc = new XWPFDocument())
        {
            XWPFTable table = doc.createTable(1, headers.length);
            XWPFTableRow headerRow = table.getRow(0);
            for (int i = 0; i < headers.length; i++)
            {
                headerRow.getCell(i).setText(headers[i]);
            }

            for (T rowObj : data)
            {
                String[] rowValues = rowMapper.map(rowObj);
                XWPFTableRow row = table.createRow();
                for (int i = 0; i < headers.length; i++)
                {
                    row.getCell(i).setText(i < rowValues.length ? safe(rowValues[i]) : "");
                }
            }

            doc.write(response.getOutputStream());
        }
    }

    private static <T> void writePdf(HttpServletResponse response, String filePrefix, String title, String[] headers, List<T> data,
            RowMapper<T> rowMapper) throws Exception
    {
        response.setContentType("application/pdf");
        response.setCharacterEncoding("utf-8");
        setAttachmentHeader(response, filePrefix + "_" + DateUtils.dateTimeNow(DateUtils.YYYYMMDDHHMMSS) + ".pdf");

        Font titleFont = buildPdfFont(14f, Font.BOLD);
        Font headerFont = buildPdfFont(10f, Font.BOLD);
        Font bodyFont = buildPdfFont(9f, Font.NORMAL);

        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();
        try
        {
            Paragraph titleParagraph = new Paragraph(title, titleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            titleParagraph.setSpacingAfter(10f);
            document.add(titleParagraph);

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100f);
            for (String header : headers)
            {
                table.addCell(buildCell(header, headerFont, true));
            }

            for (T rowObj : data)
            {
                String[] rowValues = rowMapper.map(rowObj);
                for (int i = 0; i < headers.length; i++)
                {
                    String text = i < rowValues.length ? safe(rowValues[i]) : "";
                    table.addCell(buildCell(text, bodyFont, false));
                }
            }

            document.add(table);
        }
        finally
        {
            document.close();
        }
    }

    private static PdfPCell buildCell(String text, Font font, boolean header)
    {
        PdfPCell cell = new PdfPCell(new Paragraph(safe(text), font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4f);
        if (header)
        {
            cell.setGrayFill(0.9f);
        }
        return cell;
    }

    private static Font buildPdfFont(float size, int style)
    {
        try
        {
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(baseFont, size, style);
        }
        catch (DocumentException | IOException ignored)
        {
            return new Font(Font.HELVETICA, size, style);
        }
    }

    private static String formatBusinessType(Integer businessType)
    {
        if (businessType == null)
        {
            return "";
        }
        switch (businessType)
        {
            case 1:
                return "INSERT";
            case 2:
                return "UPDATE";
            case 3:
                return "DELETE";
            case 4:
                return "GRANT";
            case 5:
                return "EXPORT";
            case 6:
                return "IMPORT";
            case 7:
                return "FORCE";
            case 8:
                return "GEN_CODE";
            case 9:
                return "CLEAN";
            default:
                return "OTHER";
        }
    }

    private static String formatOperStatus(Integer status)
    {
        if (status == null)
        {
            return "";
        }
        return Integer.valueOf(0).equals(status) ? "NORMAL" : "EXCEPTION";
    }

    private static String formatLoginStatus(String status)
    {
        if ("0".equals(status))
        {
            return "SUCCESS";
        }
        if ("1".equals(status))
        {
            return "FAIL";
        }
        return safe(status);
    }

    private static String formatDate(Date date)
    {
        return date == null ? "" : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, date);
    }

    private static String toString(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static void setAttachmentHeader(HttpServletResponse response, String fileName) throws IOException
    {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    private interface RowMapper<T>
    {
        String[] map(T row);
    }
}

