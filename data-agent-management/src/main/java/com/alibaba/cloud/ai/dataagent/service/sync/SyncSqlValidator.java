/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.sync;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 同步 SQL 脚本安全校验（白名单语句类型）。
 */
@Component
public class SyncSqlValidator {

	private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
			"\\b(DROP|TRUNCATE|ALTER\\s+USER|GRANT|REVOKE|CREATE\\s+USER|LOAD\\s+DATA|INTO\\s+OUTFILE|INTO\\s+DUMPFILE)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern ALLOWED_START = Pattern.compile(
			"^(CREATE\\s+TABLE|DELETE\\s+FROM|INSERT\\s+INTO)\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	public void validate(String sqlScript) {
		if (!StringUtils.hasText(sqlScript)) {
			throw new IllegalArgumentException("生成的 SQL 为空，请重新描述同步需求");
		}

		String normalized = sqlScript.trim();
		if (FORBIDDEN_PATTERN.matcher(normalized).find()) {
			throw new IllegalArgumentException("生成的 SQL 包含不允许的操作（DROP/TRUNCATE 等），请修改同步需求后重试");
		}

		List<String> statements = splitStatements(normalized);
		if (statements.isEmpty()) {
			throw new IllegalArgumentException("生成的 SQL 无法解析为有效语句，请重新描述同步需求");
		}

		for (String statement : statements) {
			if (!ALLOWED_START.matcher(statement.trim()).find()) {
				throw new IllegalArgumentException(
						"生成的 SQL 包含不允许的语句类型，仅支持 CREATE TABLE / DELETE / INSERT，请修改同步需求后重试");
			}
		}
	}

	List<String> splitStatements(String sqlScript) {
		List<String> statements = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		boolean inBacktick = false;

		for (int i = 0; i < sqlScript.length(); i++) {
			char c = sqlScript.charAt(i);
			if (c == '\'' && !inDoubleQuote && !inBacktick) {
				inSingleQuote = !inSingleQuote;
			}
			else if (c == '"' && !inSingleQuote && !inBacktick) {
				inDoubleQuote = !inDoubleQuote;
			}
			else if (c == '`' && !inSingleQuote && !inDoubleQuote) {
				inBacktick = !inBacktick;
			}

			if (c == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
				appendIfNotBlank(statements, current);
				current.setLength(0);
			}
			else {
				current.append(c);
			}
		}
		appendIfNotBlank(statements, current);
		return statements;
	}

	private void appendIfNotBlank(List<String> statements, StringBuilder current) {
		String stmt = current.toString().trim();
		if (StringUtils.hasText(stmt)) {
			statements.add(stmt);
		}
	}

}
