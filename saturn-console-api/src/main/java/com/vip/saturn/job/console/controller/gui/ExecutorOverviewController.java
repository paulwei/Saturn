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

package com.vip.saturn.job.console.controller.gui;

import com.google.common.collect.Lists;
import com.vip.saturn.job.console.aop.annotation.Audit;
import com.vip.saturn.job.console.aop.annotation.AuditParam;
import com.vip.saturn.job.console.controller.SuccessResponseEntity;
import com.vip.saturn.job.console.domain.RequestResult;
import com.vip.saturn.job.console.domain.ServerBriefInfo;
import com.vip.saturn.job.console.domain.ServerStatus;
import com.vip.saturn.job.console.exception.SaturnJobConsoleException;
import com.vip.saturn.job.console.service.ExecutorService;
import com.vip.saturn.job.console.utils.PermissionKeys;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.vip.saturn.job.console.exception.SaturnJobConsoleException.ERROR_CODE_BAD_REQUEST;
import static com.vip.saturn.job.console.exception.SaturnJobConsoleException.ERROR_CODE_NOT_EXISTED;

/**
 * Executor overview related operations.
 */
@RequestMapping("/console/namespaces/{namespace:.+}/executors")
public class ExecutorOverviewController extends AbstractGUIController {

	private static final Logger log = LoggerFactory.getLogger(ExecutorOverviewController.class);

	private static final String TRAFFIC_OPERATION_EXTRACT = "extract";

	private static final String TRAFFIC_OPERATION_RECOVER = "recover";

	@Resource
	private ExecutorService executorService;

	/**
	 * ??????????????????executor????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@GetMapping
	public SuccessResponseEntity getExecutors(final HttpServletRequest request, @PathVariable String namespace,
			@RequestParam(required = false) String status) throws SaturnJobConsoleException {
		if ("online".equalsIgnoreCase(status)) {
			return new SuccessResponseEntity(executorService.getExecutors(namespace, ServerStatus.ONLINE));
		}

		return new SuccessResponseEntity(executorService.getExecutors(namespace));
	}

	/**
	 * ??????executor??????????????????????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@GetMapping(value = "/{executorName}/allocationWithStatus")
	public SuccessResponseEntity getExecutorAllocationWithStatus(final HttpServletRequest request,
			@PathVariable String namespace, @PathVariable String executorName) throws SaturnJobConsoleException {
		return new SuccessResponseEntity(executorService.getExecutorAllocation(namespace, executorName));
	}

	/**
	 * ??????executor??????????????????????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@GetMapping(value = "/{executorName}/runningInfo")
	public SuccessResponseEntity getExecutorRunningInfo(final HttpServletRequest request,
			@PathVariable String namespace, @PathVariable String executorName) throws SaturnJobConsoleException {
		return new SuccessResponseEntity(executorService.getExecutorRunningInfo(namespace, executorName));
	}

	/**
	 * ????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@PostMapping(value = "/shardAll")
	public SuccessResponseEntity shardAll(final HttpServletRequest request,
			@AuditParam("namespace") @PathVariable String namespace) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.executorShardAllAtOnce, namespace);
		executorService.shardAll(namespace);
		return new SuccessResponseEntity();
	}

	/*
	 * ????????????????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@PostMapping(value = "/{executorName}/traffic")
	public SuccessResponseEntity extractOrRecoverTraffic(final HttpServletRequest request,
			@AuditParam("namespace") @PathVariable String namespace,
			@AuditParam("executorName") @PathVariable String executorName,
			@AuditParam("operation") @RequestParam String operation) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.executorExtractOrRecoverTraffic, namespace);
		extractOrRecoverTraffic(namespace, executorName, operation);
		return new SuccessResponseEntity();
	}

	/*
	 * ??????????????????????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@PostMapping(value = "/traffic")
	public SuccessResponseEntity batchExtractOrRecoverTraffic(final HttpServletRequest request,
			@AuditParam("namespace") @PathVariable String namespace,
			@AuditParam("executorNames") @RequestParam List<String> executorNames,
			@AuditParam("operation") @RequestParam String operation) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.executorBatchExtractOrRecoverTraffic, namespace);
		List<String> success2ExtractOrRecoverTrafficExecutors = Lists.newArrayList();
		List<String> fail2ExtractOrRecoverTrafficExecutors = Lists.newArrayList();
		for (String executorName : executorNames) {
			try {
				extractOrRecoverTraffic(namespace, executorName, operation);
				success2ExtractOrRecoverTrafficExecutors.add(executorName);
			} catch (Exception e) {
				log.warn("exception happens during extract or recover traffic of executor:" + executorName, e);
				fail2ExtractOrRecoverTrafficExecutors.add(executorName);
			}
		}

		if (!fail2ExtractOrRecoverTrafficExecutors.isEmpty()) {
			StringBuilder message = new StringBuilder();
			message.append("???????????????executor:").append(success2ExtractOrRecoverTrafficExecutors).append("???")
					.append("???????????????executor:").append(fail2ExtractOrRecoverTrafficExecutors);
			throw new SaturnJobConsoleException(message.toString());
		}

		return new SuccessResponseEntity();
	}

	private void extractOrRecoverTraffic(String namespace, String executorName, String operation)
			throws SaturnJobConsoleException {
		if (TRAFFIC_OPERATION_EXTRACT.equals(operation)) {
			executorService.extractTraffic(namespace, executorName);
		} else if (TRAFFIC_OPERATION_RECOVER.equals(operation)) {
			executorService.recoverTraffic(namespace, executorName);
		} else {
			throw new SaturnJobConsoleException(ERROR_CODE_BAD_REQUEST, "operation " + operation + "?????????");
		}
	}

	/**
	 * ??????executor
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@DeleteMapping(value = "/{executorName}")
	public SuccessResponseEntity removeExecutor(final HttpServletRequest request,
			@AuditParam("namespace") @PathVariable String namespace,
			@AuditParam("executorName") @PathVariable String executorName) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.executorRemove, namespace);
		// check executor is existed and online.
		checkExecutorStatus(namespace, executorName, ServerStatus.OFFLINE, "Executor?????????????????????");
		executorService.removeExecutor(namespace, executorName);
		return new SuccessResponseEntity();
	}

	/**
	 * ????????????executor
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@DeleteMapping
	public SuccessResponseEntity batchRemoveExecutors(final HttpServletRequest request,
			@AuditParam("namespace") @PathVariable String namespace,
			@AuditParam("executorNames") @RequestParam List<String> executorNames) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.executorBatchRemove, namespace);
		// check executor is existed and online.
		List<String> success2RemoveExecutors = Lists.newArrayList();
		List<String> fail2RemoveExecutors = Lists.newArrayList();
		for (String executorName : executorNames) {
			try {
				checkExecutorStatus(namespace, executorName, ServerStatus.OFFLINE, "Executor?????????????????????");
				executorService.removeExecutor(namespace, executorName);
				success2RemoveExecutors.add(executorName);
			} catch (Exception e) {
				log.warn("exception happens during remove executor:" + executorName, e);
				fail2RemoveExecutors.add(executorName);
			}
		}
		if (!fail2RemoveExecutors.isEmpty()) {
			StringBuilder message = new StringBuilder();
			message.append("???????????????executor:").append(success2RemoveExecutors).append("???").append("???????????????executor:")
					.append(fail2RemoveExecutors);
			throw new SaturnJobConsoleException(message.toString());
		}

		return new SuccessResponseEntity();
	}

	private void checkExecutorStatus(String namespace, String executorName, ServerStatus status, String errMsg)
			throws SaturnJobConsoleException {
		ServerBriefInfo executorInfo = executorService.getExecutor(namespace, executorName);
		if (executorInfo == null) {
			throw new SaturnJobConsoleException(ERROR_CODE_NOT_EXISTED, "Executor?????????");
		}
		if (status != executorInfo.getStatus()) {
			throw new SaturnJobConsoleException(ERROR_CODE_BAD_REQUEST, errMsg);
		}
	}

	/**
	 * ??????Dump?????????threadump???gc.log???
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@PostMapping(value = "/{executorName}/dump")
	public SuccessResponseEntity dump(final HttpServletRequest request,
			@AuditParam("namespace") @PathVariable String namespace,
			@AuditParam("executorName") @PathVariable String executorName) throws SaturnJobConsoleException {
		// check executor is existed and online.
		checkExecutorStatus(namespace, executorName, ServerStatus.ONLINE, "Executor?????????????????????dump");
		executorService.dump(namespace, executorName);
		return new SuccessResponseEntity();
	}

	/**
	 * ???????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@PostMapping(value = "/{executorName}/restart")
	public SuccessResponseEntity restart(final HttpServletRequest request,
			@AuditParam("namespace") @PathVariable String namespace,
			@AuditParam("executorName") @PathVariable String executorName) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.executorRestart, namespace);
		// check executor is existed and online.
		checkExecutorStatus(namespace, executorName, ServerStatus.ONLINE, "Executor???????????????????????????");
		executorService.restart(namespace, executorName);
		return new SuccessResponseEntity();
	}
}
