/*******************************************************************************
 * Copyright (c) 2018 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Lars Vogel <Lars.Vogel@vogella.com> - initial API and implementation
 ******************************************************************************/

package org.eclipse.e4.ui.tests.extensions;

import org.eclipse.e4.core.commands.CommandServiceAddon;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.internal.workbench.swt.E4Application;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.osgi.framework.FrameworkUtil;
import org.eclipse.e4.ui.services.ContextServiceAddon;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.internal.WorkingSetManager;
import org.eclipse.ui.internal.progress.ProgressManager;
import org.eclipse.ui.progress.IProgressService;


public class HeadlessApplicationExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
	
	
	/**
	 * @return the applicationContext
	 */
	public static IEclipseContext getApplicationContext(ExtensionContext context) {
		return context.getStore(Namespace.create(HeadlessApplicationExtension.class)).getOrComputeIfAbsent(IEclipseContext.class, HeadlessApplicationExtension::createApplicationContext, IEclipseContext.class);
	}
	static IEclipseContext createApplicationContext(Class<?> key) {
		final IEclipseContext appContext = E4Application.createDefaultContext();
		appContext.set(IProgressService.class, ProgressManager.getInstance());
//		ContextInjectionFactory.make(ProgressManager.class, appContext);
//		ContextInjectionFactory.make(CommandServiceAddon.class, appContext);
//		ContextInjectionFactory.make(ContextServiceAddon.class, appContext);
		return appContext;
	}


	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		getApplicationContext(context).dispose();
	}


	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		getApplicationContext(context);
	}
	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return IEclipseContext.class.isAssignableFrom(parameterContext.getParameter().getType());
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return getApplicationContext(extensionContext);
	}
}