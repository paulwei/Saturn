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

package com.vip.saturn.job.console.controller.gui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.vip.saturn.job.console.aop.annotation.Audit;
import com.vip.saturn.job.console.aop.annotation.AuditParam;
import com.vip.saturn.job.console.controller.SuccessResponseEntity;
import com.vip.saturn.job.console.domain.JobConfigMeta;
import com.vip.saturn.job.console.domain.RequestResult;
import com.vip.saturn.job.console.domain.SystemConfigVo;
import com.vip.saturn.job.console.exception.SaturnJobConsoleException;
import com.vip.saturn.job.console.mybatis.entity.SystemConfig;
import com.vip.saturn.job.console.service.SystemConfigService;
import com.vip.saturn.job.console.service.helper.SystemConfigProperties;
import com.vip.saturn.job.console.utils.PermissionKeys;
import com.vip.saturn.job.console.utils.SaturnConstants;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

/**
 * System config related operations.
 *
 * @author kfchu
 */
@RequestMapping("/console/configs/console")
public class ConsoleConfigController extends AbstractGUIController {

	protected static final ObjectMapper YAML_OBJ_MAPPER = new ObjectMapper(new YAMLFactory());

	@Resource
	private SystemConfigService systemConfigService;

	@Value(value = "classpath:system-config-meta.yaml")
	private org.springframework.core.io.Resource configYaml;

	/**
	 * ??????????????????
	 *
	 * @param key ??????key
	 * @param value ?????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@PostMapping("/create")
	public SuccessResponseEntity createConfig(@AuditParam(value = "key") @RequestParam String key,
			@AuditParam(value = "value") @RequestParam String value) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.systemConfig);
		//????????????EXECUTOR_CONFIGS
		if (SystemConfigProperties.EXECUTOR_CONFIGS.equals(key)) {
			throw new SaturnJobConsoleException(String.format("??????????????????%s", key));
		}
		SystemConfig systemConfig = new SystemConfig();
		systemConfig.setProperty(key);
		systemConfig.setValue(value);

		systemConfigService.createConfig(systemConfig);
		return new SuccessResponseEntity();
	}

	/**
	 * ??????????????????
	 *
	 * @param key ??????key
	 * @param value ?????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@Audit
	@PostMapping("/update")
	public SuccessResponseEntity updateConfig(@AuditParam(value = "key") @RequestParam String key,
			@AuditParam(value = "value") @RequestParam String value) throws SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.systemConfig);
		//????????????EXECUTOR_CONFIGS
		if (SystemConfigProperties.EXECUTOR_CONFIGS.equals(key)) {
			throw new SaturnJobConsoleException(String.format("??????????????????%s", key));
		}
		SystemConfig systemConfig = new SystemConfig();
		systemConfig.setProperty(key);
		systemConfig.setValue(value);

		systemConfigService.updateConfig(systemConfig);
		return new SuccessResponseEntity();
	}


	/**
	 * ?????????????????????????????????
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@GetMapping
	public SuccessResponseEntity getConfigs() throws IOException, SaturnJobConsoleException {
		assertIsPermitted(PermissionKeys.systemConfig);
		//????????????meta
		Map<String, List<JobConfigMeta>> jobConfigGroups = getSystemConfigMeta();
		//????????????????????????
		List<SystemConfig> systemConfigs = systemConfigService.getSystemConfigsDirectly(null);
		//??????EXECUTOR_CONFIGS
		removeExecutorConfigs(systemConfigs);

		return new SuccessResponseEntity(genSystemConfigInfo(jobConfigGroups, systemConfigs));
	}

	/**
	 * ???????????????????????????????????????????????????.
	 */
	@ApiResponses(value = {@ApiResponse(code = 200, message = "Success/Fail", response = RequestResult.class)})
	@GetMapping("/env")
	public SuccessResponseEntity getEnvConfig() {
		return new SuccessResponseEntity(systemConfigService.getValueDirectly(SaturnConstants.SYSTEM_CONFIG_ENV));
	}

	/**
	 * ??????Executor????????????????????????????????????????????????
	 * @param systemConfigs ???????????????????????????
	 */
	private void removeExecutorConfigs(List<SystemConfig> systemConfigs) {
		if (systemConfigs == null) {
			return;
		}
		Iterator<SystemConfig> iterator = systemConfigs.iterator();
		while (iterator.hasNext()) {
			SystemConfig systemConfig = iterator.next();
			if (SystemConfigProperties.EXECUTOR_CONFIGS.equals(systemConfig.getProperty())) {
				iterator.remove();
			}
		}
	}

	/**
	 * ??????Yaml??????????????????????????????
	 *
	 * @param jobConfigGroups ??????yaml?????????????????????????????????
	 * @param systemConfigs ???????????????????????????
	 * @return ???????????????????????????map
	 */
	private Map<String, List<SystemConfigVo>> genSystemConfigInfo(Map<String, List<JobConfigMeta>> jobConfigGroups,
			List<SystemConfig> systemConfigs) {
		Map<String, SystemConfig> systemConfigMap = convertList2Map(systemConfigs);
		Map<String, List<SystemConfigVo>> jobConfigDisplayInfoMap = Maps.newHashMap();
		Set<String> categorizedConfigKeySet = Sets.newHashSet();

		for (Map.Entry<String, List<JobConfigMeta>> group : jobConfigGroups.entrySet()) {
			List<JobConfigMeta> jobConfigMetas = group.getValue();
			List<SystemConfigVo> jobConfigVos = Lists.newArrayListWithCapacity(jobConfigMetas.size());
			for (JobConfigMeta configMeta : jobConfigMetas) {
				String configName = configMeta.getName();
				SystemConfig systemConfig = systemConfigMap.get(configName);
				String value = systemConfig != null ? systemConfig.getValue() : null;
				jobConfigVos.add(new SystemConfigVo(configName, value, configMeta.getDesc_zh()));
				categorizedConfigKeySet.add(configName);
			}

			jobConfigDisplayInfoMap.put(group.getKey(), jobConfigVos);
		}

		// ??????????????????yaml?????????????????????Others??????
		if (categorizedConfigKeySet.size() != systemConfigs.size()) {
			List<SystemConfigVo> unCategorizedJobConfigVos = getUncategorizedSystemConfigs(systemConfigs,
					categorizedConfigKeySet);
			jobConfigDisplayInfoMap.put("other_configs", unCategorizedJobConfigVos);
		}

		return jobConfigDisplayInfoMap;
	}

	private List<SystemConfigVo> getUncategorizedSystemConfigs(List<SystemConfig> systemConfigList,
			Set<String> configKeySet) {
		List<SystemConfigVo> unCategorizedJobConfigVos = Lists.newArrayList();
		for (SystemConfig systemConfig : systemConfigList) {
			String property = systemConfig.getProperty();
			if (configKeySet.contains(property)) {
				continue;
			}
			unCategorizedJobConfigVos.add(new SystemConfigVo(property, systemConfig.getValue(), ""));
		}

		return unCategorizedJobConfigVos;
	}

	private Map<String, List<JobConfigMeta>> getSystemConfigMeta() throws IOException {
		TypeReference<HashMap<String, List<JobConfigMeta>>> typeRef = new TypeReference<HashMap<String, List<JobConfigMeta>>>() {
		};

		return YAML_OBJ_MAPPER.readValue(configYaml.getInputStream(), typeRef);
	}

	private Map<String, SystemConfig> convertList2Map(List<SystemConfig> configList) {
		Map<String, SystemConfig> configMap = Maps.newHashMap();
		for (SystemConfig config : configList) {
			if (configMap.containsKey(config.getProperty())) {
				continue;
			}
			configMap.put(config.getProperty(), config);
		}

		return configMap;
	}

	public SystemConfigService getSystemConfigService() {
		return systemConfigService;
	}
}
