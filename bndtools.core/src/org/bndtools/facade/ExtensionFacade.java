package org.bndtools.facade;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bndtools.api.launch.LaunchConstants;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import aQute.bnd.exceptions.Exceptions;
import bndtools.Plugin;

public class ExtensionFacade<T> implements IExecutableExtension, IExecutableExtensionFactory, InvocationHandler {

	ServiceTracker<Object, T>	tracker;
	String						id;
	IConfigurationElement		config;
	String						propertyName;
	Class<T>					downstreamClass;
	Object						data;
	BundleContext				bc;

	@Override
	public Object create() throws CoreException {
		System.err.println("Attempting to create downstream object of type: " + downstreamClass);
		return Proxy.newProxyInstance(downstreamClass.getClassLoader(), new Class<?>[] {
			downstreamClass
		}, this);
	}

	class Customizer implements ServiceTrackerCustomizer<Object, T> {

		@Override
		public T addingService(ServiceReference<Object> reference) {
			System.err.println("addingService:" + reference);
			final Object service = bc.getService(reference);
			if (service instanceof IExecutableExtension) {
				IExecutableExtension ee = (IExecutableExtension) service;
				try {
					System.err.println("Initializing the ExecutableExtension");
					ee.setInitializationData(config, propertyName, data);
				} catch (CoreException e) {
					e.printStackTrace();
					return null;
				}
			}
			if (service instanceof IExecutableExtensionFactory) {
				IExecutableExtensionFactory factory = (IExecutableExtensionFactory) service;
				try {
					System.err.println("Running factory.create()");
					@SuppressWarnings("unchecked")
					final T retval = (T) factory.create();
					onNewService.forEach(callback -> callback.accept(reference, retval));
					return retval;
				} catch (CoreException e) {
					e.printStackTrace();
					return null;
				}
			}
			if (!downstreamClass.isAssignableFrom(service.getClass())) {
				System.err.println("downstreamClass is not an instance of " + downstreamClass.getCanonicalName()
					+ ", was " + service.getClass()
						.getCanonicalName());
				return null;
			}
			System.err.println("Returning non-factory extension");
			@SuppressWarnings("unchecked")
			final T retval = (T) service;
			onNewService.forEach(callback -> callback.accept(reference, retval));
			return retval;
		}

		@Override
		public void modifiedService(ServiceReference<Object> reference, T service) {}

		@Override
		public void removedService(ServiceReference<Object> reference, T service) {
			onClosedService.forEach(callback -> callback.accept(reference, service));
		}

	}

	List<BiConsumer<ServiceReference<Object>, T>>	onNewService	= new ArrayList<>();
	List<BiConsumer<ServiceReference<Object>, T>>	onClosedService	= new ArrayList<>();

	public void onNewService(BiConsumer<ServiceReference<Object>, T> callback) {
		onNewService.add(callback);
	}

	public void onClosedService(BiConsumer<ServiceReference<Object>, T> callback) {
		onClosedService.add(callback);
	}

	public boolean isEmpty() {
		return tracker.isEmpty();
	}

	public int size() {
		return tracker.size();
	}

	public Optional<T> getService() {
		return Optional.ofNullable(tracker.getService());
	}

	public T getRequiredService() {
		System.err.println("Attempting to get service " + id);
		return getService().orElseThrow(() -> {
			Plugin.getDefault()
				.getLog()
				.log(new Status(IStatus.WARNING, Plugin.PLUGIN_ID, 0,
					MessageFormat.format("The {0} attribute is no longer supported, use {1} instead.", ,
						LaunchConstants.ATTR_TRACE),
					null));
			return new RuntimeException("Service " + id + " (" + downstreamClass.getCanonicalName() + ") not found");
		});
	}

	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
		throws CoreException {
		this.config = config;
		this.propertyName = propertyName;
		this.data = data;
		this.id = config.getAttribute("id");

		System.err.println("Initializing facade: " + System.identityHashCode(this) + ", id: " + id + ", propName: "
			+ propertyName + ", data: " + data);

		try {
			System.err.println("contextClassloader: " + Thread.currentThread()
				.getContextClassLoader());
			@SuppressWarnings("unchecked")
			final Class<T> clazz = (Class<T>) Class.forName(data.toString());
			downstreamClass = clazz;
		} catch (ClassNotFoundException e) {
			throw new CoreException(
				new Status(IStatus.ERROR, getClass(), 0, "Downstream interface for " + id + " not found", e));
		}

		initializeTracker(id);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		System.err.println("Proxying method call: " + method + "()");
		// + (args == null ? ")"
		// : Stream.of(args)
		// .map(Object::toString)
		// .collect(Collectors.joining(",")) + ")"));
		ClassLoader current = Thread.currentThread()
			.getContextClassLoader();
		try {
			Object service = getRequiredService();
			Thread.currentThread()
				.setContextClassLoader(service.getClass()
					.getClassLoader());
			return method.invoke(service, args);
		} finally {
			Thread.currentThread()
				.setContextClassLoader(current);
		}
	}

	/**
	 * Invoked by the Eclipse UI. Initialization is deferred until
	 * {@link #setInitializationData} is called.
	 */
	public ExtensionFacade() {}

	/**
	 * Constructor for programmatic instantiation.
	 *
	 * @param id
	 */
	public ExtensionFacade(String id, Class<T> downstreamType) {
		initializeTracker(id);
	}

	private void initializeTracker(String id) {
		System.err.println("Initializing tracker for: " + System.identityHashCode(this) + ", id: " + id);
		bc = FrameworkUtil.getBundle(ExtensionFacade.class)
			.getBundleContext();
		Filter filter;
		try {
			filter = bc.createFilter("(eclipse.id=" + id + ")");
			System.err.println("Tracking services with filter: " + filter);
			tracker = new ServiceTracker<Object, T>(bc, filter, new Customizer());
			tracker.open();
		} catch (InvalidSyntaxException e) {
			Exceptions.duck(e);
		}
	}
}
