package com.ruoyi.framework.web.domain;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.ruoyi.common.utils.Arith;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.web.domain.server.Cpu;
import com.ruoyi.framework.web.domain.server.Gpu;
import com.ruoyi.framework.web.domain.server.Jvm;
import com.ruoyi.framework.web.domain.server.Mem;
import com.ruoyi.framework.web.domain.server.Net;
import com.ruoyi.framework.web.domain.server.Sys;
import com.ruoyi.framework.web.domain.server.SysFile;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.TickType;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.Util;

/**
 * 服务器相关信息
 *
 * @author ruoyi
 */
public class Server
{
    private static final int OSHI_WAIT_SECOND = 1000;

    /**
     * CPU信息
     */
    private Cpu cpu = new Cpu();

    /**
     * 内存信息
     */
    private Mem mem = new Mem();

    /**
     * JVM信息
     */
    private Jvm jvm = new Jvm();

    /**
     * GPU信息
     */
    private Gpu gpu = new Gpu();

    /**
     * 网络吞吐量信息
     */
    private Net net = new Net();

    /**
     * 系统信息
     */
    private Sys sys = new Sys();

    /**
     * 磁盘信息
     */
    private List<SysFile> sysFiles = new LinkedList<SysFile>();

    public Cpu getCpu()
    {
        return cpu;
    }

    public void setCpu(Cpu cpu)
    {
        this.cpu = cpu;
    }

    public Mem getMem()
    {
        return mem;
    }

    public void setMem(Mem mem)
    {
        this.mem = mem;
    }

    public Jvm getJvm()
    {
        return jvm;
    }

    public void setJvm(Jvm jvm)
    {
        this.jvm = jvm;
    }

    public Gpu getGpu()
    {
        return gpu;
    }

    public void setGpu(Gpu gpu)
    {
        this.gpu = gpu;
    }

    public Net getNet()
    {
        return net;
    }

    public void setNet(Net net)
    {
        this.net = net;
    }

    public Sys getSys()
    {
        return sys;
    }

    public void setSys(Sys sys)
    {
        this.sys = sys;
    }

    public List<SysFile> getSysFiles()
    {
        return sysFiles;
    }

    public void setSysFiles(List<SysFile> sysFiles)
    {
        this.sysFiles = sysFiles;
    }

    public void copyTo() throws Exception
    {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        CentralProcessor processor = hal.getProcessor();
        List<NetworkIF> networkIFs = hal.getNetworkIFs();
        CompletableFuture<Double> gpuUsageFuture = CompletableFuture.supplyAsync(this::queryGpuUsage);
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        Map<Integer, NetworkSnapshot> networkSnapshots = snapshotNetworkInfo(networkIFs);

        Util.sleep(OSHI_WAIT_SECOND);

        setCpuInfo(processor, prevTicks);
        setMemInfo(hal.getMemory());
        setGpuInfo(hal.getGraphicsCards(), getGpuUsage(gpuUsageFuture));
        setNetInfo(networkIFs, networkSnapshots);
        setSysInfo();
        setJvmInfo();
        setSysFiles(si.getOperatingSystem());
    }

    /**
     * 设置CPU信息
     */
    private void setCpuInfo(CentralProcessor processor, long[] prevTicks)
    {
        long[] ticks = processor.getSystemCpuLoadTicks();
        long nice = ticks[TickType.NICE.getIndex()] - prevTicks[TickType.NICE.getIndex()];
        long irq = ticks[TickType.IRQ.getIndex()] - prevTicks[TickType.IRQ.getIndex()];
        long softirq = ticks[TickType.SOFTIRQ.getIndex()] - prevTicks[TickType.SOFTIRQ.getIndex()];
        long steal = ticks[TickType.STEAL.getIndex()] - prevTicks[TickType.STEAL.getIndex()];
        long cSys = ticks[TickType.SYSTEM.getIndex()] - prevTicks[TickType.SYSTEM.getIndex()];
        long user = ticks[TickType.USER.getIndex()] - prevTicks[TickType.USER.getIndex()];
        long iowait = ticks[TickType.IOWAIT.getIndex()] - prevTicks[TickType.IOWAIT.getIndex()];
        long idle = ticks[TickType.IDLE.getIndex()] - prevTicks[TickType.IDLE.getIndex()];
        long totalCpu = user + nice + cSys + idle + iowait + irq + softirq + steal;
        cpu.setCpuNum(processor.getLogicalProcessorCount());
        cpu.setTotal(totalCpu);
        cpu.setSys(cSys);
        cpu.setUsed(user);
        cpu.setWait(iowait);
        cpu.setFree(idle);
    }

    /**
     * 设置GPU信息
     */
    private void setGpuInfo(List<GraphicsCard> graphicsCards, double usage)
    {
        if (graphicsCards == null || graphicsCards.isEmpty())
        {
            gpu.setName("N/A");
            gpu.setVendor("N/A");
            gpu.setUsage(0D);
            return;
        }
        GraphicsCard primaryCard = graphicsCards.get(0);
        gpu.setName(primaryCard.getName());
        gpu.setVendor(primaryCard.getVendor());
        gpu.setUsage(Math.min(Math.max(usage, 0D), 100D));
    }

    /**
     * 设置内存信息
     */
    private void setMemInfo(GlobalMemory memory)
    {
        mem.setTotal(memory.getTotal());
        mem.setUsed(memory.getTotal() - memory.getAvailable());
        mem.setFree(memory.getAvailable());
    }

    /**
     * 设置网络吞吐量信息
     */
    private void setNetInfo(List<NetworkIF> networkIFs, Map<Integer, NetworkSnapshot> networkSnapshots)
    {
        double recvRate = 0D;
        double sentRate = 0D;
        double busiestRate = -1D;
        String busiestName = "N/A";

        for (NetworkIF networkIF : networkIFs)
        {
            if (!networkIF.updateAttributes() || !isPhysicalNetwork(networkIF))
            {
                continue;
            }

            NetworkSnapshot snapshot = networkSnapshots.get(networkIF.getIndex());
            if (snapshot == null)
            {
                continue;
            }

            long elapsedMillis = Math.max(1L, networkIF.getTimeStamp() - snapshot.getTimeStamp());
            double currentRecvRate = Math.max(0L, networkIF.getBytesRecv() - snapshot.getBytesRecv()) * 1000D / elapsedMillis;
            double currentSentRate = Math.max(0L, networkIF.getBytesSent() - snapshot.getBytesSent()) * 1000D / elapsedMillis;
            double currentTotalRate = currentRecvRate + currentSentRate;

            recvRate += currentRecvRate;
            sentRate += currentSentRate;
            if (currentTotalRate > busiestRate)
            {
                busiestRate = currentTotalRate;
                busiestName = networkIF.getDisplayName();
            }
        }

        net.setName(busiestName);
        net.setRecvRate(recvRate);
        net.setSentRate(sentRate);
        net.setTotalRate(recvRate + sentRate);
    }

    /**
     * 设置服务器信息
     */
    private void setSysInfo()
    {
        Properties props = System.getProperties();
        sys.setComputerName(IpUtils.getHostName());
        sys.setComputerIp(IpUtils.getHostIp());
        sys.setOsName(props.getProperty("os.name"));
        sys.setOsArch(props.getProperty("os.arch"));
        sys.setUserDir(props.getProperty("user.dir"));
    }

    /**
     * 设置Java虚拟机
     */
    private void setJvmInfo() throws UnknownHostException
    {
        Properties props = System.getProperties();
        jvm.setTotal(Runtime.getRuntime().totalMemory());
        jvm.setMax(Runtime.getRuntime().maxMemory());
        jvm.setFree(Runtime.getRuntime().freeMemory());
        jvm.setVersion(props.getProperty("java.version"));
        jvm.setHome(props.getProperty("java.home"));
    }

    /**
     * 设置磁盘信息
     */
    private void setSysFiles(OperatingSystem os)
    {
        sysFiles = new LinkedList<SysFile>();
        FileSystem fileSystem = os.getFileSystem();
        List<OSFileStore> fsArray = fileSystem.getFileStores();
        for (OSFileStore fs : fsArray)
        {
            long free = fs.getUsableSpace();
            long total = fs.getTotalSpace();
            long used = total - free;
            SysFile sysFile = new SysFile();
            sysFile.setDirName(fs.getMount());
            sysFile.setSysTypeName(fs.getType());
            sysFile.setTypeName(fs.getName());
            sysFile.setTotal(convertFileSize(total));
            sysFile.setFree(convertFileSize(free));
            sysFile.setUsed(convertFileSize(used));
            sysFile.setUsage(Arith.mul(Arith.div(used, total, 4), 100));
            sysFiles.add(sysFile);
        }
    }

    /**
     * 字节转换
     *
     * @param size 字节大小
     * @return 转换后值
     */
    public String convertFileSize(long size)
    {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;
        if (size >= gb)
        {
            return String.format("%.1f GB", (float) size / gb);
        }
        else if (size >= mb)
        {
            float f = (float) size / mb;
            return String.format(f > 100 ? "%.0f MB" : "%.1f MB", f);
        }
        else if (size >= kb)
        {
            float f = (float) size / kb;
            return String.format(f > 100 ? "%.0f KB" : "%.1f KB", f);
        }
        else
        {
            return String.format("%d B", size);
        }
    }

    private Map<Integer, NetworkSnapshot> snapshotNetworkInfo(List<NetworkIF> networkIFs)
    {
        Map<Integer, NetworkSnapshot> snapshots = new HashMap<Integer, NetworkSnapshot>();
        for (NetworkIF networkIF : networkIFs)
        {
            if (!networkIF.updateAttributes() || !isPhysicalNetwork(networkIF))
            {
                continue;
            }
            snapshots.put(networkIF.getIndex(), new NetworkSnapshot(networkIF.getBytesRecv(), networkIF.getBytesSent(),
                    networkIF.getTimeStamp()));
        }
        return snapshots;
    }

    private boolean isPhysicalNetwork(NetworkIF networkIF)
    {
        NetworkInterface networkInterface = networkIF.queryNetworkInterface();
        if (networkInterface == null)
        {
            return false;
        }
        try
        {
            return networkInterface.isUp() && !networkInterface.isLoopback() && !networkInterface.isVirtual();
        }
        catch (SocketException e)
        {
            return false;
        }
    }

    private double getGpuUsage(CompletableFuture<Double> gpuUsageFuture)
    {
        try
        {
            return gpuUsageFuture.get(2500, TimeUnit.MILLISECONDS);
        }
        catch (Exception e)
        {
            gpuUsageFuture.cancel(true);
            return 0D;
        }
    }

    private double queryGpuUsage()
    {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("win"))
        {
            return 0D;
        }

        Process process = null;
        try
        {
            process = new ProcessBuilder("typeperf", "\\GPU Engine(*)\\Utilization Percentage", "-sc", "1")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2500, TimeUnit.MILLISECONDS))
            {
                process.destroyForcibly();
                return 0D;
            }

            double maxUsage = 0D;
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null)
            {
                maxUsage = Math.max(maxUsage, extractMaxQuotedNumber(line));
            }
            return Math.min(maxUsage, 100D);
        }
        catch (Exception e)
        {
            return 0D;
        }
        finally
        {
            if (process != null)
            {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private double extractMaxQuotedNumber(String line)
    {
        double maxValue = 0D;
        if (line == null || line.indexOf('"') < 0)
        {
            return maxValue;
        }

        int start = -1;
        for (int i = 0; i < line.length(); i++)
        {
            if (line.charAt(i) == '"')
            {
                if (start < 0)
                {
                    start = i + 1;
                }
                else
                {
                    String value = line.substring(start, i);
                    maxValue = Math.max(maxValue, parseDouble(value));
                    start = -1;
                }
            }
        }
        return maxValue;
    }

    private double parseDouble(String value)
    {
        if (value == null || value.indexOf('/') >= 0 || value.indexOf(':') >= 0)
        {
            return 0D;
        }
        try
        {
            return Double.parseDouble(value.trim());
        }
        catch (NumberFormatException e)
        {
            return 0D;
        }
    }

    private void closeQuietly(Closeable closeable)
    {
        if (closeable == null)
        {
            return;
        }
        try
        {
            closeable.close();
        }
        catch (IOException e)
        {
            // ignore
        }
    }

    private static class NetworkSnapshot
    {
        private final long bytesRecv;
        private final long bytesSent;
        private final long timeStamp;

        NetworkSnapshot(long bytesRecv, long bytesSent, long timeStamp)
        {
            this.bytesRecv = bytesRecv;
            this.bytesSent = bytesSent;
            this.timeStamp = timeStamp;
        }

        long getBytesRecv()
        {
            return bytesRecv;
        }

        long getBytesSent()
        {
            return bytesSent;
        }

        long getTimeStamp()
        {
            return timeStamp;
        }
    }
}
