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
package org.apache.maven.spring.boot.utils;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.core.io.FileSystemResource;

/**
 * TODO
 * @author 		： <a href="https://github.com/vindell">vindell</a>
 */

public class ArtifactUtils {

	public static FileSystemResource toResource(ArtifactResult resolvedArtifact) {
		return new FileSystemResource(resolvedArtifact.getArtifact().getFile());
	}

	public static Artifact toJarArtifact(MavenResource resource) {
		return toArtifact(resource, resource.getExtension());
	}

	public static Artifact toPomArtifact(MavenResource resource) {
		return toArtifact(resource, "pom");
	}
	
	public static Artifact toArtifact(MavenResource resource, String extension) {
		return new DefaultArtifact(resource.getGroupId(),
				resource.getArtifactId(),
				resource.getClassifier() != null ? resource.getClassifier() : "",
				extension,
				resource.getVersion());
	}
	
	public static Artifact toSourcesArtifact(MavenResource resource) {
		return toArtifact(resource, "sources", resource.getExtension());
	}

	public static Artifact toJavadocArtifact(MavenResource resource) {
		return toArtifact(resource, "javadoc", resource.getExtension());
	}
	
	public static Artifact toArtifact(MavenResource resource, String classifier, String extension) {
		return new DefaultArtifact(resource.getGroupId(),
				resource.getArtifactId(),
				classifier != null ? classifier : "",
				extension,
				resource.getVersion());
	}
     
	
}
