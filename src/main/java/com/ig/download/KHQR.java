package com.ig.download;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import kh.gov.nbc.bakong_khqr.BakongKHQR;
import kh.gov.nbc.bakong_khqr.model.IndividualInfo;
import kh.gov.nbc.bakong_khqr.model.KHQRCurrency;
import kh.gov.nbc.bakong_khqr.model.KHQRData;
import kh.gov.nbc.bakong_khqr.model.KHQRResponse;

public class KHQR {

	public static void main(String[] args) {
		IndividualInfo individualInfo = new IndividualInfo();
		individualInfo.setBakongAccountId("khqr@ppcb");
		individualInfo.setAccountInformation("0116021521338");
		individualInfo.setAcquiringBank("Dev Bank");
		individualInfo.setCurrency(KHQRCurrency.USD);
		individualInfo.setAmount(100.0);
		individualInfo.setMerchantName("John Smith");
		individualInfo.setMerchantCity("PHNOM PENH");
		individualInfo.setBillNumber("#12345");
		individualInfo.setMobileNumber("85512233455");
		individualInfo.setStoreLabel("Coffee Shop");
		individualInfo.setTerminalLabel("Cashier_1");
		long timestamp = Instant.now().plus(10, ChronoUnit.MINUTES).toEpochMilli();
		individualInfo.setExpirationTimestamp(timestamp);
		KHQRResponse<KHQRData> response = BakongKHQR.generateIndividual(individualInfo);
		if (response.getKHQRStatus().getCode() == 0) {
			System.out.println("data: " + response.getData().getQr());
		}
		
		// KHR Support UPI
		// 00020101021215311234567812345678ABCDEFGHIJKLMNO29460015john_smith@devb0111855122334550208Dev Bank52045999530311654031005802KH5910John Smith6010PHNOM PENH62670106#123450211855122334550311Coffee Shop0709Cashier_10810Buy coffee64280002km0108ចន ស្មីន0206ភ្នំពញ9934001317715760419770113177157664196163047A4C
		// USD 
		// 00020101021229460015john_smith@devb0111855122334550208Dev Bank52045999530384054031005802KH5910John Smith6010PHNOM PENH62530106#123450211855122334550311Coffee Shop0709Cashier_19934001317715761247620113177157672474863047871

	}

}
