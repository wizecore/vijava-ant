package com.wizecore.ant.vijava;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.vmware.vim25.MethodFault;
import com.vmware.vim25.mo.ManagedObject;
import com.vmware.vim25.mo.ServiceInstance;

/**
 * Abstract VM task.
 * Can be executed standalone or as part of &lt;vm&gt; set of tasks.
 * 
 * @see VMTask
 * @author huksley
 */
public class AbstractVMTask extends Task {

	protected final static Logger log = Logger.getLogger(AbstractVMTask.class.getName());
	
	VMTask parent;
	String vcenter;
	String host;
	String username;
	String password;
	File credentialsFile = null;
	boolean persistentConnection = true;
	ServiceInstance serviceInstance = null;
	
	public static boolean same(ManagedObject a, ManagedObject b) {
		if (a == null || b == null) {
			return a == b;
		} else {
			return a.getMOR().getType().equals(b.getMOR().getType()) &&
				a.getMOR().get_value().equals(b.getMOR().get_value());
		}
	}
	
	protected void closeServerInstance(ServiceInstance si) {
		if (parent == null) {
			si.getServerConnection().logout();
			si = null;
			serviceInstance = null;
		}
	}

	protected void runTask(com.vmware.vim25.mo.Task task, boolean wait) throws MethodFault, RemoteException, InterruptedException {
		if (wait) {
			String status = task.waitForTask();
			if(status != com.vmware.vim25.mo.Task.SUCCESS) {
				throw new BuildException("Task " + task + " failed: " + status);
			}
		}
	}
		
	public ServiceInstance getServiceInstance() throws IOException {
		if (parent != null && parent.serviceInstance != null) {
			return parent.serviceInstance;
		}
		
		if (serviceInstance != null) {
			return serviceInstance;
		}
		
		if (vcenter == null && host == null) {
			throw new IllegalArgumentException("Either vcenter= or host= must be set!");
		}
		
		if (credentialsFile != null && credentialsFile.exists()) {
			File ff = credentialsFile;
			Properties p = new Properties();
			FileInputStream fis = new FileInputStream(ff);
			try {
				p.load(fis);
				username = p.getProperty("username");
				password = p.getProperty("password");
			} finally {
				fis.close();
			}
		}
		
		if (username == null || password == null || username.trim().equals("") || password.trim().equals("")) {
			throw new IllegalArgumentException("No username and password specified!");
		}
		
		URL url = new URL("https://" + (vcenter != null ? vcenter : host) + "/sdk");
		log.info("Connecting to " + url + " as " + username + ":***");
		ServiceInstance si = new ServiceInstance(url, username, password, true);
		if (persistentConnection) {
			serviceInstance = si;
		}
		return si;
	}

	/**
	 * Getter for {@link AbstractVMTask#vcenter}.
	 */
	public String getvcenter() {
		return vcenter;
	}

	/**
	 * Setter for {@link AbstractVMTask#vcenter}.
	 */
	public void setvcenter(String vcenter) {
		this.vcenter = vcenter;
	}

	/**
	 * Getter for {@link AbstractVMTask#host}.
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Setter for {@link AbstractVMTask#host}.
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * Getter for {@link AbstractVMTask#username}.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Setter for {@link AbstractVMTask#username}.
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Getter for {@link AbstractVMTask#password}.
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Setter for {@link AbstractVMTask#password}.
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Getter for {@link AbstractVMTask#credentialsFile}.
	 */
	public File getCredentialsFile() {
		return credentialsFile;
	}

	/**
	 * Setter for {@link AbstractVMTask#credentialsFile}.
	 */
	public void setCredentialsFile(File credentialsFile) {
		this.credentialsFile = credentialsFile;
	}

	/**
	 * Getter for {@link AbstractVMTask#persistentConnection}.
	 */
	public boolean isPersistentConnection() {
		return persistentConnection;
	}

	/**
	 * Setter for {@link AbstractVMTask#persistentConnection}.
	 */
	public void setPersistentConnection(boolean persistentConnection) {
		this.persistentConnection = persistentConnection;
	};
}
