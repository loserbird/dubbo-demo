package com.loserbird.curator.utils;

import java.util.concurrent.CountDownLatch;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.corba.se.pept.transport.EventHandler;

import sun.util.logging.resources.logging;

public class DistributedLock {
	private CuratorFramework client = null;
	
	final static Logger log = LoggerFactory.getLogger(DistributedLock.class);
	
	//用于挂起当前请求，并且等待上一个分布式锁释放
	private static CountDownLatch zkLocklatch = new CountDownLatch(1);
	
	
	//分布式锁的总节点名
	private static final String ZK_LOCK_PROJECT = "locks";
	
	//分布式锁节点
	private static final String DISTRIBUTED_LOCK = "distributed_lock";
	
	//构造函数
	public DistributedLock(CuratorFramework client) {
		this.client = client;
	}
	
	public void init() {
		client = client.usingNamespace("zklocks-namespace");
		
		try {
			if(client.checkExists().forPath("/"+ZK_LOCK_PROJECT) == null) {
				client.create()
					.creatingParentsIfNeeded()
					.withMode(CreateMode.PERSISTENT)
					.withACL(Ids.OPEN_ACL_UNSAFE)
					.forPath("/"+ZK_LOCK_PROJECT);
			}
			//针对zk的分布式锁节点，创建相应的watcher事件
			addWatcherToLock("/"+ZK_LOCK_PROJECT);
		}catch (Exception e) {
			log.error("客户端连接zookeeper服务器失败，请重试");
		}
	}

	private void addWatcherToLock(String path) throws Exception {
		final PathChildrenCache childrenCache = new PathChildrenCache(client, path, true);
		childrenCache.start(StartMode.POST_INITIALIZED_EVENT);
		childrenCache.getListenable().addListener(new PathChildrenCacheListener() {
			public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
				if(event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
					String path = event.getData().getPath();
					log.info("上一个会话已经释放锁或该会话已经断开，节点路径为："+path);
					if(path.contains(DISTRIBUTED_LOCK)) {
						log.info("释放计数器，让当前请求来获取分布式锁。。");
						zkLocklatch.countDown();
					}
				}
			}
		
		});
	}
	
	public boolean realeaseLock() {
		try {
			if(client.checkExists().forPath("/"+ZK_LOCK_PROJECT+"/"+DISTRIBUTED_LOCK) != null) {
				client.delete().forPath("/"+ZK_LOCK_PROJECT+"/"+DISTRIBUTED_LOCK);
			}
		}catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		log.info("分布式锁释放完毕");
		return true;
	}
	
	public void getLock() {
		//当获得分布式锁退出循环
		while(true) {
			try {
				client.create()
				.creatingParentsIfNeeded()
				.withMode(CreateMode.EPHEMERAL)
				.withACL(Ids.OPEN_ACL_UNSAFE)
				.forPath("/"+ZK_LOCK_PROJECT+"/"+DISTRIBUTED_LOCK);
				log.info("获得分布式成功");
				return;
			}catch (Exception e) {
				log.info("获得分布式锁失败");
				//如果没有获取到分布式锁，需要重新设置同步资源值
				if(zkLocklatch.getCount() <=0) {
					zkLocklatch = new CountDownLatch(1);
				}
				//阻塞线程
				try {
					zkLocklatch.await();
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
	}
}
