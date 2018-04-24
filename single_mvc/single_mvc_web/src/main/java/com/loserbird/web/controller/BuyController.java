package com.loserbird.web.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.loserbird.common.utils.MyJSONResult;
import com.loserbird.web.service.BuyService;

/**
 * @Description: 订购商品controller
 */
@Controller
public class BuyController {
	
	@Autowired
	private BuyService buyService;
	
	@RequestMapping("/index")
	public String index() {
		return "index";
	}
	
	@GetMapping("/buy")
	@ResponseBody
	public MyJSONResult doGetlogin(String itemId) {
		
		if (StringUtils.isNotBlank(itemId)) {
			buyService.doBuyItem(itemId);
		} else {
			return MyJSONResult.errorMsg("商品id不能为空");
		}
		
		return MyJSONResult.ok();
	}
	
}
