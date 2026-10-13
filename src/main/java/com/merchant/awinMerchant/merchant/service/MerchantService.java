package com.merchant.awinMerchant.merchant.service;

import org.springframework.stereotype.Service;
import com.merchant.awinMerchant.merchant.dao.MerchantDao;
import com.merchant.awinMerchant.merchant.entity.Merchant;

/** Merchant lookup shared by the merchant, dashboard and to-do features. */
@Service
public class MerchantService {

	private final MerchantDao merchantDao;

	public MerchantService(MerchantDao merchantDao) {
		this.merchantDao = merchantDao;
	}

	/** Loads the merchant or throws {@link MerchantNotFoundException} (rendered as 404). */
	public Merchant getMerchant(long merchantId) {
		return merchantDao.findById(merchantId).orElseThrow(() -> new MerchantNotFoundException(merchantId));
	}

}
