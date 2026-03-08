package com.ruoyi.system.service;

import com.ruoyi.system.domain.Region;
import com.ruoyi.system.domain.vo.RegionVo;

import java.util.List;

public interface ISysRegionService {
    public List<Region> getDistrict(String keywords, String subdistrict);

    List<RegionVo> getChildren(String parentCode);
}
