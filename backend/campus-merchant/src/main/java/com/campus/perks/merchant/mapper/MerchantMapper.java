package com.campus.perks.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.perks.merchant.entity.MerchantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MerchantMapper extends BaseMapper<MerchantEntity> {
  @Select(
      "SELECT"
          + " id,owner_id,school_id,name,category,address,longitude,latitude,qualification_object_key,qualification_file_id,description,business_hours,status,version"
          + " FROM campus_merchant WHERE id=#{id} FOR UPDATE")
  @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
  MerchantEntity lock(long id);
}
