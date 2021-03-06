/**
 * Copyright 2016 vip.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * </p>
 **/

package com.vip.saturn.it.impl;

import com.vip.saturn.it.base.AbstractSaturnIT;
import com.vip.saturn.it.base.FinishCheck;
import com.vip.saturn.it.job.SimpleJavaJob;
import com.vip.saturn.job.console.domain.JobConfig;
import com.vip.saturn.job.console.domain.JobType;
import com.vip.saturn.job.executor.Main;
import com.vip.saturn.job.internal.sharding.ShardingNode;
import com.vip.saturn.job.internal.storage.JobNodePath;
import com.vip.saturn.job.sharding.node.SaturnExecutorsNode;
import com.vip.saturn.job.sharding.service.NamespaceShardingContentService;
import com.vip.saturn.job.sharding.task.AbstractAsyncShardingTask;
import com.vip.saturn.job.utils.ItemUtils;
import com.vip.saturn.job.utils.SystemEnvProperties;
import org.apache.curator.framework.CuratorFramework;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ShardingIT extends AbstractSaturnIT {

	private NamespaceShardingContentService namespaceShardingContentService = new NamespaceShardingContentService(
			(CuratorFramework) regCenter.getRawClient());

	@BeforeClass
	public static void setUp() throws Exception {
		startSaturnConsoleList(1);
	}

	@AfterClass
	public static void tearDown() throws Exception {
		stopExecutorListGracefully();
		stopSaturnConsoleList();
	}

	@Test
	public void test_A_JAVA() throws Exception {
		int shardCount = 3;
		final String jobName = "test_A_JAVA";

		for (int i = 0; i < shardCount; i++) {
			String key = jobName + "_" + i;
			SimpleJavaJob.statusMap.put(key, 0);
		}

		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(shardCount);
		jobConfig.setShardingItemParameters("0=0,1=1,2=2");

		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);
		Thread.sleep(1000);

		Main executor1 = startOneNewExecutorList();// ?????????1???executor
		runAtOnce(jobName);
		Thread.sleep(1000);

		assertThat(regCenter.getDirectly(SaturnExecutorsNode.SHARDING_COUNT_PATH)).isEqualTo("4");

		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).contains(0, 1, 2);

		Main executor2 = startOneNewExecutorList();// ?????????2???executor
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isNotEmpty();
		System.out.println(items);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isNotEmpty();

		Main executor3 = startOneNewExecutorList();// ?????????3???executor
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor3.getExecutorName()))));
		System.out.println(items);
		assertThat(items).hasSize(1);

		stopExecutorGracefully(0); // ??????1???executor

		Thread.sleep(1000);
		assertThat(regCenter.getDirectly(SaturnExecutorsNode.SHARDING_COUNT_PATH)).isEqualTo("10");

		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isEmpty();

		stopExecutorGracefully(1); // ??????2???executor
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isEmpty();

		// ?????????????????????3???executor
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor3.getExecutorName()))));
		assertThat(items).contains(0, 1, 2);

		disableJob(jobName);
		Thread.sleep(1000);
		removeJob(jobName);
		stopExecutorListGracefully();
	}

	@Test
	public void test_B_JobAverage() throws Exception {
		if (!AbstractAsyncShardingTask.ENABLE_JOB_BASED_SHARDING) {
			return;
		}
		// ?????????1???executor
		Main executor1 = startOneNewExecutorList();
		// ?????????1?????????
		final String job1 = "test_B_JobAverage_job1";
		{
			final JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(job1);
			jobConfig.setCron("0/1 * * * * ?");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(2);
			jobConfig.setShardingItemParameters("0=0,1=1");
			addJob(jobConfig);
			Thread.sleep(1000);
		}
		// ?????????2?????????
		final String job2 = "test_B_JobAverage_job2";
		{
			final JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(job2);
			jobConfig.setCron("0/1 * * * * ?");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(2);
			jobConfig.setShardingItemParameters("0=0,1=1");
			addJob(jobConfig);
			Thread.sleep(1000);
		}
		// ??????job1
		enableJob(job1);
		Thread.sleep(2000);
		// ??????
		Map<String, List<Integer>> shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(2).contains(0, 1);
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).isEmpty();

		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();
		// ?????????2???executor
		Main executor2 = startOneNewExecutorList();
		// ??????????????????????????????
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(0);
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(1).contains(1);
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).isEmpty();
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		// ??????job2
		enableJob(job2);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(0);
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(1).contains(1);
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(0);
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(1).contains(1);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		// ??????job1
		disableJob(job1);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).isEmpty();
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(0);
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(1).contains(1);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0); // ??????????????????????????????notify?????????????????????????????????????????????
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		// ??????executor1
		stopExecutorGracefully(0);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).isNull();
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).isNull();
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(2).contains(0, 1);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);

		// ??????
		removeJob(job1);
		Thread.sleep(1000);
		disableJob(job2);
		Thread.sleep(1000);
		removeJob(job2);
		stopExecutorListGracefully();
	}

	@Test
	public void test_B_JobAverageWithPreferListAndUseDispreferList() throws Exception {
		testJobAverageWithPreferList("test_B_JobAverageWithPreferListAndUseDispreferList", true);
	}

	@Test
	public void test_B_JobAverageWithPreferList() throws Exception {
		testJobAverageWithPreferList("test_B_JobAverageWithPreferList", false);
	}

	private void testJobAverageWithPreferList(String jobPrefix, boolean useDispreferList) throws Exception {
		if (!AbstractAsyncShardingTask.ENABLE_JOB_BASED_SHARDING) {
			return;
		}
		// ?????????1???executor
		Main executor1 = startOneNewExecutorList();
		// ?????????1??????????????????preferList
		final String job1 = jobPrefix + "_job1";
		{
			final JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(job1);
			jobConfig.setCron("0/1 * * * * ?");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(2);
			jobConfig.setShardingItemParameters("0=0,1=1");
			jobConfig.setPreferList(executor1.getExecutorName());
			jobConfig.setUseDispreferList(useDispreferList);
			addJob(jobConfig);
			Thread.sleep(1000);
		}
		// ?????????2?????????
		final String job2 = jobPrefix + "_job2";
		{
			final JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(job2);
			jobConfig.setCron("0/1 * * * * ?");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(2);
			jobConfig.setShardingItemParameters("0=0,1=1");
			addJob(jobConfig);
			Thread.sleep(1000);
		}
		// ??????job1
		enableJob(job1);
		Thread.sleep(2000);
		// ??????
		Map<String, List<Integer>> shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(2).contains(0, 1);
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).isEmpty();

		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();
		// ?????????2???executor
		Main executor2 = startOneNewExecutorList();
		// ??????????????????????????????
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(2)
				.contains(0, 1); // ???????????????preferList???executor1
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).isEmpty();
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		// ??????job2
		enableJob(job2);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(2).contains(0, 1);
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(1);
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(1).contains(0);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0); // ?????????????????????????????????????????????executor2???????????????0
		// ??????job1
		disableJob(job1);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).isEmpty();
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(1);
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(1).contains(0);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1); // ??????????????????????????????notify?????????????????????????????????????????????
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0);
		// ??????executor1
		stopExecutorGracefully(0);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).isNull();
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).isNull();
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(2).contains(0, 1);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		// ??????job1
		enableJob(job1);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).isNull();
		if (useDispreferList) { // ??????useDispreferList???true??????????????????executor2??????
			assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(2).contains(0, 1);
		} else {
			assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		}
		shardingItems = namespaceShardingContentService.getShardingItems(job2);
		assertThat(shardingItems.get(executor1.getExecutorName())).isNull();
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(2).contains(0, 1);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		if (useDispreferList) {
			assertThat(items).hasSize(2).contains(0, 1);
		} else {
			assertThat(items).isEmpty();
		}
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);

		// ??????
		disableJob(job1);
		Thread.sleep(1000);
		removeJob(job1);
		Thread.sleep(1000);
		disableJob(job2);
		Thread.sleep(1000);
		removeJob(job2);
		stopExecutorListGracefully();
	}

	@Test
	public void test_C_JobAverageWithLocalMode() throws Exception {
		if (!AbstractAsyncShardingTask.ENABLE_JOB_BASED_SHARDING) {
			return;
		}
		// ?????????1???executor
		Main executor1 = startOneNewExecutorList();
		Main executor2 = startOneNewExecutorList();
		// ?????????1??????????????????preferList
		final String job1 = "test_C_JobAverageWithLocalMode_job1";
		{
			final JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(job1);
			jobConfig.setCron("0/1 * * * * ?");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(2);
			jobConfig.setShardingItemParameters("0=0,1=1");
			jobConfig.setPreferList(executor1.getExecutorName()); // ??????preferList
			addJob(jobConfig);
			Thread.sleep(1000);
		}
		// ????????????
		enableJob(job1);
		Thread.sleep(2000);
		// ??????
		Map<String, List<Integer>> shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(2).contains(0, 1);
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();

		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		// ?????????2??????????????????????????????
		final String job2 = "test_C_JobAverageWithLocalMode_job2";
		{
			final JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(job2);
			jobConfig.setCron("0/1 * * * * ?");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(1); // ???????????????1
			jobConfig.setShardingItemParameters("*=0");
			jobConfig.setLocalMode(true); // ??????????????????
			addJob(jobConfig);
			Thread.sleep(1000);
		}
		// ??????job2
		enableJob(job2);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(2).contains(0, 1);
		assertThat(shardingItems.get(executor2.getExecutorName())).isEmpty();
		shardingItems = namespaceShardingContentService.getShardingItems(job2); // ??????job2??????????????????????????????????????????????????????
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(1);
		assertThat(shardingItems.get(executor2.getExecutorName())).hasSize(1)
				.contains(0); // ??????????????????????????????????????????????????????executor2?????????0

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).isEmpty();
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).hasSize(1).contains(0);
		// ??????executor2
		stopExecutorGracefully(1);
		Thread.sleep(2000);
		// ??????
		shardingItems = namespaceShardingContentService.getShardingItems(job1);
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(2).contains(0, 1);
		assertThat(shardingItems.get(executor2.getExecutorName())).isNull();
		shardingItems = namespaceShardingContentService.getShardingItems(job2); // ??????job2??????????????????????????????????????????????????????
		assertThat(shardingItems.get(executor1.getExecutorName())).hasSize(1).contains(1); // ???????????????0??????
		assertThat(shardingItems.get(executor2.getExecutorName())).isNull();

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job1, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(2).contains(0, 1);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(job2, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).hasSize(1).contains(1);

		// ??????
		disableJob(job1);
		Thread.sleep(1000);
		removeJob(job1);
		Thread.sleep(1000);
		disableJob(job2);
		Thread.sleep(1000);
		removeJob(job2);
		stopExecutorListGracefully();
	}

	@Test
	public void test_D_PreferList() throws Exception {
		Main executor1 = startOneNewExecutorList();// ?????????1???executor

		int shardCount = 3;
		final String jobName = "test_D_PreferList";

		for (int i = 0; i < shardCount; i++) {
			String key = jobName + "_" + i;
			SimpleJavaJob.statusMap.put(key, 0);
		}

		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(shardCount);
		jobConfig.setShardingItemParameters("0=0,1=1,2=2");
		jobConfig.setPreferList(executor1.getExecutorName());
		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);
		Thread.sleep(1000);

		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).contains(0, 1, 2);

		Main executor2 = startOneNewExecutorList();// ?????????2???executor
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).contains(0, 1, 2);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isEmpty();

		stopExecutorGracefully(0); // ??????1???executor
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isEmpty();

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		System.out.println(items);
		assertThat(items).contains(0, 1, 2);

		// ?????????????????????executor
		startExecutor(0);
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);

		// ?????????????????????executor1???
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		log.info("sharding at executor1 {}: ", items);
		assertThat(items).contains(0, 1, 2);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		log.info("sharding at executor2 {}: ", items);
		assertThat(items).isEmpty();

		disableJob(jobName);
		Thread.sleep(1000);
		removeJob(jobName);
		stopExecutorListGracefully();
	}

	@Test
	public void test_E_PreferListOnly() throws Exception {
		Main executor1 = startOneNewExecutorList();// ?????????1???executor

		int shardCount = 3;
		final String jobName = "test_E_PreferListOnly";

		for (int i = 0; i < shardCount; i++) {
			String key = jobName + "_" + i;
			SimpleJavaJob.statusMap.put(key, 0);
		}

		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(shardCount);
		jobConfig.setShardingItemParameters("0=0,1=1,2=2");
		jobConfig.setPreferList(executor1.getExecutorName());
		jobConfig.setUseDispreferList(false);

		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);
		Thread.sleep(1000);

		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).contains(0, 1, 2);

		Main executor2 = startOneNewExecutorList();// ?????????2???executor
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).contains(0, 1, 2);

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isEmpty();

		stopExecutorGracefully(0); // ??????1???executor
		Thread.sleep(1000);
		runAtOnce(jobName);
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {

			@Override
			public boolean isOk() {
				if (isNeedSharding(jobName)) {
					return false;
				}
				return true;
			}

		}, 10);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isEmpty();

		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		System.out.println(items);
		assertThat(items).isEmpty();

		disableJob(jobName);
		Thread.sleep(1000);
		removeJob(jobName);
		stopExecutorListGracefully();
	}

	/**
	 * ??????????????????????????????preferList?????????useDispreferList???false????????????preferList????????????????????????
	 */
	@Test
	public void test_F_LocalModeWithPreferList() throws Exception {
		Main executor1 = startOneNewExecutorList();
		Main executor2 = startOneNewExecutorList();

		int shardCount = 2;
		final String jobName = "test_F_LocalModeWithPreferList";

		for (int i = 0; i < shardCount; i++) {
			String key = jobName + "_" + i;
			SimpleJavaJob.statusMap.put(key, 0);
		}

		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(shardCount);
		jobConfig.setShardingItemParameters("*=0");
		jobConfig.setLocalMode(true);
		jobConfig.setPreferList(executor2.getExecutorName()); // ??????preferList???executor2
		jobConfig.setUseDispreferList(false); // ??????useDispreferList???false

		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);
		runAtOnce(jobName);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);
		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).contains(0);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();
		// wait running completed
		Thread.sleep(1000);
		// executor2??????
		stopExecutorGracefully(1);
		Thread.sleep(1000);
		// ??????sharding????????????
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);
		runAtOnce(jobName);
		// ??????????????????
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);
		// executor1????????????????????????
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();

		disableJob(jobName);
		Thread.sleep(1000);
		removeJob(jobName);
		stopExecutorListGracefully();
	}

	/**
	 * ??????????????????????????????preferList???????????????useDispreferList???true?????????????????????preferList??????????????????????????????useDispreferList????????????????????????????????????
	 */
	@Test
	public void test_F_LocalModeWithPreferListAndUseDispreferList() throws Exception {
		Main executor1 = startOneNewExecutorList();
		Main executor2 = startOneNewExecutorList();

		int shardCount = 2;
		final String jobName = "test_F_LocalModeWithPreferListAndUseDispreferList";

		for (int i = 0; i < shardCount; i++) {
			String key = jobName + "_" + i;
			SimpleJavaJob.statusMap.put(key, 0);
		}

		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(shardCount);
		jobConfig.setShardingItemParameters("*=0");
		jobConfig.setLocalMode(true);
		jobConfig.setPreferList(executor2.getExecutorName()); // ??????preferList???executor2
		jobConfig.setUseDispreferList(true); // ??????useDispreferList???true

		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);
		runAtOnce(jobName);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);
		// executor2?????????0?????????executor1??????????????????
		List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
		assertThat(items).contains(0);
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();
		// wait running completed
		Thread.sleep(1000);
		// executor2??????
		stopExecutorGracefully(1);
		Thread.sleep(1000L);
		// ??????sharding????????????
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);
		runAtOnce(jobName);
		// ??????????????????
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);
		// executor1????????????????????????
		items = ItemUtils.toItemList(regCenter.getDirectly(
				JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
		assertThat(items).isEmpty();

		disableJob(jobName);
		Thread.sleep(1000);
		removeJob(jobName);
		stopExecutorListGracefully();
	}

	/**
	 * preferList??????????????????????????????useDispreferList???true??????????????????executor?????????????????????????????????????????????executor??????????????????executor????????????
	 */
	@Test
	public void test_G_ContainerWithUseDispreferList() throws Exception {
		Main executor1 = startOneNewExecutorList(); // ?????????????????????executor

		boolean cleanOld = SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN;
		String taskOld = SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID;
		try {
			String taskId = "test1";
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = true;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskId;
			Main executor2 = startOneNewExecutorList(); // ??????????????????executor

			final int shardCount = 2;
			final String jobName = "test_G_ContainerWithUseDispreferList";

			for (int i = 0; i < shardCount; i++) {
				String key = jobName + "_" + i;
				SimpleJavaJob.statusMap.put(key, 0);
			}

			final JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(jobName);
			jobConfig.setCron("9 9 9 9 9 ? 2099");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(shardCount);
			jobConfig.setShardingItemParameters("0=0,1=1");
			jobConfig.setLocalMode(false);
			jobConfig.setPreferList("@" + taskId); // ??????preferList???@taskId
			jobConfig.setUseDispreferList(true); // ??????useDispreferList???true

			addJob(jobConfig);
			Thread.sleep(1000);
			enableJob(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);

			// executor2?????????0???1?????????executor1??????????????????
			List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
			assertThat(items).contains(0, 1);
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).isEmpty();

			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return hasCompletedZnodeForAllShards(jobName, shardCount);
				}
			}, 10);

			// wait running completed
			Thread.sleep(1000);
			// executor2??????
			stopExecutorGracefully(1);
			Thread.sleep(1000L);
			// ??????sharding????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);

			runAtOnce(jobName);
			// ??????????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);
			// executor1????????????0???1??????
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).contains(0, 1);

			disableJob(jobName);
			Thread.sleep(1000);
			removeJob(jobName);
			stopExecutorListGracefully();
		} finally {
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = cleanOld;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskOld;
		}
	}

	/**
	 * preferList??????????????????????????????useDispreferList???false??????????????????executor?????????????????????????????????????????????executor??????????????????executor?????????????????????
	 */
	@Test
	public void test_H_ContainerWithOnlyPreferList() throws Exception {
		Main executor1 = startOneNewExecutorList(); // ?????????????????????executor

		boolean cleanOld = SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN;
		String taskOld = SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID;
		try {
			String taskId = "test1";
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = true;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskId;
			Main executor2 = startOneNewExecutorList(); // ??????????????????executor

			int shardCount = 2;
			final String jobName = "test_H_ContainerWithOnlyPreferList";

			for (int i = 0; i < shardCount; i++) {
				String key = jobName + "_" + i;
				SimpleJavaJob.statusMap.put(key, 0);
			}

			JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(jobName);
			jobConfig.setCron("9 9 9 9 9 ? 2099");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(shardCount);
			jobConfig.setShardingItemParameters("0=0,1=1");
			jobConfig.setLocalMode(false);
			jobConfig.setPreferList("@" + taskId); // ??????preferList???@taskId
			jobConfig.setUseDispreferList(false); // ??????useDispreferList???false

			addJob(jobConfig);
			Thread.sleep(1000);
			enableJob(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);

			// executor2?????????0???1?????????executor1??????????????????
			List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
			assertThat(items).contains(0, 1);
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).isEmpty();
			// wait running completed
			Thread.sleep(1000);
			// executor2??????
			stopExecutorGracefully(1);
			Thread.sleep(1000L);
			// ??????sharding????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			// ??????????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);
			// executor1????????????????????????
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).isEmpty();

			disableJob(jobName);
			Thread.sleep(1000);
			removeJob(jobName);
			stopExecutorListGracefully();
		} finally {
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = cleanOld;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskOld;
		}
	}

	/**
	 * preferList????????????????????????????????????????????????useDispreferList???true??????????????????executor?????????????????????????????????????????????1?????????????????????executor??????????????????executor???????????????????????????useDispreferList?????????????????????
	 */
	@Test
	public void test_I_ContainerWithLocalModeAndUseDispreferList() throws Exception {
		Main executor1 = startOneNewExecutorList(); // ?????????????????????executor

		boolean cleanOld = SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN;
		String taskOld = SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID;
		try {
			String taskId = "test1";
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = true;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskId;
			Main executor2 = startOneNewExecutorList(); // ??????????????????executor

			int shardCount = 2;
			final String jobName = "test_I_ContainerWithLocalModeAndUseDispreferList";

			for (int i = 0; i < shardCount; i++) {
				String key = jobName + "_" + i;
				SimpleJavaJob.statusMap.put(key, 0);
			}

			JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(jobName);
			jobConfig.setCron("9 9 9 9 9 ? 2099");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(shardCount);
			jobConfig.setShardingItemParameters("*=a");
			jobConfig.setLocalMode(true); // ??????localMode???true
			jobConfig.setPreferList("@" + taskId); // ??????preferList???@taskId
			jobConfig.setUseDispreferList(true); // ??????useDispreferList???true

			addJob(jobConfig);
			Thread.sleep(1000);
			enableJob(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);

			// executor2?????????0?????????executor1??????????????????
			List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
			assertThat(items).hasSize(1).contains(0);
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).isEmpty();
			// wait running completed
			Thread.sleep(1000);
			// executor2??????
			stopExecutorGracefully(1);
			Thread.sleep(1000L);
			// ??????sharding????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			// ??????????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);
			// executor1?????????????????????
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).isEmpty();

			disableJob(jobName);
			Thread.sleep(1000);
			removeJob(jobName);
			stopExecutorListGracefully();
		} finally {
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = cleanOld;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskOld;
		}
	}

	/**
	 * preferList????????????????????????????????????????????????useDispreferList???false??????????????????executor?????????????????????????????????????????????1?????????????????????executor??????????????????executor??????????????????
	 */
	@Test
	public void test_J_ContainerWithLocalModeAndOnlyPreferList() throws Exception {
		Main executor1 = startOneNewExecutorList(); // ?????????????????????executor

		boolean cleanOld = SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN;
		String taskOld = SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID;
		try {
			String taskId = "test1";
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = true;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskId;
			Main executor2 = startOneNewExecutorList(); // ??????????????????executor

			int shardCount = 2;
			final String jobName = "test_J_ContainerWithLocalModeAndOnlyPreferList";

			for (int i = 0; i < shardCount; i++) {
				String key = jobName + "_" + i;
				SimpleJavaJob.statusMap.put(key, 0);
			}

			JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(jobName);
			jobConfig.setCron("9 9 9 9 9 ? 2099");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(shardCount);
			jobConfig.setShardingItemParameters("*=a");
			jobConfig.setLocalMode(true); // ??????localMode???true
			jobConfig.setPreferList("@" + taskId); // ??????preferList???@taskId
			jobConfig.setUseDispreferList(false); // ??????useDispreferList???false

			addJob(jobConfig);
			Thread.sleep(1000);
			enableJob(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);

			// executor2?????????0?????????executor1??????????????????
			List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor2.getExecutorName()))));
			assertThat(items).hasSize(1).contains(0);
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).isEmpty();
			// wait running completed
			Thread.sleep(1000);
			// executor2??????
			stopExecutorGracefully(1);
			Thread.sleep(1000L);
			// ??????sharding????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			// ??????????????????
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);
			// executor1????????????????????????
			items = ItemUtils.toItemList(regCenter.getDirectly(
					JobNodePath.getNodeFullPath(jobName, ShardingNode.getShardingNode(executor1.getExecutorName()))));
			assertThat(items).isEmpty();

			disableJob(jobName);
			Thread.sleep(1000);
			removeJob(jobName);
			stopExecutorListGracefully();
		} finally {
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = cleanOld;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskOld;
		}
	}

	/**
	 * preferList????????????????????????????????????useDispreferList???true???????????????????????????????????????
	 */
	@Test
	public void test_K_ContainerWithUseDispreferList_ButInvalidTaskId() throws Exception {
		Main logicExecutor = startOneNewExecutorList(); // ?????????????????????executor

		boolean cleanOld = SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN;
		String taskOld = SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID;
		try {
			String taskId = "test1";
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = true;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskId;
			Main vdosExecutor = startOneNewExecutorList(); // ??????????????????executor

			int shardCount = 2;
			final String jobName = "test_K_ContainerWithUseDispreferList_ButInvalidTaskId";

			for (int i = 0; i < shardCount; i++) {
				String key = jobName + "_" + i;
				SimpleJavaJob.statusMap.put(key, 0);
			}

			JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(jobName);
			jobConfig.setCron("9 9 9 9 9 ? 2099");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(shardCount);
			jobConfig.setShardingItemParameters("0=0,1=1");
			jobConfig.setLocalMode(false);
			jobConfig.setPreferList("@haha" + taskId); // ??????preferList???@hahataskId
			jobConfig.setUseDispreferList(true); // ??????useDispreferList???true

			addJob(jobConfig);
			Thread.sleep(1000);
			enableJob(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);

			// vdosExecutor?????????????????????logicExecutor?????????0???1??????
			List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(vdosExecutor.getExecutorName()))));
			assertThat(items).isEmpty();
			items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(logicExecutor.getExecutorName()))));
			assertThat(items).contains(0, 1);
			// wait running completed
			Thread.sleep(1000);
			// vdosExecutor??????
			stopExecutorGracefully(1);
			Thread.sleep(1000);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);
			// logicExecutor????????????0???1??????
			items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(logicExecutor.getExecutorName()))));
			assertThat(items).contains(0, 1);

			disableJob(jobName);
			Thread.sleep(1000);
			removeJob(jobName);
			stopExecutorListGracefully();
		} finally {
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = cleanOld;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskOld;
		}
	}

	/**
	 * preferList????????????????????????????????????useDispreferList???true???????????????????????????????????????????????????????????????????????????????????????????????????????????????
	 */
	@Test
	public void test_L_ContainerWithUseDispreferList_ButInvalidTaskId_ContainerFirst() throws Exception {
		boolean cleanOld = SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN;
		String taskOld = SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID;
		try {
			int shardCount = 2;
			final String jobName = "test_L_ContainerWithUseDispreferList_ButInvalidTaskId_ContainerFirst";

			for (int i = 0; i < shardCount; i++) {
				String key = jobName + "_" + i;
				SimpleJavaJob.statusMap.put(key, 0);
			}

			JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(jobName);
			jobConfig.setCron("9 9 9 9 9 ? 2099");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(shardCount);
			jobConfig.setShardingItemParameters("0=0,1=1");
			jobConfig.setLocalMode(false);
			jobConfig.setPreferList("@haha"); // ??????preferList???@haha
			jobConfig.setUseDispreferList(true); // ??????useDispreferList???true

			addJob(jobConfig);
			Thread.sleep(1000);
			enableJob(jobName);

			// ??????????????????executor
			String taskId = "test1";
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = true;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskId;
			Main vdosExecutor = startOneNewExecutorList();

			// ?????????????????????executor
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = false;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = null;
			Main logicExecutor = startOneNewExecutorList();

			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);

			runAtOnce(jobName);

			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);

			// vdosExecutor?????????????????????logicExecutor?????????0???1??????
			List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(vdosExecutor.getExecutorName()))));
			assertThat(items).isEmpty();
			items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(logicExecutor.getExecutorName()))));
			assertThat(items).contains(0, 1);
			// wait running completed
			Thread.sleep(1000);
			// vdosExecutor??????
			stopExecutorGracefully(0);
			Thread.sleep(1000L);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);
			// logicExecutor????????????0???1??????
			items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(logicExecutor.getExecutorName()))));
			assertThat(items).contains(0, 1);

			disableJob(jobName);
			Thread.sleep(1000);
			removeJob(jobName);
			stopExecutorListGracefully();
		} finally {
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = cleanOld;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskOld;
		}
	}

	/**
	 * preferList????????????????????????????????????useDispreferList???true????????????????????????????????????????????????????????????????????????????????????????????????????????????
	 */
	@Test
	public void test_M_UseDispreferList_ButInvalidLogicPreferList() throws Exception {
		boolean cleanOld = SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN;
		String taskOld = SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID;
		try {
			int shardCount = 2;
			final String jobName = "test_M_UseDispreferList_ButInvalidLogicPreferList";

			for (int i = 0; i < shardCount; i++) {
				String key = jobName + "_" + i;
				SimpleJavaJob.statusMap.put(key, 0);
			}

			JobConfig jobConfig = new JobConfig();
			jobConfig.setJobName(jobName);
			jobConfig.setCron("9 9 9 9 9 ? 2099");
			jobConfig.setJobType(JobType.JAVA_JOB.toString());
			jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
			jobConfig.setShardingTotalCount(shardCount);
			jobConfig.setShardingItemParameters("0=0,1=1");
			jobConfig.setLocalMode(false);
			jobConfig.setPreferList("haha"); // ??????preferList???@haha
			jobConfig.setUseDispreferList(true); // ??????useDispreferList???true

			addJob(jobConfig);
			Thread.sleep(1000);
			enableJob(jobName);

			// ??????????????????executor
			String taskId = "test1";
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = true;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskId;
			Main vdosExecutor = startOneNewExecutorList();

			// ?????????????????????executor
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = false;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = null;
			Main logicExecutor = startOneNewExecutorList();

			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);

			runAtOnce(jobName);

			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);

			// vdosExecutor?????????????????????logicExecutor?????????0???1??????
			List<Integer> items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(vdosExecutor.getExecutorName()))));
			assertThat(items).isEmpty();
			items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(logicExecutor.getExecutorName()))));
			assertThat(items).contains(0, 1);
			// wait running completed
			Thread.sleep(1000);
			// vdosExecutor??????
			stopExecutorGracefully(0);
			Thread.sleep(1000L);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return isNeedSharding(jobName);
				}
			}, 10);
			runAtOnce(jobName);
			waitForFinish(new FinishCheck() {
				@Override
				public boolean isOk() {
					return !isNeedSharding(jobName);
				}
			}, 10);
			// logicExecutor????????????0???1??????
			items = ItemUtils.toItemList(regCenter.getDirectly(JobNodePath
					.getNodeFullPath(jobName, ShardingNode.getShardingNode(logicExecutor.getExecutorName()))));
			assertThat(items).contains(0, 1);

			disableJob(jobName);
			Thread.sleep(1000);
			removeJob(jobName);
			stopExecutorListGracefully();
		} finally {
			SystemEnvProperties.VIP_SATURN_EXECUTOR_CLEAN = cleanOld;
			SystemEnvProperties.VIP_SATURN_CONTAINER_DEPLOYMENT_ID = taskOld;
		}
	}

	/**
	 * sharding???????????????????????????????????????
	 */
	@Test
	public void test_N_NotifyNecessaryJobs() throws Exception {
		// ??????1???executor
		Main executor1 = startOneNewExecutorList();
		Thread.sleep(1000);

		// ?????????????????????
		Thread.sleep(1000);
		final String jobName1 = "test_N_NotifyNecessaryJobs1";
		JobConfig jobConfig1 = new JobConfig();
		jobConfig1.setJobName(jobName1);
		jobConfig1.setCron("9 9 9 9 9 ? 2099");
		jobConfig1.setJobType(JobType.JAVA_JOB.toString());
		jobConfig1.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig1.setShardingTotalCount(1);
		jobConfig1.setShardingItemParameters("0=0");
		addJob(jobConfig1);
		Thread.sleep(1000);
		enableJob(jobName1);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName1);
			}
		}, 10);
		runAtOnce(jobName1);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName1);
			}
		}, 10);

		// ?????????????????????
		Thread.sleep(1000);
		final String jobName2 = "test_N_NotifyNecessaryJobs2";
		JobConfig jobConfig2 = new JobConfig();
		jobConfig2.setJobName(jobName2);
		jobConfig2.setCron("9 9 9 9 9 ? 2099");
		jobConfig2.setJobType(JobType.JAVA_JOB.toString());
		jobConfig2.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig2.setShardingTotalCount(1);
		jobConfig2.setShardingItemParameters("0=0");

		addJob(jobConfig2);
		// job1???job2?????????re-sharding
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName1) && !isNeedSharding(jobName2);
			}
		}, 10);

		enableJob(jobName2);
		// job1??????re-sharding
		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName1);
			}
		}, 10);

		disableJob(jobName1);
		removeJob(jobName1);
		disableJob(jobName2);
		removeJob(jobName2);
		stopExecutorListGracefully();
	}

	/**
	 * sharding??????????????????????????????????????? test the fix:
	 * https://github.com/vipshop/Saturn/commit/9b64dfe50c21c1b4f3e3f781d5281be06a0a8d08
	 */
	@Test
	public void test_O_NotifyNecessaryJobsPrior() throws Exception {
		// ??????1???executor
		Main executor1 = startOneNewExecutorList();
		Thread.sleep(1000);

		// ?????????????????????
		Thread.sleep(1000);
		final String jobName = "test_O_NotifyNecessaryJobsPrior";
		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(1);
		jobConfig.setShardingItemParameters("0=0");
		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);
		runAtOnce(jobName);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);

		// ????????????
		Thread.sleep(1000);
		disableJob(jobName);

		// ??????preferList??????????????????executor???????????????useDispreferList???false
		zkUpdateJobNode(jobName, "config/preferList", "abc");
		zkUpdateJobNode(jobName, "config/useDispreferList", "false");

		// ????????????
		Thread.sleep(500);
		enableJob(jobName);

		// job1???re-sharding
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);

		disableJob(jobName);
		Thread.sleep(1000);
		removeJob(jobName);
		stopExecutorListGracefully();
	}

	/**
	 * NamespaceShardingService is not necessary to persist the sharding result content that is not changed<br/>
	 * https://github.com/vipshop/Saturn/issues/88
	 */
	@Test
	public void test_P_PersistShardingContentIfNecessary() throws Exception {
		// ??????1???executor
		Main executor1 = startOneNewExecutorList();
		Thread.sleep(1000);

		// ?????????????????????
		Thread.sleep(1000);
		final String jobName = "test_P_PersistShardingContentIfNecessary";
		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(1);
		jobConfig.setShardingItemParameters("0=0");
		jobConfig.setPreferList("abc");
		jobConfig.setUseDispreferList(false);
		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);
		runAtOnce(jobName);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);
		long mtime = ((CuratorFramework) regCenter.getRawClient()).checkExists()
				.forPath(SaturnExecutorsNode.getShardingContentElementNodePath("0")).getMtime();

		// ????????????
		Thread.sleep(1000);
		disableJob(jobName);

		Thread.sleep(1000);
		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);

		long mtime2 = ((CuratorFramework) regCenter.getRawClient()).checkExists()
				.forPath(SaturnExecutorsNode.getShardingContentElementNodePath("0")).getMtime();

		assertThat(mtime).isEqualTo(mtime2);

		removeJob(jobName);
		stopExecutorListGracefully();
	}

	/**
	 * https://github.com/vipshop/Saturn/issues/119
	 */
	@Test
	public void test_Q_PersistNecessaryTheRightData() throws Exception {
		// ??????1???executor
		Main executor1 = startOneNewExecutorList();
		Thread.sleep(1000);

		// ?????????????????????
		Thread.sleep(1000);
		final String jobName = "test_Q_PersistNecessaryTheRightData";
		JobConfig jobConfig = new JobConfig();
		jobConfig.setJobName(jobName);
		jobConfig.setCron("9 9 9 9 9 ? 2099");
		jobConfig.setJobType(JobType.JAVA_JOB.toString());
		jobConfig.setJobClass(SimpleJavaJob.class.getCanonicalName());
		jobConfig.setShardingTotalCount(1);
		jobConfig.setShardingItemParameters("0=0");
		jobConfig.setUseDispreferList(false);
		addJob(jobConfig);
		Thread.sleep(1000);
		enableJob(jobName);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);

		String jobLeaderShardingNecessaryNodePath = SaturnExecutorsNode.getJobLeaderShardingNecessaryNodePath(jobName);
		String data1 = regCenter.getDirectly(jobLeaderShardingNecessaryNodePath);
		System.out.println("data1:" + data1);

		runAtOnce(jobName);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);

		// ?????????2???executor
		Main executor2 = startOneNewExecutorList();
		Thread.sleep(1000);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);

		String data2 = regCenter.getDirectly(jobLeaderShardingNecessaryNodePath);
		System.out.println("data2:" + data2);
		assertThat(data2.contains(executor2.getExecutorName())).isTrue();

		runAtOnce(jobName);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return !isNeedSharding(jobName);
			}
		}, 10);

		// wait running completed
		Thread.sleep(1000);
		// offline executor2
		stopExecutorGracefully(1);
		Thread.sleep(1000);

		waitForFinish(new FinishCheck() {
			@Override
			public boolean isOk() {
				return isNeedSharding(jobName);
			}
		}, 10);

		String data3 = regCenter.getDirectly(jobLeaderShardingNecessaryNodePath);
		System.out.println("data3:" + data3);

		assertThat(data3.contains(executor2.getExecutorName())).isFalse();

		disableJob(jobName);
		Thread.sleep(1000);
		removeJob(jobName);
		stopExecutorListGracefully();
	}

}
