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
package org.apache.maven.spring.boot.utils;

import java.util.Map;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.*;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.spi.artifact.decorator.ArtifactDecoratorFactory;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;

/**
 * TODO
 * 
 * @author ： <a href="https://github.com/hiwepy">hiwepy</a>
 */
public class RepositorySystemUtils {

	/**
	 * Check if the proxy settings are provided.
	 * @param properties MavenProperties
	 * @return boolean true if the proxy settings are provided.
	 */
	public static boolean isProxyEnabled(MavenProperties properties) {
		return (properties.getProxy() != null && properties.getProxy().getHost() != null
				&& properties.getProxy().getPort() > 0);
	}

	/**
	 * Check if the proxy setting has username/password set.
	 * @param properties MavenProperties
	 * @return boolean true if both the username/password are set
	 */
	public static boolean proxyHasCredentials(MavenProperties properties) {
		return (properties.getProxy() != null && properties.getProxy().getAuth() != null
				&& properties.getProxy().getAuth().getUsername() != null
				&& properties.getProxy().getAuth().getPassword() != null);
	}

	/**
	 * Check if the {@link MavenProperties.RemoteRepository} setting has
	 * username/password set.
	 * @param remoteRepository remoteRepository
	 * @return boolean true if both the username/password are set
	 */
	public static boolean remoteRepositoryHasCredentials(MavenProperties.RemoteRepository remoteRepository) {
		return remoteRepository != null && remoteRepository.getAuth() != null
				&& remoteRepository.getAuth().getUsername() != null && remoteRepository.getAuth().getPassword() != null;
	}

	/**
	 * Create an {@link Authentication} given a username/password
	 *
	 * @param username username
	 * @param password password 
	 * @return a configured {@link Authentication}
	 */
	public static Authentication newAuthentication(final String username, final String password) {
		return new Authentication() {

			@Override
			public void fill(AuthenticationContext context, String key, Map<String, String> data) {
				context.put(AuthenticationContext.USERNAME, username);
				context.put(AuthenticationContext.PASSWORD, password);
			}

			@Override
			public void digest(AuthenticationDigest digest) {
				digest.update(AuthenticationContext.USERNAME, username, AuthenticationContext.PASSWORD, password);
			}
		};
	}

	/*
	 * Create a session to manage remote and local synchronization.
	 */
	public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem repositorySystem,
			MavenProperties properties, Authentication authentication) {

		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository localRepo = new LocalRepository(properties.getLocalRepository());
		session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepo));
		session.setOffline(properties.isOffline());
		session.setUpdatePolicy(properties.getUpdatePolicy());
		session.setChecksumPolicy(properties.getChecksumPolicy());
		if (properties.getConnectTimeout() != null) {
			session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, properties.getConnectTimeout());
		}
		if (properties.getRequestTimeout() != null) {
			session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, properties.getRequestTimeout());
		}
		if (isProxyEnabled(properties)) {
			DefaultProxySelector proxySelector = new DefaultProxySelector();
			Proxy proxy = new Proxy(properties.getProxy().getProtocol(), properties.getProxy().getHost(),
					properties.getProxy().getPort(), authentication);
			proxySelector.add(proxy, properties.getProxy().getNonProxyHosts());
			session.setProxySelector(proxySelector);
		}

		return session;
	}


	public static Dependency createDependencyRoot(MavenResource resource) {
        Artifact artifact = null;
        if (resource.getClassifier() == null) {
            artifact = new DefaultArtifact(String.format("%s:%s:%s:%s", resource.getGroupId(),
            		resource.getArtifactId(),
            		resource.getExtension(),
            		resource.getVersion()));
            return new Dependency(artifact, "compile");
        }
        else {
            artifact = new DefaultArtifact(String.format("%s:%s:%s:%s:%s",
            		resource.getGroupId(), resource.getArtifactId(),
            		resource.getExtension(),
            		resource.getClassifier(), resource.getVersion()));
            return new Dependency(artifact, "compile", true);
        }
	}
	
}
