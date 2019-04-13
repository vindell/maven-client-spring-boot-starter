/*
 * Copyright (c) 2018, vindell (https://github.com/vindell).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.maven.spring.boot;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.spring.boot.ext.MavenClientTemplate;
import org.junit.Test;
import org.springframework.cloud.deployer.resource.maven.MavenProperties.RemoteRepository;
import org.springframework.core.io.Resource;

public class MavenClientTemplate_Test {
	
	private static String coordinates = "com.squareup.okhttp3:okhttp:3.13.1";
	private static MavenClientTemplate clientTemplate = null;

	static {
		
		MavenClientProperties properties = new MavenClientProperties();
		// 当Maven验证构件校验文件失败时该怎么做-ignore（忽略），fail（失败），或者warn（警告）。
		properties.setChecksumPolicy("warn");
		// 连接超时时间
		properties.setConnectTimeout(100000);
		// 本地Maven仓库
		properties.setLocalRepository("E:\\Java\\.m2\\repository2");
		// 是否离线模式
		properties.setOffline(false);
		// 远程仓库地址
		Map<String, RemoteRepository> remoteRepositories = new HashMap<String, RemoteRepository>();

		remoteRepositories.put("maven-local", new RemoteRepository("http://localhost:8081/repository/maven-public/"));

		properties.setRemoteRepositories(remoteRepositories);
		// 请求超时时间
		properties.setRequestTimeout(50000);
		// 除了解析JAR工件之外，如果为true，则解析POM工件。 这与Maven解析工件的方式一致。
		properties.setResolvePom(true);
		// 该参数指定更新发生的频率。Maven会比较本地POM和远程POM的时间戳。这里的选项是：always（一直），daily（默认，每日），interval：X（这里X是以分钟为单位的时间间隔），或者never（从不）。
		properties.setUpdatePolicy("always");
		
		clientTemplate = new MavenClientTemplate(properties);
	}
	
	@Test
	public void testResource1() {

		Resource resource = clientTemplate.resource(coordinates);
		System.out.println("Description:" + resource.getDescription());
		System.out.println("Filename:" + resource.getFilename());
		
	}

	@Test
	public void testResource2() {

		Resource resource = clientTemplate.resource("com.squareup.okhttp3","okhttp","3.14.1");
		System.out.println("Description:" + resource.getDescription());
		System.out.println("Filename:" + resource.getFilename());
		 
	}

}
