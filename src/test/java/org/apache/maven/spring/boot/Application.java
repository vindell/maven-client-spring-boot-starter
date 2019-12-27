/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
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

import java.io.File;

import javax.annotation.PostConstruct;

import org.apache.maven.spring.boot.ext.MavenClientTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
	
	private static String coordinates = "com.squareup.okhttp3:okhttp:3.14.1";
	
	@Autowired
	private MavenClientTemplate mavenClientTemplate;
	
	@PostConstruct
	private void init() throws Exception {
		
		mavenClientTemplate.install(new File("D:\\okhttp-3.14.1.jar"), coordinates);
		mavenClientTemplate.deploy(new File("D:\\okhttp-3.14.1.jar"), coordinates, "maven-releases");
		
	}
	
	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}

}
