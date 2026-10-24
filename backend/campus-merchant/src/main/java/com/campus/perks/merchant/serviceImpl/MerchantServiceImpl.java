package com.campus.perks.merchant.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.perks.common.api.PageResponse;
import com.campus.perks.common.enums.ReviewStatus;
import com.campus.perks.common.exception.FeatureNotImplementedException;
import com.campus.perks.merchant.dto.MerchantQuery;
import com.campus.perks.merchant.entity.MerchantEntity;
import com.campus.perks.merchant.mapper.MerchantMapper;
import com.campus.perks.merchant.service.MerchantService;
import com.campus.perks.merchant.vo.MerchantSummary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MerchantServiceImpl implements MerchantService {
  @Override
  public com.campus.perks.merchant.vo.MerchantDetail detail(String id) {
    MerchantMapper repository = mapper.getIfAvailable();
    if (repository == null) throw new FeatureNotImplementedException("数据库尚未启用");
    MerchantEntity m = null;
    if (id.matches("[1-9][0-9]{0,17}")) m = repository.selectById(Long.parseLong(id));
    if (m == null || m.getStatus() != ReviewStatus.APPROVED)
      throw new com.campus.perks.common.exception.ApplicationException(
          org.springframework.http.HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "门店不存在");
    return new com.campus.perks.merchant.vo.MerchantDetail(
        m.getId().toString(),
        m.getName(),
        m.getSchoolId().toString(),
        m.getCategory(),
        m.getAddress(),
        m.getLongitude(),
        m.getLatitude(),
        m.getDescription(),
        m.getBusinessHours());
  }

  private final ObjectProvider<MerchantMapper> mapper;

  public MerchantServiceImpl(ObjectProvider<MerchantMapper> mapper) {
    this.mapper = mapper;
  }

  @Override
  public PageResponse<MerchantSummary> list(MerchantQuery query) {
    MerchantMapper repository = mapper.getIfAvailable();
    if (repository == null) throw new FeatureNotImplementedException("数据库尚未启用");
    var filter =
        new LambdaQueryWrapper<MerchantEntity>()
            .eq(MerchantEntity::getStatus, ReviewStatus.APPROVED)
            .like(
                StringUtils.hasText(query.getKeyword()),
                MerchantEntity::getName,
                query.getKeyword())
            .orderByDesc(MerchantEntity::getId);
    IPage<MerchantEntity> page =
        repository.selectPage(new Page<>(query.getPage(), query.getSize()), filter);
    var items =
        page.getRecords().stream()
            .map(
                m ->
                    new MerchantSummary(
                        m.getId().toString(), m.getName(), m.getCategory(), m.getAddress()))
            .toList();
    return new PageResponse<>(items, page.getTotal(), page.getCurrent(), page.getSize());
  }
}
