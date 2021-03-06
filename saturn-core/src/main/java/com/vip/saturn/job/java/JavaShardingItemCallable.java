/**
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.vip.saturn.job.java;

import com.vip.saturn.job.SaturnJobExecutionContext;
import com.vip.saturn.job.SaturnJobReturn;
import com.vip.saturn.job.SaturnSystemErrorGroup;
import com.vip.saturn.job.SaturnSystemReturnCode;
import com.vip.saturn.job.basic.AbstractSaturnJob;
import com.vip.saturn.job.basic.SaturnConstant;
import com.vip.saturn.job.basic.SaturnExecutionContext;
import com.vip.saturn.job.basic.ShardingItemCallable;
import com.vip.saturn.job.utils.LogUtils;
import com.vip.saturn.job.utils.SaturnSystemOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class JavaShardingItemCallable extends ShardingItemCallable {

	protected static final int INIT = 0;
	protected static final int TIMEOUT = 1;
	protected static final int SUCCESS = 2;
	protected static final int FORCE_STOP = 3;
	protected static final int STOPPED = 4;
	private static final Logger log = LoggerFactory.getLogger(JavaShardingItemCallable.class);
	protected Thread currentThread;
	protected AtomicInteger status = new AtomicInteger(INIT);
	protected boolean breakForceStop = false;
	protected Object contextForJob;

	public JavaShardingItemCallable(String jobName, Integer item, String itemValue, int timeoutSeconds,
			SaturnExecutionContext shardingContext, AbstractSaturnJob saturnJob) {
		super(jobName, item, itemValue, timeoutSeconds, shardingContext, saturnJob);
	}

	/**
	 * ????????????
	 */
	public static Object cloneObject(Object source, ClassLoader classLoader) throws Exception {
		if (source == null) {
			return null;
		}
		Class<?> clazz = classLoader.loadClass(source.getClass().getCanonicalName());
		Object target = clazz.newInstance();
		clazz.getMethod("copyFrom", Object.class).invoke(target, source);
		return target;
	}

	/**
	 * ?????????????????????????????????
	 */
	public Thread getCurrentThread() {
		return currentThread;
	}

	/**
	 * ?????????????????????????????????
	 */
	public void setCurrentThread(Thread currentThread) {
		this.currentThread = currentThread;
	}

	/**
	 * ???????????????????????????
	 */
	public Object getContextForJob(ClassLoader jobClassLoader) throws Exception {
		if (contextForJob == null) {
			if (shardingContext == null) {
				return null;
			}
			SaturnJobExecutionContext context = new SaturnJobExecutionContext();
			context.setJobName(shardingContext.getJobName());
			context.setShardingItemParameters(shardingContext.getShardingItemParameters());
			context.setCustomContext(shardingContext.getCustomContext());
			context.setJobParameter(shardingContext.getJobParameter());
			context.setShardingItems(shardingContext.getShardingItems());
			context.setShardingTotalCount(shardingContext.getShardingTotalCount());
			context.setQueueName(shardingContext.getJobConfiguration().getQueueName());
			contextForJob = cloneObject(context, jobClassLoader);
		}

		return contextForJob;
	}

	/**
	 * ???????????????????????????TIMEOUT
	 *
	 * @return Mark timeout success or fail
	 */
	public boolean setTimeout() {
		return status.compareAndSet(INIT, TIMEOUT);
	}

	/**
	 * ?????????????????????TIMEOUT
	 */
	public boolean isTimeout() {
		return status.get() == TIMEOUT;
	}

	/**
	 * ???????????????????????????FORCE_STOP
	 */
	public boolean forceStop() {
		return status.compareAndSet(INIT, FORCE_STOP);
	}

	/**
	 * ???????????????????????????
	 */
	public boolean isBreakForceStop() {
		return breakForceStop;
	}

	/**
	 * ???????????????FORCE_STOP??????
	 */
	public boolean isForceStop() {
		return status.get() == FORCE_STOP;
	}

	/**
	 * ????????????
	 */
	public boolean isSuccess() {
		return status.get() == SUCCESS;
	}

	/**
	 * ???????????????
	 */
	public void reset() {
		status.set(INIT);
		breakForceStop = false;
		saturnJobReturn = null;
		businessReturned = false;
	}

	/**
	 * ???????????????
	 */
	public void beforeExecution() {
		this.startTime = System.currentTimeMillis();
	}

	public SaturnJobReturn doExecution() throws Throwable {
		return ((SaturnJavaJob) saturnJob).doExecution(jobName, item, itemValue, shardingContext, this);
	}

	/**
	 * ???????????????
	 */
	public void afterExecution() {
		this.endTime = System.currentTimeMillis();
	}

	/**
	 * ??????????????????????????????
	 *
	 * @return ????????????
	 */
	public SaturnJobReturn call() {
		reset();

		SaturnSystemOutputStream.initLogger();
		currentThread = Thread.currentThread();
		SaturnJobReturn temp = null;
		try {

			beforeExecution();

			temp = doExecution();

			// ?????????????????????????????????????????????
			breakForceStop = true;
		} catch (Throwable t) {
			// ?????????????????????????????????????????????
			breakForceStop = true;

			// ???????????????????????????????????? ???????????????????????????SaturnJobReturn???
			if (status.get() != TIMEOUT && status.get() != FORCE_STOP) {
				LogUtils.error(log, jobName, t.toString(), t);
				temp = new SaturnJobReturn(SaturnSystemReturnCode.SYSTEM_FAIL, t.getMessage(),
						SaturnSystemErrorGroup.FAIL);
			}

		} finally {
			if (status.compareAndSet(INIT, SUCCESS)) {
				saturnJobReturn = temp;
			}

			if (saturnJob != null && saturnJob.getConfigService().showNormalLog()) {
				String jobLog = SaturnSystemOutputStream.clearAndGetLog();
				if (jobLog != null && jobLog.length() > SaturnConstant.MAX_JOB_LOG_DATA_LENGTH) {
					LogUtils.info(log, jobName,
							"As the job log exceed max length, only the previous {} characters will be reported",
							SaturnConstant.MAX_JOB_LOG_DATA_LENGTH);
					jobLog = jobLog.substring(0, SaturnConstant.MAX_JOB_LOG_DATA_LENGTH);
				}

				this.shardingContext.putJobLog(this.item, jobLog);
			}
		}

		return saturnJobReturn;
	}

	protected void checkAndSetSaturnJobReturn() {
		switch (status.get()) {
			case TIMEOUT:
				saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.SYSTEM_FAIL,
						"execute job timeout(" + timeoutSeconds * 1000 + "ms)", SaturnSystemErrorGroup.TIMEOUT);
				break;
			case FORCE_STOP:
				saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.SYSTEM_FAIL, "the job was forced to stop",
						SaturnSystemErrorGroup.FAIL);
				break;
			case STOPPED:
				saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.SYSTEM_FAIL,
						"the job was stopped, will not run the business code", SaturnSystemErrorGroup.FAIL);
				break;
			default:
				break;
		}
		if (saturnJobReturn == null) {
			saturnJobReturn = new SaturnJobReturn(SaturnSystemReturnCode.USER_FAIL,
					"the SaturnJobReturn can not be null", SaturnSystemErrorGroup.FAIL);
		}
	}

	public void beforeTimeout() {
		try {
			((SaturnJavaJob) saturnJob).beforeTimeout(jobName, item, itemValue, shardingContext, this);
		} catch (Throwable t) {
			LogUtils.error(log, jobName, t.toString(), t);
		}
	}

	protected void onTimeout() {
		try {
			((SaturnJavaJob) saturnJob).postTimeout(jobName, item, itemValue, shardingContext, this);
		} catch (Throwable t) {
			LogUtils.error(log, jobName, t.toString(), t);
		}
	}

	public void beforeForceStop() {
		try {
			((SaturnJavaJob) saturnJob).beforeForceStop(jobName, item, itemValue, shardingContext, this);
		} catch (Throwable t) {
			LogUtils.error(log, jobName, t.toString(), t);
		}
	}

	protected void postForceStop() {
		try {
			((SaturnJavaJob) saturnJob).postForceStop(jobName, item, itemValue, shardingContext, this);
		} catch (Throwable t) {
			LogUtils.error(log, jobName, t.toString(), t);
		}
	}

}
