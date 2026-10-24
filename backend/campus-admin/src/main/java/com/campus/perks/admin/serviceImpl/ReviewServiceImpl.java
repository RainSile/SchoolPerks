package com.campus.perks.admin.serviceImpl;

import com.campus.perks.admin.service.ReviewService;
import com.campus.perks.common.api.*;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.MerchantReviewPort;
import org.springframework.stereotype.Service;

@Service
public class ReviewServiceImpl implements ReviewService {
  private final MerchantReviewPort reviews;

  public ReviewServiceImpl(MerchantReviewPort reviews) {
    this.reviews = reviews;
  }

  public PageResponse<ReviewRecord> query(PageQuery q) {
    return reviews.history(q.getPage(), q.getSize());
  }

  public ReviewRecord get(String id) {
    return reviews.review(id);
  }
}
