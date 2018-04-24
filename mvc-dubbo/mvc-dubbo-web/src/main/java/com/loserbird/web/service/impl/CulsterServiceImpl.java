package com.loserbird.web.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.loserbird.curator.utils.DistributedLock;
import com.loserbird.item.service.ItemsService;
import com.loserbird.order.service.OrdersService;
import com.loserbird.web.service.CulsterService;

@Service("buyService")
public class CulsterServiceImpl implements CulsterService {
	
	final static Logger log = LoggerFactory.getLogger(CulsterServiceImpl.class);
	
	@Autowired
	private ItemsService itemService;

	@Autowired
	private OrdersService ordersService;
	
	@Autowired
	private DistributedLock distributedLock;
	
	@Override
	public void doBuyItem(String itemId) {
		// 减少库存
		itemService.displayReduceCounts(itemId, 1);
		
		// 创建订单
		ordersService.createOrder(itemId);
	}
	
	@Override
	public boolean displayBuy(String itemId) {
		
		//执行订单流程之前先获得分布式锁
		distributedLock.getLock();
		
		int buyCounts = 5;
		
		// 1. 判断库存
		int stockCounts = itemService.getItemCounts(itemId);
		if (stockCounts < buyCounts) {
			log.info("库存剩余{}件，用户需求量{}件，库存不足，订单创建失败...", 
					stockCounts, buyCounts);
			
			distributedLock.realeaseLock();
			return false;
		}
		
		// 2. 创建订单
		boolean isOrderCreated = ordersService.createOrder(itemId);
		
		//模拟业务操作，需要3秒
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
			distributedLock.realeaseLock();
			return false;
		}
		
		// 3. 创建订单成功后，扣除库存
		if (isOrderCreated) {
			log.info("订单创建成功...");
			itemService.displayReduceCounts(itemId, buyCounts);
		} else {
			log.info("订单创建失败...");
			distributedLock.realeaseLock();
			return false;
		}
		
		distributedLock.realeaseLock();
		return true;
	}
	
}

