package com.ruoyi.Xidian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulation.python")
public class SimulationPythonProperties
{
    private String apiBaseUrl = "http://127.0.0.1:5000";

    private long apiConnectTimeoutSeconds = 10;

    private long apiReadTimeoutSeconds = 600;

    private long apiTaskTimeoutSeconds = 600;

    private long apiPollIntervalMillis = 500;

    private String localDataRoot = "";

    private String outputBaseDir = "";

    private String defaultOutputDirectory = "csv_output/api_requests";

    public String getApiBaseUrl()
    {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl)
    {
        this.apiBaseUrl = apiBaseUrl;
    }

    public long getApiConnectTimeoutSeconds()
    {
        return apiConnectTimeoutSeconds;
    }

    public void setApiConnectTimeoutSeconds(long apiConnectTimeoutSeconds)
    {
        this.apiConnectTimeoutSeconds = apiConnectTimeoutSeconds;
    }

    public long getApiReadTimeoutSeconds()
    {
        return apiReadTimeoutSeconds;
    }

    public void setApiReadTimeoutSeconds(long apiReadTimeoutSeconds)
    {
        this.apiReadTimeoutSeconds = apiReadTimeoutSeconds;
    }

    public long getApiTaskTimeoutSeconds()
    {
        return apiTaskTimeoutSeconds;
    }

    public void setApiTaskTimeoutSeconds(long apiTaskTimeoutSeconds)
    {
        this.apiTaskTimeoutSeconds = apiTaskTimeoutSeconds;
    }

    public long getApiPollIntervalMillis()
    {
        return apiPollIntervalMillis;
    }

    public void setApiPollIntervalMillis(long apiPollIntervalMillis)
    {
        this.apiPollIntervalMillis = apiPollIntervalMillis;
    }

    public String getLocalDataRoot()
    {
        return localDataRoot;
    }

    public void setLocalDataRoot(String localDataRoot)
    {
        this.localDataRoot = localDataRoot;
    }

    public String getOutputBaseDir()
    {
        return outputBaseDir;
    }

    public void setOutputBaseDir(String outputBaseDir)
    {
        this.outputBaseDir = outputBaseDir;
    }

    public String getDefaultOutputDirectory()
    {
        return defaultOutputDirectory;
    }

    public void setDefaultOutputDirectory(String defaultOutputDirectory)
    {
        this.defaultOutputDirectory = defaultOutputDirectory;
    }
}
