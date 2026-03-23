package com.ruoyi.web.domain.monitor;

/**
 * System log file metadata.
 */
public class SystemLogFileInfo
{
    private String fileToken;

    private String fileName;

    private String directory;

    private Long size;

    private Long modifiedTime;

    private boolean active;

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
}
