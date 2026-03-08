package com.ruoyi.system.service.impl;

import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.system.domain.AmapResponse;
import com.ruoyi.system.domain.Region;
import com.ruoyi.system.domain.vo.RegionVo;
import com.ruoyi.system.service.ISysRegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 行政区域查询服务实现
 * <p>
 * 优化说明：
 * 1. leaf 判断：高德 API 在 subdistrict=1 时只返回下一级，子级对象的 districts 字段恒为空，
 *    故不能依赖 districts 判叶子。改为根据 level 判断：street 为最底级，一定是叶子。
 * 2. 缓存：行政区域变化少，使用 Redis 缓存查询结果，减少高德 API 调用。
 */
@Service
public class SysRegionServiceImpl implements ISysRegionService {

    private static final int REGION_CACHE_EXPIRE_HOURS = 24;

    @Value("${third-api.amap.key}")
    private String apiKey;

    @Value("${third-api.amap.district-url}")
    private String districtUrl;

    @Autowired
    private RedisCache redisCache;

    @Override
    public List<Region> getDistrict(String keywords, String subdistrict) {
        String cacheKey = CacheConstants.SYS_REGION_KEY + "tree:" + keywords + ":" + subdistrict;
        List<Region> cached = redisCache.getCacheObject(cacheKey);
        if (cached != null) {
            return cached;
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = districtUrl + "?key=" + apiKey
                + "&keywords=" + keywords
                + "&subdistrict=" + subdistrict;

        ResponseEntity<AmapResponse> response = restTemplate.getForEntity(url, AmapResponse.class);
        List<Region> result = convertToTree(response.getBody());
        redisCache.setCacheObject(cacheKey, result, REGION_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        return result;
    }

    public List<Region> convertToTree(AmapResponse response) {
        if (response == null || !"1".equals(response.getStatus()) || response.getDistricts() == null || response.getDistricts().isEmpty()) {
            return new ArrayList<>();
        }
        List<AmapResponse.AmapDistrict> topDistricts = response.getDistricts();
        return topDistricts.stream().map(this::convertToRegion).collect(Collectors.toList());
    }

    private Region convertToRegion(AmapResponse.AmapDistrict amapDistrict) {
        if (amapDistrict == null) {
            return null;
        }
        Region region = new Region();
        region.setCode(amapDistrict.getAdcode());
        region.setName(amapDistrict.getName());
        region.setLevel(amapDistrict.getLevel());
        if (amapDistrict.getDistricts() != null && !amapDistrict.getDistricts().isEmpty()) {
            List<Region> children = amapDistrict.getDistricts().stream().map(this::convertToRegion).collect(Collectors.toList());
            region.setChildren(children);
        } else {
            region.setChildren(new ArrayList<>());
        }
        return region;
    }

    @Override
    public List<RegionVo> getChildren(String parentCode) {
        String cacheKey = CacheConstants.SYS_REGION_KEY + "children:" + parentCode;
        List<RegionVo> cached = redisCache.getCacheObject(cacheKey);
        if (cached != null) {
            return cached;

        }

        RestTemplate restTemplate = new RestTemplate();
        String url = districtUrl + "?key=" + apiKey
                + "&keywords=" + parentCode
                + "&subdistrict=1";

        ResponseEntity<AmapResponse> response = restTemplate.getForEntity(url, AmapResponse.class);
        List<RegionVo> list = new ArrayList<>();
        if (response.getBody() != null
                && response.getBody().getDistricts() != null
                && !response.getBody().getDistricts().isEmpty()) {
            // 高德返回结构：最外层 districts[0] 是当前区域，其下的 districts 才是真正的子级列表
            AmapResponse.AmapDistrict current = response.getBody().getDistricts().get(0);
            if (current.getDistricts() != null) {
                for (AmapResponse.AmapDistrict district : current.getDistricts()) {
                    RegionVo vo = new RegionVo();
                    vo.setCode(district.getAdcode());
                    vo.setName(district.getName());
                    vo.setLeaf(isLeafByLevel(district.getLevel()));
                    list.add(vo);
                }
            }
        }
        redisCache.setCacheObject(cacheKey, list, REGION_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        return list;
    }

    /**
     * 根据高德 level 判断是否为叶子节点。
     * subdistrict=1 时子级 districts 恒为空，不能据此判叶子。
     * 高德层级：country > province > city > district > street，street 为最底级。
     */
    private boolean isLeafByLevel(String level) {
        return level != null && "street".equalsIgnoreCase(level);
    }
}
