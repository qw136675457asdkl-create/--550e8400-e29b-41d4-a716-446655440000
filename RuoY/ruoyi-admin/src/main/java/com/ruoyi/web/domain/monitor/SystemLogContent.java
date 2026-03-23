package com.ruoyi.web.domain.monitor;

/**
 * System log content preview result.
 */
public class SystemLogContent
{
    private String fileToken;

    private String fileName;

    private String directory;

    private Long size;

    private Long modifiedTime;

    private boolean active;

    private Integer requestedLines;

    private Integer returnedLines;

    private String content;

    public String getFileToken()
    {
        return fileToken;
    }

    public void setFileToken(String fileToken)
    {
        this.fileToken = fileToken;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getDirectory()
    {
        return directory;
    }

    public void setDirectory(String directory)
    {
        this.directory = directory;
    }

    public Long getSize()
    {
        return size;
    }

    public void setSize(Long size)
    {
        this.size = size;
    }

    public Long getModifiedTime()
    {
        return modifiedTime;
    }

    public void setModifiedTime(Long modifiedTime)
    {
        this.modifiedTime = modifiedTime;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }

    public Integer getRequestedLines()
    {
        return requestedLines;
    }

    public void setRequestedLines(Integer requestedLines)
    {
        this.requestedLines = requestedLines;
    }

    public Integer getReturnedLines()
    {
        return returnedLines;
    }

    public void setReturnedLines(Integer returnedLines)
    {
        this.returnedLines = returnedLines;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
