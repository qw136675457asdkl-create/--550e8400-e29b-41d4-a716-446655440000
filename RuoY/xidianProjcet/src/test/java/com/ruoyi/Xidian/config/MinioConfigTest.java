package com.ruoyi.Xidian.config;

import io.minio.MinioClient;
import io.minio.S3Base;
import io.minio.credentials.Credentials;
import io.minio.credentials.Provider;
import okhttp3.HttpUrl;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Field;

public class MinioConfigTest {

    @Test
    public void shouldCreateMinioClientWithConfiguredEndpointAndCredentials() throws Exception {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://127.0.0.1:9000");
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setBucket("test-bucket");

        MinioConfig minioConfig = new MinioConfig();
        setField(minioConfig, "minioProperties", properties);

        MinioClient minioClient = minioConfig.minioClient();

        Assert.assertNotNull(minioClient);

        Object asyncClient = readField(minioClient, "asyncClient");
        Assert.assertNotNull(asyncClient);

        HttpUrl baseUrl = (HttpUrl) readField(asyncClient, "baseUrl");
        Assert.assertEquals("http://127.0.0.1:9000/", baseUrl.toString());

        Provider provider = (Provider) readField(asyncClient, "provider");
        Credentials credentials = provider.fetch();
        Assert.assertEquals("minioadmin", credentials.accessKey());
        Assert.assertEquals("minioadmin", credentials.secretKey());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Class<?> currentClass = target.getClass();
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ex) {
                currentClass = currentClass.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field not found: " + fieldName + " in " + S3Base.class.getName());
    }
}
