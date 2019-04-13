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

import java.util.Map;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenResource;

/**
 * TODO
 * 
 * @author ： <a href="https://github.com/vindell">vindell</a>
 */
public class RepositorySystemUtils {

	/**
	 * Check if the proxy settings are provided.
	 * 
	 * @return boolean true if the proxy settings are provided.
	 */
	public static boolean isProxyEnabled(MavenProperties properties) {
		return (properties.getProxy() != null && properties.getProxy().getHost() != null
				&& properties.getProxy().getPort() > 0);
	}

	/**
	 * Check if the proxy setting has username/password set.
	 *
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
	 *
	 * @return boolean true if both the username/password are set
	 */
	public static boolean remoteRepositoryHasCredentials(MavenProperties.RemoteRepository remoteRepository) {
		return remoteRepository != null && remoteRepository.getAuth() != null
				&& remoteRepository.getAuth().getUsername() != null && remoteRepository.getAuth().getPassword() != null;
	}

	/**
	 * Create an {@link Authentication} given a username/password
	 *
	 * @param username
	 * @param password
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


	/*
	 * Aether's components implement {@link org.eclipse.aether.spi.locator.Service} to ease manual wiring.
	 * Using the prepopulated {@link DefaultServiceLocator}, we need to register the repository connector
	 * and transporter factories
	 */
	@SuppressWarnings("unchecked")
	public static RepositorySystem newRepositorySystem() {
		
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);
		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
		try {
			Class<TransporterFactory> wagonTransporterFactory = (Class<TransporterFactory>) Class
					.forName("org.eclipse.aether.transport.wagon.WagonTransporterFactory");
			locator.addService(TransporterFactory.class, wagonTransporterFactory);
		} catch (Exception e) {
			// ignore
		}
		locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
			@Override
			public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
				throw new RuntimeException(exception);
			}
		});
		return locator.getService(RepositorySystem.class);
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
