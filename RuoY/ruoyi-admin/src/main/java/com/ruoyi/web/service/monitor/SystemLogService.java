package com.ruoyi.web.service.monitor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletResponse;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.web.domain.monitor.SystemLogContent;
import com.ruoyi.web.domain.monitor.SystemLogFileInfo;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runtime system log reader service.
 */
@Service
public class SystemLogService
{
    private static final int DEFAULT_LINES = 300;

    private static final int MAX_LINES = 2000;

    private static final int READ_BLOCK_SIZE = 8192;

    private static final String LOG_SUFFIX = ".log";

    public List<SystemLogFileInfo> listLogFiles()
    {
        Set<Path> activeLogFiles = resolveActiveLogFiles();
        List<SystemLogFileInfo> files = new ArrayList<SystemLogFileInfo>();
        Set<String> visited = new HashSet<String>();

        for (Path directory : resolveLogDirectories())
        {
            if (!Files.isDirectory(directory))
            {
                continue;
            }

            try (Stream<Path> stream = Files.list(directory))
            {
                stream.filter(this::isLogFile)
                        .forEach(path -> addLogFile(files, visited, activeLogFiles, path));
            }
            catch (IOException e)
            {
                throw new ServiceException("Failed to read system log directory: " + directory + ", " + e.getMessage());
            }
        }

        Collections.sort(files, Comparator
                .comparing(SystemLogFileInfo::isActive).reversed()
                .thenComparing(SystemLogFileInfo::getModifiedTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SystemLogFileInfo::getFileName));
        return files;
    }

    public List<String> listLogDirectories()
    {
        List<String> directories = new ArrayList<String>();
        for (Path directory : resolveLogDirectories())
        {
            directories.add(directory.toString());
        }
        return directories;
    }

    public SystemLogContent readLogContent(String fileToken, Integer lines)
    {
        Path logFile = resolveAllowedLogFile(fileToken);
        Set<Path> activeLogFiles = resolveActiveLogFiles();
        int lineLimit = normalizeLineLimit(lines);
        List<String> tailLines = readLastLines(logFile, lineLimit);

        SystemLogContent content = new SystemLogContent();
        content.setFileToken(encodeToken(logFile));
        content.setFileName(logFile.getFileName().toString());
        content.setDirectory(logFile.getParent() == null ? "" : logFile.getParent().toString());
        content.setSize(safeFileSize(logFile));
        content.setModifiedTime(safeModifiedTime(logFile));
        content.setActive(activeLogFiles.contains(logFile));
        content.setRequestedLines(lineLimit);
        content.setReturnedLines(tailLines.size());
        content.setContent(String.join("\n", tailLines));
        return content;
    }

    public void downloadLogFile(String fileToken, HttpServletResponse response) throws Exception
    {
        Path logFile = resolveAllowedLogFile(fileToken);
        response.setContentType("application/octet-stream");
        FileUtils.setAttachmentResponseHeader(response, logFile.getFileName().toString());
        FileUtils.writeBytes(logFile.toString(), response.getOutputStream());
    }

    private void addLogFile(List<SystemLogFileInfo> files, Set<String> visited, Set<Path> activeLogFiles, Path logFile)
    {
        Path realPath = toRealPath(logFile);
        if (realPath == null)
        {
            return;
        }

        String uniqueKey = realPath.toString();
        if (!visited.add(uniqueKey))
        {
            return;
        }

        SystemLogFileInfo info = new SystemLogFileInfo();
        info.setFileToken(encodeToken(realPath));
        info.setFileName(realPath.getFileName().toString());
        info.setDirectory(realPath.getParent() == null ? "" : realPath.getParent().toString());
        info.setSize(safeFileSize(realPath));
        info.setModifiedTime(safeModifiedTime(realPath));
        info.setActive(activeLogFiles.contains(realPath));
        files.add(info);
    }

    private Set<Path> resolveActiveLogFiles()
    {
        Set<Path> activeFiles = new LinkedHashSet<Path>();
        Object loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof LoggerContext))
        {
            return activeFiles;
        }

        LoggerContext context = (LoggerContext) loggerFactory;
        for (Logger logger : context.getLoggerList())
        {
            Iterator<Appender<ILoggingEvent>> iterator = logger.iteratorForAppenders();
            while (iterator.hasNext())
            {
                Appender<ILoggingEvent> appender = iterator.next();
                if (appender instanceof FileAppender)
                {
                    String file = ((FileAppender<ILoggingEvent>) appender).getFile();
                    if (StringUtils.isNotEmpty(file))
                    {
                        Path path = toRealPath(Paths.get(file));
                        if (path != null)
                        {
                            activeFiles.add(path);
                        }
                    }
                }
            }
        }
        return activeFiles;
    }

    private List<Path> resolveLogDirectories()
    {
        LinkedHashSet<Path> directories = new LinkedHashSet<Path>();
        Object loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext)
        {
            LoggerContext context = (LoggerContext) loggerFactory;
            addDirectory(directories, context.getProperty("log.path"));

            for (Logger logger : context.getLoggerList())
            {
                Iterator<Appender<ILoggingEvent>> iterator = logger.iteratorForAppenders();
                while (iterator.hasNext())
                {
                    Appender<ILoggingEvent> appender = iterator.next();
                    if (appender instanceof FileAppender)
                    {
                        String file = ((FileAppender<ILoggingEvent>) appender).getFile();
                        if (StringUtils.isNotEmpty(file))
                        {
                            Path parent = Paths.get(file).toAbsolutePath().normalize().getParent();
                            if (parent != null)
                            {
                                directories.add(parent);
                            }
                        }
                    }
                }
            }
        }

        if (StringUtils.isNotEmpty(RuoYiConfig.getProfile()))
        {
            directories.add(Paths.get(RuoYiConfig.getProfile(), "logs").toAbsolutePath().normalize());
        }
        directories.add(Paths.get(System.getProperty("user.dir"), "logs").toAbsolutePath().normalize());
        return new ArrayList<Path>(directories);
    }

    private void addDirectory(Set<Path> directories, String directory)
    {
        if (StringUtils.isEmpty(directory))
        {
            return;
        }
        directories.add(Paths.get(directory).toAbsolutePath().normalize());
    }

    private Path resolveAllowedLogFile(String fileToken)
    {
        if (StringUtils.isEmpty(fileToken))
        {
            throw new ServiceException("Log file token must not be empty");
        }

        Path requestedFile = decodeToken(fileToken);
        Path realFile = toRealPath(requestedFile);
        if (realFile == null || !Files.isRegularFile(realFile))
        {
            throw new ServiceException("Log file does not exist");
        }
        if (!isLogFile(realFile))
        {
            throw new ServiceException("Only .log files are allowed");
        }

        for (Path directory : resolveLogDirectories())
        {
            Path realDirectory = toRealPath(directory);
            if (realDirectory != null && realFile.startsWith(realDirectory))
            {
                return realFile;
            }
        }
        throw new ServiceException("The requested log file is outside the allowed log directories");
    }

    private Path decodeToken(String fileToken)
    {
        try
        {
            byte[] bytes = Base64.getUrlDecoder().decode(fileToken);
            return Paths.get(new String(bytes, StandardCharsets.UTF_8)).toAbsolutePath().normalize();
        }
        catch (Exception e)
        {
            throw new ServiceException("Invalid log file token");
        }
    }

    private String encodeToken(Path logFile)
    {
        byte[] bytes = logFile.toString().getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private int normalizeLineLimit(Integer lines)
    {
        if (lines == null || lines.intValue() <= 0)
        {
            return DEFAULT_LINES;
        }
        return Math.min(lines.intValue(), MAX_LINES);
    }

    private boolean isLogFile(Path path)
    {
        return Files.isRegularFile(path)
                && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(LOG_SUFFIX);
    }

    private Long safeFileSize(Path logFile)
    {
        try
        {
            return Files.size(logFile);
        }
        catch (IOException e)
        {
            return 0L;
        }
    }

    private Long safeModifiedTime(Path logFile)
    {
        try
        {
            FileTime lastModifiedTime = Files.getLastModifiedTime(logFile);
            return lastModifiedTime.toMillis();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private Path toRealPath(Path path)
    {
        if (path == null)
        {
            return null;
        }
        try
        {
            return path.toRealPath();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private List<String> readLastLines(Path logFile, int lineLimit)
    {
        List<String> lines = new ArrayList<String>();
        if (lineLimit <= 0)
        {
            return lines;
        }

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(logFile.toFile(), "r"))
        {
            long position = randomAccessFile.length();
            if (position <= 0)
            {
                return lines;
            }

            byte[] merged = new byte[0];
            int newlineCount = 0;

            while (position > 0 && newlineCount <= lineLimit)
            {
                long start = Math.max(0L, position - READ_BLOCK_SIZE);
                int length = (int) (position - start);
                byte[] chunk = new byte[length];

                randomAccessFile.seek(start);
                randomAccessFile.readFully(chunk);
                merged = prepend(chunk, merged);

                for (int i = chunk.length - 1; i >= 0; i--)
                {
                    if (chunk[i] == '\n')
                    {
                        newlineCount++;
                    }
                }
                position = start;
            }

            String text = new String(merged, StandardCharsets.UTF_8);
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            String[] segments = normalized.split("\n", -1);

            int end = segments.length;
            while (end > 0 && StringUtils.isEmpty(segments[end - 1]))
            {
                end--;
            }

            int startIndex = Math.max(0, end - lineLimit);
            for (int i = startIndex; i < end; i++)
            {
                lines.add(segments[i]);
            }
            return lines;
        }
        catch (IOException e)
        {
            throw new ServiceException("Failed to read system log content: " + e.getMessage());
        }
    }

    private byte[] prepend(byte[] head, byte[] tail)
    {
        byte[] result = new byte[head.length + tail.length];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }
}
