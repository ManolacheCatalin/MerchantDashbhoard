package com.merchant.awinMerchant.merchant.dto;

import java.util.List;
import com.merchant.awinMerchant.merchant.entity.Merchant;

/** Array shape kept for the frontend selector; holds only the authenticated merchant. */
public record MerchantListResponse(List<MerchantItem> merchants) {

	public record MerchantItem(long id, String name) {
	}

	/** Exposes only id and name; the reporting currency stays internal. */
	public static MerchantListResponse of(Merchant merchant) {
		return new MerchantListResponse(List.of(new MerchantItem(merchant.id(), merchant.name())));
	}

}
